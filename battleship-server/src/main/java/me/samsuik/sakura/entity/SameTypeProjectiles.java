// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class SameTypeProjectiles {
    public static boolean canCollide(final Projectile projectile, final Entity other) {
        if (!projectile.level().battleshipConfig().entities.skipSameTypeProjectileHits) {
            return true;
        }
        if (!(other instanceof Projectile) || projectile instanceof AbstractArrow) {
            return true;
        }
        return other.getType() != projectile.getType();
    }

    private SameTypeProjectiles() {
    }
}
