// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.mechanics;

import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class EntityBehaviour {
    public static void handleCollisions(
        final Entity entity,
        final BlockState effectState,
        boolean xCollision,
        final boolean zCollision,
        final Vec3 movement,
        final Vec3 relativeMovement,
        final MinecraftMechanicsTarget mechanicsTarget
    ) {
        if (entity.horizontalCollision) {
            final Vec3 momentum = entity.getDeltaMovement();
            if (xCollision && zCollision && mechanicsTarget.between(MechanicVersion.v1_14, MechanicVersion.v1_18_2)) {
                xCollision = false;
            }
            entity.setDeltaMovement(xCollision ? 0.0 : momentum.x, momentum.y, zCollision ? 0.0 : momentum.z);
        }

        if (entity.canSimulateMovement() && movement.y != relativeMovement.y) {
            bounceOrStopAfterFall(entity, effectState, mechanicsTarget);
        }
    }

    private static void bounceOrStopAfterFall(
        final Entity entity,
        final BlockState effectState,
        final MinecraftMechanicsTarget mechanicsTarget
    ) {
        final Vec3 movement = entity.getDeltaMovement();
        final boolean slime = effectState.is(Blocks.SLIME_BLOCK);
        final boolean beds = mechanicsTarget.atLeast(MechanicVersion.v1_12) && effectState.is(BlockTags.BEDS);

        if (!slime && !beds) {
            entity.setDeltaMovement(movement.x, 0.0, movement.z);
            return;
        }

        if (movement.y < 0.0) {
            final double entityModifier = entity instanceof LivingEntity ? 1.0 : 0.8;
            final float bounciness = beds ? 0.66f : 1.0f;
            entity.setDeltaMovement(movement.x, -movement.y * bounciness * entityModifier, movement.z);
        }
    }

    public static void changeEntityPosition(
        final Entity entity,
        final Vec3 position,
        final Vec3 relativeMovement,
        final MinecraftMechanicsTarget mechanicsTarget
    ) {
        final Vec3 newPosition = position.add(relativeMovement);
        final Vec3 newEntityPosition;
        if (mechanicsTarget.is(MechanicVersion.v1_21_5)) {
            newEntityPosition = manglePosition(position, relativeMovement);
            entity.addMovementThisTick(new Entity.Movement(position, newPosition, relativeMovement));
        } else {
            newEntityPosition = newPosition;
        }

        entity.setPos(newEntityPosition);
    }

    private static Vec3 manglePosition(final Vec3 position, final Vec3 relativeMovement) {
        Vec3 newPosition = position;
        for (final Direction.Axis axis : Direction.axisStepOrder(relativeMovement)) {
            final double movement = relativeMovement.get(axis);
            if (movement != 0.0) {
                newPosition = newPosition.relative(axis.getPositive(), movement);
            }
        }

        return newPosition;
    }

    public static boolean canMoveEntity(
        final double relativeMovementSqr,
        final Vec3 movement,
        final MinecraftMechanicsTarget mechanicsTarget
    ) {
        return relativeMovementSqr > 1.0E-7
            || mechanicsTarget.atLeast(MechanicVersion.v1_21_2) && movement.lengthSqr() - relativeMovementSqr < 1.0E-7
            || mechanicsTarget.before(MechanicVersion.v1_14);
    }

    public static boolean canCheckInsideBlock(
        final BlockPos pos,
        final boolean canCollide,
        final LongSet visited,
        final MinecraftMechanicsTarget mechanicsTarget
    ) {
        final boolean before1_21_9 = mechanicsTarget.before(MechanicVersion.v1_21_9);
        if (before1_21_9 && !visited.add(pos.asLong())) {
            return false;
        }
        return (mechanicsTarget.before(MechanicVersion.v1_21_2) || canCollide) && (before1_21_9 || visited.add(pos.asLong()));
    }

    public static boolean prioritiseXFirst(final double x, final double z, final @Nullable MinecraftMechanicsTarget mechanicsTarget) {
        return mechanicsTarget == null || mechanicsTarget.atLeast(MechanicVersion.v1_14)
            ? Math.abs(x) < Math.abs(z)
            : mechanicsTarget.isLegacy() && Math.abs(x) > Math.abs(z);
    }

    public static void convertVoxelsIntoAABBs(
        final Vec3 movement,
        final AABB bb,
        final List<VoxelShape> voxels,
        final List<AABB> aabbs
    ) {
        final AABB collisions = bb.expandTowards(movement);
        for (final VoxelShape shape : voxels) {
            for (final AABB boundingBox : shape.toAabbs()) {
                if (CollisionUtil.voxelShapeIntersect(boundingBox, collisions) && !CollisionUtil.isEmpty(boundingBox)) {
                    aabbs.add(boundingBox);
                }
            }
        }

        voxels.clear();
    }
}
