// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.mechanics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PistonBehaviour {
    public static @Nullable AABB getBoundsFromProgress(
        final BlockGetter level,
        final BlockPos pos,
        final BlockState state,
        final float progress,
        final Direction direction
    ) {
        if (state.is(Blocks.MOVING_PISTON) || state.isAir()) {
            return null;
        }

        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return null;
        }

        final AABB bounds = shape.bounds();
        double minX = bounds.minX;
        double minY = bounds.minY;
        double minZ = bounds.minZ;
        double maxX = bounds.maxX;
        double maxY = bounds.maxY;
        double maxZ = bounds.maxZ;

        if (direction.getStepX() < 0) {
            minX -= (float) direction.getStepX() * progress;
        } else {
            maxX -= (float) direction.getStepX() * progress;
        }

        if (direction.getStepY() < 0) {
            minY -= (float) direction.getStepY() * progress;
        } else {
            maxY -= (float) direction.getStepY() * progress;
        }

        if (direction.getStepZ() < 0) {
            minZ -= (float) direction.getStepZ() * progress;
        } else {
            maxZ -= (float) direction.getStepZ() * progress;
        }

        return expandZeroWidth(new AABB(minX, minY, minZ, maxX, maxY, maxZ), direction);
    }

    public static void moveEntitiesWithProgress(
        final Level level,
        final AABB movingBlockBox,
        final Direction direction,
        final float relativeProgress,
        final boolean pushingSlime,
        final PistonMovingBlockEntity self
    ) {
        for (final Entity entity : self.getEntities(level, movingBlockBox, null)) {
            if (pushingSlime) {
                entity.setDeltaMovement(bounceAlongPiston(direction, entity.getDeltaMovement()));
            } else {
                entity.move(MoverType.PISTON, pistonPush(direction, relativeProgress));
                entity.applyEffectsFromBlocks(entity.position(), entity.position());
            }
        }
    }

    public static void moveEntityByPistonInDirection(final Direction direction, final Entity entity, final AABB movingBlockBox) {
        final AABB entityBox = entity.getBoundingBox();
        final Vec3 pistonMovement = pushOutOfBox(direction, movingBlockBox, entityBox);
        entity.move(MoverType.PISTON, pistonMovement);
        entity.applyEffectsFromBlocks(entity.position(), entity.position());
    }

    private static AABB expandZeroWidth(AABB movingBlockBox, final Direction direction) {
        final double expandX = movingBlockBox.getXsize() == 0.0 ? 1.0e-5 * direction.getStepX() : 0.0;
        final double expandY = movingBlockBox.getYsize() == 0.0 ? 1.0e-5 * direction.getStepY() : 0.0;
        final double expandZ = movingBlockBox.getZsize() == 0.0 ? 1.0e-5 * direction.getStepZ() : 0.0;

        if (expandX != 0 || expandY != 0 || expandZ != 0) {
            movingBlockBox = movingBlockBox.expandTowards(expandX, expandY, expandZ);
        }

        return movingBlockBox;
    }

    private static Vec3 bounceAlongPiston(final Direction direction, final Vec3 movement) {
        return switch (direction.getAxis()) {
            case X -> new Vec3(direction.getStepX(), movement.y, movement.z);
            case Y -> new Vec3(movement.x, direction.getStepY(), movement.z);
            case Z -> new Vec3(movement.x, movement.y, direction.getStepZ());
        };
    }

    private static Vec3 pistonPush(final Direction direction, final float relativeProgress) {
        return new Vec3(
            relativeProgress * (float) direction.getStepX(),
            relativeProgress * (float) direction.getStepY(),
            relativeProgress * (float) direction.getStepZ()
        );
    }

    private static Vec3 pushOutOfBox(final Direction direction, final AABB movingBlockBox, final AABB entityBox) {
        final double overlap = overlapAlong(direction, movingBlockBox, entityBox) + 0.01D;
        return new Vec3(
            overlap * direction.getStepX(),
            overlap * direction.getStepY(),
            overlap * direction.getStepZ()
        );
    }

    private static double overlapAlong(final Direction direction, final AABB movingBlockBox, final AABB entityBox) {
        return switch (direction.getAxis()) {
            case X -> direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? movingBlockBox.maxX - entityBox.minX
                : entityBox.maxX - movingBlockBox.minX;
            case Y -> direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? movingBlockBox.maxY - entityBox.minY
                : entityBox.maxY - movingBlockBox.minY;
            case Z -> direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? movingBlockBox.maxZ - entityBox.minZ
                : entityBox.maxZ - movingBlockBox.minZ;
        };
    }
}
