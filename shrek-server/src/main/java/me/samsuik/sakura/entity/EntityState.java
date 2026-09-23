package me.samsuik.sakura.entity;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record EntityState(
    Vec3 oldPosition,
    Vec3 position,
    Vec3 movement,
    AABB bb,
    Vec3 stuckSpeed,
    Optional<BlockPos> supportingPos,
    boolean onGround,
    double fallDistance
) {
    public static EntityState unknown() {
        return new EntityState(
            Vec3.ZERO,
            Vec3.ZERO,
            Vec3.ZERO,
            new AABB(Vec3.ZERO, Vec3.ZERO),
            Vec3.ZERO,
            Optional.empty(),
            false,
            0.0
        );
    }

    public static EntityState of(final Entity entity) {
        return new EntityState(
            entity.oldPosition(),
            entity.position(),
            entity.getDeltaMovement(),
            entity.getBoundingBox(),
            entity.sakura$stuckSpeed(),
            entity.mainSupportingBlockPos,
            entity.onGround(),
            entity.fallDistance
        );
    }

    public void apply(final Entity entity) {
        entity.setOldPosAndRot(this.oldPosition, 0.0f, 0.0f);
        entity.setPos(this.position);
        entity.setDeltaMovement(this.movement);
        entity.setBoundingBox(this.bb);
        entity.makeStuckInBlock(Blocks.AIR.defaultBlockState(), this.stuckSpeed);
        entity.onGround = this.onGround;
        entity.mainSupportingBlockPos = this.supportingPos;
        entity.fallDistance = this.fallDistance;
    }

    public boolean isInSimilarState(final EntityState state) {
        return this.comparePositionAndMotion(state, false)
            && this.compareFallDistance(state);
    }

    public boolean comparePositionAndMotion(final EntityState state, final boolean strict) {
        return this.position.equals(state.position())
            && this.movement.equals(state.movement())
            && (!strict || this.bb.equals(state.bb()));
    }

    public boolean compareFallDistance(final EntityState state) {
        return this.fallDistance == state.fallDistance()
            || this.fallDistance <= 2.5 && state.fallDistance() <= 2.5;
    }
}
