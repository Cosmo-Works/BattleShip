// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.explosion.density;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record PaperObstructionCacheKey(Vec3 explosionPos, Vec3 entityPos, float width, float height) {
    public PaperObstructionCacheKey(final Vec3 center, final Entity entity) {
        this(center, entity.position(), entity.getBbWidth(), entity.getBbHeight());
    }
}
