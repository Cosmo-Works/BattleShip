// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.mechanics.legacy;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.redstone.RedstoneWireEvaluator;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PandaRedstoneWireEvaluator extends RedstoneWireEvaluator {
    private static final Direction[] FACINGS_HORIZONTAL = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
    private static final Direction[] FACINGS_VERTICAL = {Direction.DOWN, Direction.UP};
    private static final Direction[] FACINGS = ArrayUtils.addAll(FACINGS_VERTICAL, FACINGS_HORIZONTAL);
    private static final Vec3i[] SURROUNDING_BLOCKS_OFFSET = surroundingOffsets();

    private final ArrayDeque<BlockPos> turnOff = new ArrayDeque<>();
    private final ArrayDeque<BlockPos> turnOn = new ArrayDeque<>();
    private final LinkedHashSet<BlockPos> updatedRedstoneWire = new LinkedHashSet<>();

    public PandaRedstoneWireEvaluator(final RedstoneWireBlock wireBlock) {
        super(wireBlock);
    }

    @Override
    public void updatePowerStrength(
        final Level level,
        final BlockPos pos,
        final BlockState state,
        final @Nullable Orientation orientation,
        final boolean updateShape
    ) {
        this.calculateCurrentChanges(level, pos, level.getBlockState(pos));

        final Set<BlockPos> blocksNeedingUpdate = Sets.newLinkedHashSet();
        for (final BlockPos updatedWirePos : this.updatedRedstoneWire) {
            this.addBlocksNeedingUpdate(level, updatedWirePos, blocksNeedingUpdate);
        }
        for (final BlockPos updatedWirePos : this.updatedRedstoneWire.reversed()) {
            this.addAllSurroundingBlocks(updatedWirePos, blocksNeedingUpdate);
        }
        blocksNeedingUpdate.removeAll(this.updatedRedstoneWire);
        this.updatedRedstoneWire.clear();

        for (final BlockPos neighborPos : blocksNeedingUpdate) {
            if (ownedByThisRegion(level, neighborPos)) {
                level.neighborChanged(neighborPos, this.wireBlock, null);
            }
        }
    }

    private void calculateCurrentChanges(final Level level, final BlockPos sourcePos, final BlockState sourceState) {
        if (sourceState.is(this.wireBlock)) {
            this.turnOff.add(sourcePos);
        } else {
            this.checkSurroundingWires(level, sourcePos);
        }

        while (!this.turnOff.isEmpty()) {
            final BlockPos pos = this.turnOff.poll();
            if (!ownedByThisRegion(level, pos) || !level.getBlockState(pos).is(this.wireBlock)) {
                continue;
            }
            final BlockState state = level.getBlockState(pos);
            final int oldPower = state.getValue(RedstoneWireBlock.POWER);
            final int blockPower = this.getBlockSignal(level, pos);
            final int wirePower = this.getIncomingWireSignal(level, pos);
            final int newPower = Math.max(blockPower, wirePower);
            if (newPower < oldPower) {
                if (blockPower > 0 && !this.turnOn.contains(pos)) {
                    this.turnOn.add(pos);
                }
                this.setWireState(level, pos, state, 0);
            } else if (newPower > oldPower) {
                this.setWireState(level, pos, state, newPower);
            }
            this.checkSurroundingWires(level, pos);
        }

        while (!this.turnOn.isEmpty()) {
            final BlockPos pos = this.turnOn.poll();
            if (!ownedByThisRegion(level, pos) || !level.getBlockState(pos).is(this.wireBlock)) {
                continue;
            }
            final BlockState state = level.getBlockState(pos);
            final int oldPower = state.getValue(RedstoneWireBlock.POWER);
            final int blockPower = this.getBlockSignal(level, pos);
            final int wirePower = this.getIncomingWireSignal(level, pos);
            int newPower = Math.max(blockPower, wirePower);
            if (oldPower != newPower) {
                final BlockRedstoneEvent event = new BlockRedstoneEvent(CraftBlock.at(level, pos), oldPower, newPower);
                newPower = event.callEvent() ? event.getNewCurrent() : oldPower;
            }
            if (newPower > oldPower) {
                this.setWireState(level, pos, state, newPower);
            }
            this.checkSurroundingWires(level, pos);
        }

        this.turnOff.clear();
    }

    private void addWireToList(final Level level, final BlockPos pos, final int otherPower) {
        if (!ownedByThisRegion(level, pos)) {
            return;
        }
        final BlockState state = level.getBlockState(pos);
        if (!state.is(this.wireBlock)) {
            return;
        }
        final int power = state.getValue(RedstoneWireBlock.POWER);
        if (power < otherPower - 1 && !this.turnOn.contains(pos)) {
            this.turnOn.add(pos);
        }
        if (power > otherPower && !this.turnOff.contains(pos)) {
            this.turnOff.add(pos);
        }
    }

    private void checkSurroundingWires(final Level level, final BlockPos pos) {
        final BlockState state = ownedByThisRegion(level, pos) ? level.getBlockState(pos) : this.wireBlock.defaultBlockState();
        int ownPower = 0;
        if (state.is(this.wireBlock)) {
            ownPower = state.getValue(RedstoneWireBlock.POWER);
        }
        for (final Direction facingHorizontal : FACINGS_HORIZONTAL) {
            this.addWireToList(level, pos.relative(facingHorizontal), ownPower);
        }
        for (final Direction facingVertical : FACINGS_VERTICAL) {
            final BlockPos offsetPos = pos.relative(facingVertical);
            if (!ownedByThisRegion(level, offsetPos)) {
                continue;
            }
            final boolean solidBlock = level.getBlockState(offsetPos).isRedstoneConductor(level, offsetPos);
            for (final Direction facingHorizontal : FACINGS_HORIZONTAL) {
                final BlockPos adjacentPos = offsetPos.relative(facingHorizontal);
                if (facingVertical == Direction.UP && !solidBlock) {
                    this.addWireToList(level, adjacentPos, ownPower);
                } else if (facingVertical == Direction.DOWN && solidBlock && ownedByThisRegion(level, adjacentPos)
                    && !level.getBlockState(adjacentPos).isRedstoneConductor(level, adjacentPos)) {
                    this.addWireToList(level, adjacentPos, ownPower);
                }
            }
        }
    }

    private void addBlocksNeedingUpdate(final Level level, final BlockPos pos, final Set<BlockPos> set) {
        if (!ownedByThisRegion(level, pos)) {
            return;
        }
        final Set<Direction> connectedSides = this.getSidesToPower(level, pos);
        for (final Direction facing : FACINGS) {
            final BlockPos offsetPos = pos.relative(facing);
            if (!ownedByThisRegion(level, offsetPos)) {
                continue;
            }
            final BlockState state = level.getBlockState(offsetPos);
            final boolean connected = connectedSides.contains(facing.getOpposite()) || facing == Direction.DOWN;
            if ((connected || (facing.getAxis().isHorizontal() && state.shouldRedstoneWireConnectTo(level, offsetPos, facing)))
                && canBlockBePoweredFromSide(state, facing, true)) {
                set.add(offsetPos);
            }
            if (connected && state.isRedstoneConductor(level, offsetPos)) {
                for (final Direction adjacentFacing : FACINGS) {
                    final BlockPos adjacentPos = offsetPos.relative(adjacentFacing);
                    if (ownedByThisRegion(level, adjacentPos)
                        && canBlockBePoweredFromSide(level.getBlockState(adjacentPos), adjacentFacing, false)) {
                        set.add(adjacentPos);
                    }
                }
            }
        }
    }

    private static boolean canBlockBePoweredFromSide(final BlockState state, final Direction side, final boolean isWire) {
        final Block block = state.getBlock();
        if (state.isAir()) {
            return false;
        }
        if (block instanceof PistonBaseBlock && state.getValue(PistonBaseBlock.FACING) == side.getOpposite()) {
            return false;
        }
        if (block instanceof DiodeBlock && state.getValue(DiodeBlock.FACING) != side.getOpposite()) {
            return isWire && block instanceof ComparatorBlock
                && state.getValue(ComparatorBlock.FACING).getAxis() != side.getAxis()
                && side.getAxis().isHorizontal();
        }
        return !(block instanceof RedstoneWallTorchBlock) || (!isWire && state.getValue(RedstoneWallTorchBlock.FACING) == side);
    }

    private Set<Direction> getSidesToPower(final Level level, final BlockPos pos) {
        final Set<Direction> sidesToPower = new HashSet<>();
        final BlockState state = level.getBlockState(pos);
        if (!state.is(this.wireBlock)) {
            return sidesToPower;
        }
        for (final Direction direction : FACINGS_HORIZONTAL) {
            final RedstoneSide side = state.getValue(RedstoneWireBlock.PROPERTY_BY_DIRECTION.get(direction));
            if (side.isConnected()) {
                sidesToPower.add(direction);
            }
        }
        return sidesToPower;
    }

    private void addAllSurroundingBlocks(final BlockPos pos, final Set<BlockPos> set) {
        for (final Vec3i offset : SURROUNDING_BLOCKS_OFFSET) {
            set.add(pos.offset(offset));
        }
    }

    private void setWireState(final Level level, final BlockPos pos, final BlockState state, final int power) {
        level.setBlock(pos, state.setValue(RedstoneWireBlock.POWER, power), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        this.updatedRedstoneWire.add(pos);
    }

    private static boolean ownedByThisRegion(final Level level, final BlockPos pos) {
        return TickThread.isTickThreadFor(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static Vec3i[] surroundingOffsets() {
        final Set<Vec3i> set = Sets.newLinkedHashSet();
        for (final Direction facing : FACINGS) {
            set.add(facing.getUnitVec3i());
        }
        final Set<Vec3i> offsets = Sets.newLinkedHashSet(set);
        for (final Vec3i neighborOffset : offsets) {
            for (final Vec3i adjacentOffset : offsets) {
                set.add(neighborOffset.offset(adjacentOffset));
            }
        }
        set.remove(Vec3i.ZERO);
        return set.toArray(new Vec3i[0]);
    }
}
