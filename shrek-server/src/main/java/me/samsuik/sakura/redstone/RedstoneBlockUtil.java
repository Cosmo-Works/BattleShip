// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.redstone;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class RedstoneBlockUtil {
    private RedstoneBlockUtil() {
    }

    public static boolean needsRedstoneUpdate(final BlockState state) {
        return state.isAir()
            || state.liquid()
            || !state.isSpecialBlock();
    }

    public static boolean needsShapeUpdate(final BlockState state, final Block block) {
        return state.is(Blocks.OBSERVER)
            || state.liquid()
            || block instanceof FallingBlock
            || block instanceof LiquidBlockContainer;
    }
}
