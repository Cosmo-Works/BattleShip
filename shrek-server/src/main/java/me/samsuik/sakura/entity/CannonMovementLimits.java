// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.entity;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CannonMovementLimits {
    private CannonMovementLimits() {
    }

    public static void limitLeftShooting(final Entity entity) {
        final int threshold = parsedLimit(entity.level().shrekConfig().cannons.restrictions.parsedLeftShootingThreshold.valueSafe());
        if (threshold <= 0 || !sameWorldAsOrigin(entity)) {
            return;
        }

        final Vec3 movement = entity.getDeltaMovement();
        if (movement.x == 0.0 && movement.z == 0.0) {
            return;
        }

        final Vec3 origin = entity.origin;
        final double travelledX = Math.abs(entity.getX() - origin.x);
        final double travelledZ = Math.abs(entity.getZ() - origin.z);
        if (travelledX < travelledZ) {
            if (travelledX > threshold) {
                entity.setDeltaMovement(movement.multiply(1.0, 1.0, 0.0));
            }
        } else if (travelledZ > threshold) {
            entity.setDeltaMovement(movement.multiply(0.0, 1.0, 1.0));
        }
    }

    public static void limitAdjustMovement(
        final Entity entity,
        final AABB currentBox,
        final double dir,
        final boolean xAdjust,
        final List<VoxelShape> shapes
    ) {
        final int adjustDistance = parsedLimit(entity.level().shrekConfig().cannons.restrictions.parsedMaxAdjustDistance.valueSafe());
        if (adjustDistance <= 0 || Math.abs(dir) <= adjustDistance) {
            return;
        }

        shapes.add(Shapes.join(Shapes.INFINITY, safeSpace(currentBox, adjustDistance, xAdjust), BooleanOp.ONLY_FIRST));
    }

    private static boolean sameWorldAsOrigin(final Entity entity) {
        if (entity.origin == null) {
            return false;
        }
        if (entity.originWorld == null) {
            return true;
        }
        return entity.originWorld.equals(entity.level().getWorld().getUID());
    }

    private static VoxelShape safeSpace(final AABB currentBox, final int adjustDistance, final boolean xAdjust) {
        double minX = Double.NEGATIVE_INFINITY;
        double minZ = Double.NEGATIVE_INFINITY;
        double maxX = Double.POSITIVE_INFINITY;
        double maxZ = Double.POSITIVE_INFINITY;
        if (xAdjust) {
            minX = Math.floor(currentBox.minX) - adjustDistance;
            maxX = Math.floor(currentBox.maxX) + adjustDistance + 1;
        } else {
            minZ = Math.floor(currentBox.minZ) - adjustDistance;
            maxZ = Math.floor(currentBox.maxZ) + adjustDistance + 1;
        }
        return Shapes.box(minX, Double.NEGATIVE_INFINITY, minZ, maxX, Double.POSITIVE_INFINITY, maxZ);
    }

    private static int parsedLimit(final Integer value) {
        return value != null ? value : -1;
    }
}
