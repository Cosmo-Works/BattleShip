// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.explosion.specialised;

import me.samsuik.sakura.mechanics.ExplosionBehaviour;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class TntExplosion extends SpecialisedExplosion<PrimedTnt> {
    public TntExplosion(
        final ServerLevel level,
        final PrimedTnt cause,
        final @Nullable DamageSource damageSource,
        final @Nullable ExplosionDamageCalculator damageCalculator,
        final Vec3 center,
        final float radius,
        final boolean fire,
        final BlockInteraction blockInteraction
    ) {
        super(level, cause, damageSource, damageCalculator, center, radius, fire, blockInteraction);
    }

    @Override
    protected Vec3 explosionPosition(final PrimedTnt explodingEntity) {
        return new Vec3(
            explodingEntity.getX(),
            ExplosionBehaviour.getY(explodingEntity),
            explodingEntity.getZ()
        );
    }

    @Override
    protected void preventEntityExploding(final PrimedTnt explodableEntity) {
        explodableEntity.canExplode = false;
    }

    @Override
    protected boolean continueExploding(final PrimedTnt explodingEntity) {
        return true;
    }
}
