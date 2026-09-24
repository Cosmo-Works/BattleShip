// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.player.visibility;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TrackerOcclusion {
    private static final int RECHECK_INTERVAL = 8;
    private static final double CLOSE_RANGE_SQR = 4.0;
    private static final int MAX_TRANSPARENT_STEPS = 16;

    public static boolean hiddenFrom(final Entity entity, final ServerPlayer player, final boolean currentlyTracked) {
        if (!shouldCull(entity, player)) {
            return false;
        }
        if (currentlyTracked && player.tickCount % RECHECK_INTERVAL != 0) {
            return false;
        }
        return solidBlocksView(player, entity);
    }

    private static boolean shouldCull(final Entity entity, final ServerPlayer player) {
        if (!player.level().shrekConfig().entities.trackerOcclusionCulling) {
            return false;
        }
        if (entity.cannonEntity || player.isSpectator() || entity.isCurrentlyGlowing()) {
            return false;
        }
        if (player.level() != entity.level()) {
            return false;
        }
        return TickThread.isTickThreadFor(player) && TickThread.isTickThreadFor(entity);
    }

    private static boolean solidBlocksView(final ServerPlayer player, final Entity entity) {
        final Vec3 eye = player.getEyePosition();
        final Vec3 target = closestPointOnBox(entity.getBoundingBox(), eye);
        if (eye.distanceToSqr(target) <= CLOSE_RANGE_SQR) {
            return false;
        }

        final Level level = player.level();
        Vec3 from = eye;
        for (int step = 0; step < MAX_TRANSPARENT_STEPS; step++) {
            final BlockHitResult hit = level.clip(new ClipContext(from, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return false;
            }

            final BlockState state = level.getBlockStateIfLoaded(hit.getBlockPos());
            if (state == null) {
                return false;
            }
            if (state.canOcclude()) {
                return true;
            }

            from = stepPast(from, target, hit.getLocation());
            if (from.distanceToSqr(target) < 1.0E-6) {
                return false;
            }
        }
        return false;
    }

    private static Vec3 closestPointOnBox(final AABB box, final Vec3 point) {
        return new Vec3(
            Mth.clamp(point.x, box.minX, box.maxX),
            Mth.clamp(point.y, box.minY, box.maxY),
            Mth.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    private static Vec3 stepPast(final Vec3 from, final Vec3 target, final Vec3 hit) {
        final Vec3 direction = target.subtract(from);
        final double length = direction.length();
        if (length < 1.0E-4) {
            return target;
        }
        return hit.add(direction.scale(0.05 / length));
    }

    private TrackerOcclusion() {
    }
}
