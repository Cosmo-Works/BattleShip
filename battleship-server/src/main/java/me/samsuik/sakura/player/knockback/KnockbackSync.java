// Ported from KnockbackSync (https://github.com/CASELOAD7000/knockback-sync), licensed under GPL-3.0.
package me.samsuik.sakura.player.knockback;

import dev.cosmoworks.battleship.BattleshipServerConfig;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class KnockbackSync {
    private static final long DEFAULT_KEEPALIVE_NANOS = TimeUnit.SECONDS.toNanos(1L);
    private static final double GROUND_CHECK_DISTANCE = 5.0;
    private static final double CORNER_INSET = 0.01;
    private static final int SKIP_IFRAMES = 8;
    private static final double FULL_STRENGTH = 0.848;
    private static final double FULL_STRENGTH_VERTICAL = 0.4;
    private static final double WEAK_VERTICAL = 0.36080000519752503;
    private static final double RESISTANCE_FACTOR = 0.04000000119;

    public static void rememberPlayerAttack(final LivingEntity victim, final DamageSource source) {
        if (!(victim instanceof final ServerPlayer player)) {
            return;
        }
        if (!(source.getDirectEntity() instanceof final ServerPlayer attacker)) {
            return;
        }
        if (player.level() != attacker.level()) {
            return;
        }
        if (!config().enabled) {
            return;
        }

        player.knockbackSync.rememberAttack(
            player.connection.latency(),
            player.tickCount,
            config().combatTimer,
            player.getInvulnerableTime(),
            verticalVelocityFrom(attacker)
        );
    }

    public static void applyToKnockback(
        final ServerPlayer victim,
        final io.papermc.paper.event.entity.EntityKnockbackEvent.Cause cause
    ) {
        if (cause != io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.ENTITY_ATTACK) {
            return;
        }
        if (!config().enabled) {
            return;
        }

        final KnockbackSyncState state = victim.knockbackSync;
        state.observePing(victim.connection.latency());
        if (state.pingBelowOffset(config().pingOffset)) {
            return;
        }

        final Vec3 movement = victim.getDeltaMovement();
        final double distanceToGround = distanceToGround(victim);
        if (distanceToGround <= 0.0 || shouldLeaveVanillaKnockback(victim)) {
            return;
        }

        if (state.onGroundClientSide(movement.y, distanceToGround, victim.getGravity(), config().pingOffset, config().spikeThreshold)) {
            if (state.lastDamageTicks() > SKIP_IFRAMES) {
                return;
            }
            victim.setDeltaMovement(movement.x, state.verticalVelocity(), movement.z);
            return;
        }

        if (!config().offGround) {
            return;
        }

        victim.setDeltaMovement(
            movement.x,
            KnockbackSyncPhysics.fallVelocity(movement.y, victim.getGravity(), state.rawTicks()),
            movement.z
        );
    }

    public static long keepAliveIntervalNanos(final ServerCommonPacketListenerImpl listener) {
        if (!(listener instanceof final ServerGamePacketListenerImpl game)) {
            return DEFAULT_KEEPALIVE_NANOS;
        }
        final BattleshipServerConfig.KnockbackSync settings = config();
        if (!settings.enabled) {
            return DEFAULT_KEEPALIVE_NANOS;
        }
        final ServerPlayer player = game.getPlayer();
        if (player == null || !player.knockbackSync.inCombat(player.tickCount)) {
            return DEFAULT_KEEPALIVE_NANOS;
        }
        return TimeUnit.MILLISECONDS.toNanos(settings.combatPingInterval * 50L);
    }

    private static boolean shouldLeaveVanillaKnockback(final ServerPlayer victim) {
        if (victim.isFallFlying()) {
            return true;
        }
        if (victim.isInWater() || victim.isInLava()) {
            return true;
        }
        final BlockState feet = victim.level().getBlockStateIfLoaded(victim.blockPosition());
        return feet != null && (feet.is(Blocks.COBWEB) || feet.is(Blocks.SCAFFOLDING));
    }

    private static double distanceToGround(final ServerPlayer victim) {
        final Level level = victim.level();
        final AABB box = victim.getBoundingBox();
        final double y = victim.getY();
        final Vec3[] corners = {
            new Vec3(box.minX + CORNER_INSET, y, box.minZ + CORNER_INSET),
            new Vec3(box.minX + CORNER_INSET, y, box.maxZ - CORNER_INSET),
            new Vec3(box.maxX - CORNER_INSET, y, box.minZ + CORNER_INSET),
            new Vec3(box.maxX - CORNER_INSET, y, box.maxZ - CORNER_INSET)
        };

        double closest = GROUND_CHECK_DISTANCE;
        for (final Vec3 corner : corners) {
            final Vec3 down = new Vec3(corner.x, corner.y - GROUND_CHECK_DISTANCE, corner.z);
            final BlockHitResult hit = level.clip(new ClipContext(corner, down, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim));
            if (hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            closest = Math.min(closest, corner.y - hit.getBlockPos().getY());
        }
        return closest - 1.0;
    }

    private static double verticalVelocityFrom(final ServerPlayer attacker) {
        double vertical = attacker.getAttackStrengthScale(0.5F) > FULL_STRENGTH ? FULL_STRENGTH_VERTICAL : WEAK_VERTICAL;
        if (!attacker.isSprinting()) {
            vertical = WEAK_VERTICAL - RESISTANCE_FACTOR * attacker.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * 10.0;
        }
        if (knockbackEnchantmentLevel(attacker) > 0) {
            return FULL_STRENGTH_VERTICAL;
        }
        return vertical;
    }

    private static int knockbackEnchantmentLevel(final ServerPlayer attacker) {
        final RegistryAccess access = MinecraftServer.getServer().registryAccess();
        final HolderLookup.RegistryLookup<Enchantment> enchantments = access.lookupOrThrow(Registries.ENCHANTMENT);
        final Holder<Enchantment> knockback = enchantments.getOrThrow(Enchantments.KNOCKBACK);
        return EnchantmentHelper.getItemEnchantmentLevel(knockback, attacker.getMainHandItem());
    }

    private static BattleshipServerConfig.KnockbackSync config() {
        final BattleshipServerConfig server = BattleshipServerConfig.get();
        return server == null ? new BattleshipServerConfig.KnockbackSync() : server.knockbackSync;
    }

    private KnockbackSync() {
    }
}
