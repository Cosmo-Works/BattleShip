// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.explosion.specialised;

import me.samsuik.sakura.entity.EntityState;
import me.samsuik.sakura.entity.SakuraEntityController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class WindChargeExplosion extends SpecialisedExplosion<AbstractWindCharge> {
    public WindChargeExplosion(
        final ServerLevel level,
        final AbstractWindCharge cause,
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
    protected Vec3 explosionPosition(final AbstractWindCharge explodingEntity) {
        return this.center;
    }

    @Override
    protected void preventEntityExploding(final AbstractWindCharge explodableEntity) {
        explodableEntity.canExplode = false;
    }

    @Override
    protected boolean continueExploding(final AbstractWindCharge explodingEntity) {
        return !explodingEntity.getSakuraEntityController().haveBlocksChangedSinceTicking(this.level());
    }

    @Override
    protected EntityState nextEntityState(final SakuraEntityController controller, final AbstractWindCharge explodingEntity) {
        return controller.getPreTickState();
    }
}
