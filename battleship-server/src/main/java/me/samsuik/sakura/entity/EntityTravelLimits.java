// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class EntityTravelLimits {
    private EntityTravelLimits() {
    }

    public static void discardIfTravelledTooFar(final Entity entity) {
        if (travelledTooFar(entity)) {
            entity.discard(EntityRemoveEvent.Cause.DESPAWN);
        }
    }

    private static boolean travelledTooFar(final Entity entity) {
        if (!sameWorldAsOrigin(entity)) {
            return false;
        }

        final Integer chunks = chunkLimit(entity);
        if (chunks == null || chunks <= 0) {
            return false;
        }

        final Vec3 origin = entity.origin;
        final double travelledX = entity.getX() - origin.x;
        final double travelledZ = entity.getZ() - origin.z;
        final double limit = chunks * 16.0;
        return Math.max(travelledX * travelledX, travelledZ * travelledZ) >= limit * limit;
    }

    private static @Nullable Integer chunkLimit(final Entity entity) {
        return entity.level().sakuraConfig().entity.chunkTravelLimit.get(entity.getType());
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
}
