// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.redstone.network;

import io.papermc.paper.threadedregions.TickRegionScheduler;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.samsuik.sakura.tracking.block_change.BlockFilter;
import me.samsuik.sakura.tracking.block_change.BlockStateChangeTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class RedstoneNetwork {
    private final Map<BlockPos, RedstoneWireState> redstoneChanges;
    private final List<RedstoneWireState> redstoneUpdates;
    private final List<@Nullable BlockPos> neighborUpdates;
    private final BitSet redundantUpdates;
    private final BlockStateChangeTracker blockStateChangeTracker;
    private final LongArrayList listeners = new LongArrayList(2);

    public RedstoneNetwork(
        final Map<BlockPos, RedstoneWireState> redstoneChanges,
        final List<RedstoneWireState> redstoneUpdates,
        final List<@Nullable BlockPos> neighborUpdates,
        final BitSet redundantUpdates,
        final BlockStateChangeTracker blockStateChangeTracker
    ) {
        this.redstoneChanges = redstoneChanges;
        this.redstoneUpdates = redstoneUpdates;
        this.neighborUpdates = neighborUpdates;
        this.redundantUpdates = redundantUpdates;
        this.blockStateChangeTracker = blockStateChangeTracker;
        this.register(redstoneChanges.keySet(), blockStateChangeTracker);
    }

    public void unregister() {
        for (final long identifier : this.listeners) {
            this.blockStateChangeTracker.stopListening(identifier);
        }
        this.listeners.clear();
    }

    public boolean isWirePos(final BlockPos pos) {
        return this.redstoneChanges.containsKey(pos);
    }

    public boolean apply(final Level level) {
        if (this.listeners.isEmpty() || !this.wiresMatchCachedPower(level)) {
            return false;
        }

        final RedStoneWireBlock wireBlock = (RedStoneWireBlock) Blocks.REDSTONE_WIRE;
        final Orientation defaultOrientation = ExperimentalRedstoneUtils.initialOrientation(level, null, null);
        int updateFrom = 0;
        for (final RedstoneWireState redstoneUpdate : this.redstoneUpdates) {
            if (redstoneUpdate.skip()) {
                updateFrom = redstoneUpdate.updateIndex();
                continue;
            }

            final int updateTo = redstoneUpdate.updateIndex();
            this.performUpdates(level, wireBlock, defaultOrientation, updateFrom, updateTo);
            updateFrom = updateTo;

            final BlockPos wirePos = redstoneUpdate.position();
            final BlockState state = level.getBlockState(wirePos);
            final BlockState newState = state.setValue(RedStoneWireBlock.POWER, redstoneUpdate.newPower());
            if (level.setBlock(wirePos, newState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)
                && redstoneUpdate.updateShapes()) {
                TickRegionScheduler.getCurrentRegionizedWorldData().turbo.updateNeighborShapes(level, wirePos, newState);
            }
        }
        this.performUpdates(level, wireBlock, defaultOrientation, updateFrom, this.neighborUpdates.size());
        return true;
    }

    private void register(final Set<BlockPos> wirePositions, final BlockStateChangeTracker tracker) {
        final ObjectOpenHashSet<BlockPos> positions = new ObjectOpenHashSet<>();
        for (final BlockPos neighbor : this.neighborUpdates) {
            if (neighbor != null) {
                positions.add(neighbor);
            }
        }
        positions.addAll(wirePositions);

        this.listeners.add(tracker.firstChange(positions, BlockFilter.REDSTONE_COMPONENT, this::unregister));
        this.listeners.add(tracker.firstChange(positions, BlockFilter.BLOCK_TYPE, this::allowRedundantNeighborUpdates));
    }

    private void allowRedundantNeighborUpdates() {
        for (int updateIndex = 0; updateIndex < this.neighborUpdates.size(); ++updateIndex) {
            if (!this.redundantUpdates.get(updateIndex)) {
                continue;
            }
            final BlockPos pos = this.neighborUpdates.get(updateIndex);
            if (pos == null || !this.redstoneChanges.containsKey(pos)) {
                this.redundantUpdates.clear(updateIndex);
            }
        }
    }

    private void performUpdates(
        final Level level,
        final RedStoneWireBlock wireBlock,
        final @Nullable Orientation orientation,
        final int updateFrom,
        final int updateTo
    ) {
        for (int updateIndex = updateFrom; updateIndex < updateTo; ++updateIndex) {
            if (this.redundantUpdates.get(updateIndex)) {
                continue;
            }
            final BlockPos updatePos = this.neighborUpdates.get(updateIndex);
            if (updatePos != null) {
                level.getBlockState(updatePos).handleNeighborChanged(level, updatePos, wireBlock, orientation, false);
            }
        }
    }

    private boolean wiresMatchCachedPower(final Level level) {
        for (final Map.Entry<BlockPos, RedstoneWireState> wireEntry : this.redstoneChanges.entrySet()) {
            if (level.getBlockState(wireEntry.getKey()).getValue(RedStoneWireBlock.POWER) != wireEntry.getValue().oldPower()) {
                return false;
            }
        }
        return true;
    }
}
