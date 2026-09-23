// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.mechanics;

import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class LadderBehaviour {
    private static final Map<Direction, VoxelShape> LEGACY_SHAPES = Shapes.rotateHorizontal(Block.boxZ(16.0, 14.0, 16.0));

    private LadderBehaviour() {
    }

    public static VoxelShape collisionShape(final BlockGetter level, final BlockState state) {
        if (level instanceof final Level gameLevel && gameLevel.mechanicsTarget().before(MechanicVersion.v1_9)) {
            return LEGACY_SHAPES.get(state.getValue(LadderBlock.FACING));
        }
        return LadderBlock.SHAPES.get(state.getValue(LadderBlock.FACING));
    }
}
