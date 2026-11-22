// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.mechanics;

import me.samsuik.sakura.mechanics.legacy.LegacyExplosionBlockClipping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ExplosionBehaviour {
    private static final double LEGACY_EXPLOSION_HEIGHT = (double) 0.98f * 0.0625D;

    public static boolean useSingleRaycast(final MinecraftMechanicsTarget mechanicsTarget) {
        return mechanicsTarget.isServerType(ServerType.SAKE) && mechanicsTarget.hasProperties(ServerProperties.SINGLE_RAY_CAST);
    }

    public static double getY(final PrimedTnt tnt) {
        return tnt.mechanicsTarget().atLeast(MechanicVersion.v1_10)
            ? tnt.getY(0.0625)
            : tnt.getY() + LEGACY_EXPLOSION_HEIGHT;
    }

    public static float getSeenPercentSingleRaycast(final Vec3 center, final Entity entity, final MinecraftMechanicsTarget mechanicsTarget) {
        final AABB bounds = entity.getBoundingBox();
        final double middle = 1.0 / (bounds.getYsize() * 2.0 + 1.0);
        final Vec3 point = new Vec3(entity.getX(), entity.getY() + middle, entity.getZ());

        final HitResult.Type hitResult;
        if (mechanicsTarget.before(MechanicVersion.v1_14)) {
            hitResult = LegacyExplosionBlockClipping.clip(entity.level(), point, center);
        } else {
            final ClipContext.Block blockContext = entity.mechanicsTarget().atLeast(MechanicVersion.v1_16)
                ? ClipContext.Block.COLLIDER
                : ClipContext.Block.OUTLINE;
            hitResult = entity.level().clip(new ClipContext(point, center, blockContext, ClipContext.Fluid.NONE, entity)).getType();
        }

        return hitResult == HitResult.Type.MISS ? 1.0f : 0.0f;
    }
}
