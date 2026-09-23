// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.mechanics;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class FenceGateBehaviour {
    private FenceGateBehaviour() {
    }

    public static boolean shouldUpdatePower(final Level level, final BlockState state, final Block neighbor, final boolean hasPower) {
        if (level.mechanicsTarget().atLeast(MechanicVersion.v1_10)) {
            return state.getValue(FenceGateBlock.POWERED) != hasPower;
        }
        return hasPower || neighbor.defaultBlockState().isSignalSource();
    }

    public static boolean openAfterPower(final Level level, final BlockState state, final boolean hasPower, final boolean wasPowered) {
        if (level.mechanicsTarget().atLeast(MechanicVersion.v1_10)) {
            return hasPower;
        }
        if (hasPower == wasPowered || state.getValue(FenceGateBlock.OPEN) != wasPowered) {
            return state.getValue(FenceGateBlock.OPEN);
        }
        return hasPower;
    }
}
