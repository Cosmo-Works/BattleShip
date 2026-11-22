package me.samsuik.sakura.entity;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface SakuraEntity {
    SakuraEntityController getSakuraEntityController();

    default Entity asEntity() {
        return (Entity) this;
    }

    default boolean canLink(final Entity otherEntity, final int tickDifference) {
        return true;
    }

    default boolean samePropertiesAndState(final SakuraEntity otherSakuraEntity, final int tickDifference) {
        final SakuraEntityController otherController = otherSakuraEntity.getSakuraEntityController();
        return this.sameProperties(otherSakuraEntity.asEntity(), tickDifference)
            && this.isInSimilarState(otherController.getEntityState());
    }

    default boolean isInSimilarState(final EntityState state) {
        return this.getSakuraEntityController().getEntityState().isInSimilarState(state);
    }

    default boolean sameProperties(final Entity otherEntity, final int tickDifference) {
        return true;
    }

    default void catchupOnTicks(final int ticks) {
    }
}
