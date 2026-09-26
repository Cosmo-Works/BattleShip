// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.player.visibility;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class FilterOrModifyPackets {
    public static boolean filterExplosion(
        final Vec3 explosionPos,
        final ServerPlayer player,
        final long gameTime,
        final MinimalVisibility minimalVisibility
    ) {
        final VisibilitySettings settings = player.visibilitySettings;
        final VisibilityState state = settings.get(VisibilityTypes.EXPLOSIONS);

        if (state == VisibilityState.MINIMAL && minimalVisibility.filterExplosion(explosionPos, gameTime)) {
            return false;
        }

        return state != VisibilityState.ON;
    }

    public static boolean canSendEntityUpdates(
        final Entity entity,
        final ServerPlayer player,
        final MinimalVisibility minimalVisibility
    ) {
        final VisibilitySettings settings = player.visibilitySettings;
        if (!settings.playerModified() || !entity.cannonEntity) {
            return true;
        }

        final EntityType<?> entityType = entity.getType();
        final VisibilityType type;
        if (entityType == EntityTypes.TNT) {
            type = VisibilityTypes.TNT;
        } else if (entityType == EntityTypes.FALLING_BLOCK) {
            type = VisibilityTypes.SAND;
        } else if (entityType == EntityTypes.WIND_CHARGE || entityType == EntityTypes.BREEZE_WIND_CHARGE) {
            type = VisibilityTypes.WIND_CHARGE;
        } else if (entityType == EntityTypes.SULFUR_CUBE) {
            type = VisibilityTypes.SULFUR_CUBE;
        } else {
            return true;
        }

        final VisibilityState state = settings.get(type);
        if (state == VisibilityState.MINIMAL) {
            return !minimalVisibility.filterEntity(entity);
        }

        return state != VisibilityState.OFF;
    }

    /**
     * The packet to send to {@code player} instead of {@code packet}: the same packet, a filtered copy, or null to
     * send nothing. Never changes {@code packet}: chunk packets are shared between players (chunk packet cache,
     * shared chunk frames), so hiding spawners for one player must not hide them for everyone (Shrek fix: Sakura
     * removed them from the shared packet in place).
     */
    public static @Nullable Packet<?> filterOrModifyPacket(final Packet<?> packet, final ServerPlayer player) {
        return filterOrModifyPacket(packet, player.visibilitySettings);
    }

    public static @Nullable Packet<?> filterOrModifyPacket(final Packet<?> packet, final VisibilitySettings settings) {

        if (settings.isToggled(VisibilityTypes.SPAWNERS)) {
            if (packet instanceof final ClientboundLevelChunkWithLightPacket chunkPacket
                && chunkPacket.chunkData().shrek$hasBlockEntities(FilterOrModifyPackets::isSpawner)) {
                return new ClientboundLevelChunkWithLightPacket(
                    chunkPacket.x(), chunkPacket.z(),
                    chunkPacket.chunkData().shrek$withoutBlockEntities(FilterOrModifyPackets::isSpawner),
                    chunkPacket.lightData()
                );
            }

            if (packet instanceof final ClientboundBlockEntityDataPacket bedPacket && isSpawner(bedPacket.getType())) {
                return null;
            }
        }

        if (packet instanceof final ClientboundBlockEventPacket bePacket && settings.isToggled(VisibilityTypes.PISTONS)
            && bePacket.getBlock() instanceof PistonBaseBlock) {
            return null;
        }

        return packet;
    }

    private static boolean isSpawner(final net.minecraft.world.level.block.entity.BlockEntityType<?> type) {
        return type == BlockEntityTypes.MOB_SPAWNER;
    }

}
