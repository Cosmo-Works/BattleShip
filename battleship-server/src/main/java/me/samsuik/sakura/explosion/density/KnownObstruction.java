// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.explosion.density;

import me.samsuik.sakura.utils.AABBUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class KnownObstruction {
    private AABB explosionBox;
    private AABB entityBox;
    private final float seenPercent;

    public KnownObstruction(final Vec3 center, final AABB entityBox, final float seenPercent) {
        this.explosionBox = new AABB(center, center);
        this.entityBox = entityBox;
        this.seenPercent = seenPercent;
    }

    public float getSeenPercent() {
        return this.seenPercent;
    }

    public boolean isPartiallyObstructed() {
        return this.seenPercent != 0.0f && this.seenPercent != 1.0f;
    }

    public HitResult.Type hitResult() {
        return this.seenPercent == 1.0f ? HitResult.Type.MISS : HitResult.Type.BLOCK;
    }

    public boolean has(final Vec3 center, final AABB entityBox) {
        return this.isExplosionKnown(center)
            && AABBUtil.containsInclusive(this.entityBox, entityBox);
    }

    public boolean isExplosionKnown(final Vec3 center) {
        return AABBUtil.containsInclusive(this.explosionBox, center);
    }

    public boolean isEntityPositionKnown(final Vec3 from) {
        return AABBUtil.containsInclusive(this.entityBox, from);
    }

    public void expandKnownArea(final Vec3 center, final AABB entityBox) {
        this.explosionBox = AABBUtil.expand(this.explosionBox, center);
        this.entityBox    = this.entityBox.minmax(entityBox);
    }
}
