// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.mechanics.sake;

import ca.spottedleaf.moonrise.common.util.TickThread;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DispenseRelocationHandler {
    private static final int MAX_HORIZONTAL_DISTANCE = 64;
    private static final int MAX_VERTICAL_DISTANCE = 384;

    private final Level level;
    private final Map<BlockPos, DispenserGroup> groups = new HashMap<>();

    public DispenseRelocationHandler(final Level level) {
        this.level = level;
    }

    public BlockPos relocatePosition(final BlockPos spawnPos, final BlockPos sourcePos, final BlockState sourceState) {
        return this.groupContaining(sourcePos, sourceState).getSpawnPosition(spawnPos);
    }

    public void clear() {
        this.groups.clear();
    }

    private DispenserGroup groupContaining(final BlockPos sourcePos, final BlockState sourceState) {
        final DispenserGroup existing = this.groups.get(sourcePos);
        if (existing != null) {
            return existing;
        }

        final DispenserGroup group = new DispenserGroup();
        for (final BlockPos dispenserPos : this.connectedDispensers(sourcePos, sourceState)) {
            this.groups.putIfAbsent(dispenserPos, group);
        }
        return group;
    }

    private Set<BlockPos> connectedDispensers(final BlockPos pos, final BlockState state) {
        final Set<BlockPos> dispensers = new HashSet<>();
        dispensers.add(pos);

        final Direction facing = state.getValue(DispenserBlock.FACING);
        if (facing.getAxis().isVertical() || !this.meetsConditions(pos, state, facing, true)) {
            return dispensers;
        }

        for (final Direction sideways : facing.getClockWise().getAxis().getDirections()) {
            this.collectAlong(pos, sideways, MAX_HORIZONTAL_DISTANCE, state, facing, dispensers);
        }
        for (final BlockPos horizontalPos : List.copyOf(dispensers)) {
            for (final Direction vertical : Direction.Plane.VERTICAL) {
                this.collectAlong(horizontalPos, vertical, MAX_VERTICAL_DISTANCE, state, facing, dispensers);
            }
        }
        return dispensers;
    }

    private void collectAlong(
        final BlockPos origin,
        final Direction direction,
        final int maxDistance,
        final BlockState expected,
        final Direction facing,
        final Set<BlockPos> dispensers
    ) {
        for (int distance = 1; distance < maxDistance; ++distance) {
            final BlockPos adjacentPos = origin.relative(direction, distance);
            if (!this.thisRegionOwns(adjacentPos)) {
                break;
            }

            final BlockState adjacentState = this.level.getBlockState(adjacentPos);
            if (expected != adjacentState || !this.meetsConditions(adjacentPos, adjacentState, facing, false)) {
                break;
            }
            dispensers.add(adjacentPos);
        }
    }

    private boolean meetsConditions(
        final BlockPos pos,
        final BlockState state,
        final Direction facing,
        final boolean source
    ) {
        if (!source && !this.level.getBlockTicks().willTickThisTick(pos, state.getBlock())) {
            return false;
        }
        if (this.blockedInFront(pos, facing)) {
            return false;
        }
        if (!(this.level.getBlockEntity(pos) instanceof Container container)) {
            return false;
        }
        for (int slot = 0; slot < container.getContainerSize(); ++slot) {
            if (!emptyOrTnt(container.getItem(slot))) {
                return false;
            }
        }
        return true;
    }

    private boolean blockedInFront(final BlockPos pos, final Direction facing) {
        final BlockState inFront = this.level.getBlockState(pos.relative(facing));
        final BlockState beneathFront = this.level.getBlockState(pos.relative(facing).below());
        return !inFront.getCollisionShape(this.level, pos).isEmpty()
            && !beneathFront.getCollisionShape(this.level, pos).isEmpty();
    }

    private boolean thisRegionOwns(final BlockPos pos) {
        return TickThread.isTickThreadFor(this.level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static boolean emptyOrTnt(final ItemStack itemStack) {
        return itemStack.isEmpty() || itemStack.is(Items.TNT);
    }
}
