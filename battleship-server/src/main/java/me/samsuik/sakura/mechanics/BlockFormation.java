// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.mechanics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class BlockFormation {
    private BlockFormation() {
    }

    public static boolean canLiquidFormBlock(final Level level, final BlockPos pos, final FluidState fluidState) {
        return canLiquidFormBlock(level, pos, fluidState, target(level));
    }

    public static boolean lavaAllowsWaterReplacement(
        final FluidState state,
        final BlockGetter level,
        final BlockPos pos,
        final Fluid other
    ) {
        if (usesLegacyFluidMixing(level)) {
            return false;
        }
        return state.getHeight(level, pos) >= 0.44444445F && other.is(FluidTags.WATER);
    }

    public static boolean waterAllowsReplacement(
        final BlockGetter level,
        final Fluid other,
        final Direction direction
    ) {
        return canBeReplacedFromThisDirection(level, direction) && !other.is(FluidTags.WATER);
    }

    public static MinecraftMechanicsTarget target(final Level level) {
        if (level.sakuraConfig().environment.blockGeneration.legacyBlockFormation) {
            return MinecraftMechanicsTarget.legacy();
        }
        return level.mechanicsTarget();
    }

    private static boolean canLiquidFormBlock(
        final Level level,
        final BlockPos pos,
        final FluidState fluidState,
        final MinecraftMechanicsTarget mechanicsTarget
    ) {
        if (alwaysSolidifies(mechanicsTarget)) {
            return true;
        }
        if (occupiesEnoughOfTheBlock(level, pos, fluidState, mechanicsTarget)) {
            return true;
        }
        if (mechanicsTarget.before(MechanicVersion.v1_13)) {
            return legacyLevel(fluidState) < 4;
        }
        return true;
    }

    private static boolean alwaysSolidifies(final MinecraftMechanicsTarget mechanicsTarget) {
        return mechanicsTarget.atLeast(MechanicVersion.v1_16)
            || mechanicsTarget.before(MechanicVersion.v1_10) && mechanicsTarget.isPaperOrDerivative();
    }

    private static boolean occupiesEnoughOfTheBlock(
        final Level level,
        final BlockPos pos,
        final FluidState fluidState,
        final MinecraftMechanicsTarget mechanicsTarget
    ) {
        return mechanicsTarget.atLeast(MechanicVersion.v1_13)
            && fluidState.getHeight(level, pos) >= 0.44444445f;
    }

    private static boolean usesLegacyFluidMixing(final BlockGetter level) {
        return level instanceof ServerLevel serverLevel
            && target(serverLevel).before(MechanicVersion.v1_13);
    }

    private static boolean canBeReplacedFromThisDirection(
        final BlockGetter level,
        final Direction direction
    ) {
        if (direction == Direction.DOWN) {
            return true;
        }
        return usesLegacyFluidMixing(level);
    }

    private static int legacyLevel(final FluidState fluidState) {
        if (fluidState.isSource()) {
            return 0;
        }
        return 8 - Math.min(fluidState.getAmount(), 8) + (fluidState.getValue(FlowingFluid.FALLING) ? 8 : 0);
    }
}
