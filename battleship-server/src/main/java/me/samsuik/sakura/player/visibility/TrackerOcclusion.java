// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.player.visibility;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;

/**
 * Hides entities from a player when an opaque block is between them.
 *
 * <p>Battleship: the ray is walked voxel by voxel (Amanatides-Woo) over chunks that are already loaded
 * and owned by the current region; it never loads a chunk (level.clip did, synchronously, when the
 * ray crossed an unloaded chunk) and never reads another region's chunk. A block it cannot read
 * counts as not occluding, so the entity stays visible. It only reads, so the parallel tracker's
 * helpers may run it.</p>
 */
@NullMarked
public final class TrackerOcclusion {
    private static final int RECHECK_INTERVAL = 8;
    private static final double CLOSE_RANGE_SQR = 4.0;
    /** Longest ray walked, in voxels; beyond it the entity counts as visible. */
    private static final int MAX_VOXELS = 512;

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
        if (!player.level().battleshipConfig().entities.trackerOcclusionCulling) {
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
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        final CollisionContext context = CollisionContext.of(player);
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final LevelChunk[] chunk = new LevelChunk[1];
        final long[] chunkKey = {Long.MIN_VALUE};
        final int result = walk(eye, target, MAX_VOXELS, (x, y, z) -> {
            final long key = net.minecraft.world.level.ChunkPos.pack(x >> 4, z >> 4);
            if (key != chunkKey[0]) {
                chunkKey[0] = key;
                // only chunks that are loaded and belong to this region: never load, never read another region's
                chunk[0] = TickThread.isTickThreadFor(level, x >> 4, z >> 4) ? level.moonrise$getFullChunkIfLoaded(x >> 4, z >> 4) : null;
            }
            if (chunk[0] == null) {
                return STOP_VISIBLE;
            }
            if (y < level.getMinY() || y > level.getMaxY()) {
                return CONTINUE;
            }
            pos.set(x, y, z);
            final BlockState state = chunk[0].getBlockState(pos);
            return state.canOcclude() && hitsVisualShape(state, level, pos, context, eye, target) ? STOP_OCCLUDED : CONTINUE;
        });
        return result == STOP_OCCLUDED;
    }

    static final int CONTINUE = 0;
    static final int STOP_VISIBLE = 1;
    static final int STOP_OCCLUDED = 2;

    @FunctionalInterface
    interface VoxelVisitor {
        int visit(int x, int y, int z);
    }

    /**
     * Visits the voxels the segment from {@code from} to {@code to} passes through, in order,
     * skipping the voxel it starts in (Amanatides-Woo). Stops when the visitor returns something
     * other than {@link #CONTINUE}, at the end voxel, or after {@code maxVoxels}.
     *
     * @return what the visitor returned last, or {@link #STOP_VISIBLE} if the walk ended without a hit
     */
    static int walk(final Vec3 from, final Vec3 to, final int maxVoxels, final VoxelVisitor visitor) {
        int x = Mth.floor(from.x);
        int y = Mth.floor(from.y);
        int z = Mth.floor(from.z);
        final int endX = Mth.floor(to.x);
        final int endY = Mth.floor(to.y);
        final int endZ = Mth.floor(to.z);
        final double dx = to.x - from.x;
        final double dy = to.y - from.y;
        final double dz = to.z - from.z;
        final int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        final int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        final int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        final double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        final double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        final double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double maxX = stepX == 0 ? Double.MAX_VALUE : deltaX * (stepX > 0 ? (x + 1 - from.x) : (from.x - x));
        double maxY = stepY == 0 ? Double.MAX_VALUE : deltaY * (stepY > 0 ? (y + 1 - from.y) : (from.y - y));
        double maxZ = stepZ == 0 ? Double.MAX_VALUE : deltaZ * (stepZ > 0 ? (z + 1 - from.z) : (from.z - z));
        for (int voxel = 0; voxel < maxVoxels; ++voxel) {
            if (x == endX && y == endY && z == endZ) {
                return STOP_VISIBLE;
            }
            if (maxX < maxY && maxX < maxZ) {
                if (maxX > 1.0) return STOP_VISIBLE;
                x += stepX;
                maxX += deltaX;
            } else if (maxY < maxZ) {
                if (maxY > 1.0) return STOP_VISIBLE;
                y += stepY;
                maxY += deltaY;
            } else {
                if (maxZ > 1.0) return STOP_VISIBLE;
                z += stepZ;
                maxZ += deltaZ;
            }
            final int result = visitor.visit(x, y, z);
            if (result != CONTINUE) {
                return result;
            }
        }
        return STOP_VISIBLE;
    }

    /** Whether the segment crosses the block's visual shape, as level.clip with ClipContext.Block.VISUAL checks. */
    private static boolean hitsVisualShape(
        final BlockState state, final ServerLevel level, final BlockPos pos, final CollisionContext context, final Vec3 from, final Vec3 to
    ) {
        final VoxelShape shape = state.getVisualShape(level, pos, context);
        if (shape.isEmpty()) {
            return false;
        }
        final BlockHitResult hit = shape.clip(from, to, pos);
        return hit != null;
    }

    private static Vec3 closestPointOnBox(final AABB box, final Vec3 point) {
        return new Vec3(
            Mth.clamp(point.x, box.minX, box.maxX),
            Mth.clamp(point.y, box.minY, box.maxY),
            Mth.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    private TrackerOcclusion() {
    }
}
