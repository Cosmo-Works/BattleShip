// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.redstone;

import ca.spottedleaf.moonrise.common.util.TickThread;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import me.samsuik.sakura.redstone.network.RedstoneNetwork;
import me.samsuik.sakura.redstone.network.RedstoneSourceKey;
import me.samsuik.sakura.redstone.network.RedstoneWireState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class TrackedRedstoneChanges {
    private final Map<BlockPos, RedstoneWireState> redstoneChanges = new Object2ObjectLinkedOpenHashMap<>();
    private final List<RedstoneWireState> redstoneUpdates = new ObjectArrayList<>();
    private final List<@Nullable BlockPos> neighborUpdates = new ObjectArrayList<>();
    private final RedstoneSourceKey sourceKey;
    private final Level level;

    public TrackedRedstoneChanges(final Level level, final RedstoneSourceKey sourceKey) {
        this.level = level;
        this.sourceKey = sourceKey;
    }

    public void addWireUpdate(final BlockPos redstoneWirePos, final int oldPower, final int newPower) {
        final RedstoneWireState redstoneWireState = new RedstoneWireState(
            redstoneWirePos,
            oldPower,
            newPower,
            false,
            false,
            this.neighborUpdates.size()
        );
        this.redstoneChanges.compute(redstoneWirePos, (pos, state) -> state == null ? redstoneWireState : state.newPower(newPower));
        this.redstoneUpdates.add(redstoneWireState);
    }

    public void addNeighborUpdate(final BlockPos updatePos, final boolean single) {
        if (single) {
            this.neighborUpdates.add(updatePos);
            return;
        }
        for (final Direction direction : NeighborUpdater.UPDATE_ORDER) {
            this.neighborUpdates.add(updatePos.relative(direction));
        }
    }

    public RedstoneSourceKey getSourceKey() {
        return this.sourceKey;
    }

    public @Nullable RedstoneNetwork createNetwork() {
        final Level level = this.level;
        final boolean fullDeduplication = this.sourceKey.deduplicate();
        final Object2IntOpenHashMap<BlockPos> wireIndexes = new Object2IntOpenHashMap<>();
        wireIndexes.defaultReturnValue(-1);

        for (int wireIndex = this.redstoneUpdates.size() - 1; wireIndex >= 0; wireIndex--) {
            final RedstoneWireState redstoneUpdate = this.redstoneUpdates.get(wireIndex);
            final BlockPos wirePos = redstoneUpdate.position();
            if (!TickThread.isTickThreadFor(level, wirePos.getX() >> 4, wirePos.getZ() >> 4)) {
                return null;
            }

            final BlockState blockStateBelow = level.getBlockState(wirePos.below());
            if (blockStateBelow.is(Blocks.PISTON_HEAD) || blockStateBelow.is(BlockTags.TRAPDOORS)) {
                return null;
            }

            // Filter out wires updates that are not the first and last update
            if (fullDeduplication && redstoneUpdate.isRedundantWireUpdate(this.redstoneChanges.get(wirePos))) {
                this.redstoneUpdates.set(wireIndex, redstoneUpdate.skip(true));
            }
            wireIndexes.put(wirePos, wireIndex);
        }

        final BitSet redundantUpdates = new BitSet(this.neighborUpdates.size());
        for (int updateIndex = 0; updateIndex < this.neighborUpdates.size(); ++updateIndex) {
            final BlockPos updatePos = this.neighborUpdates.get(updateIndex);
            if (updatePos == null || !TickThread.isTickThreadFor(level, updatePos.getX() >> 4, updatePos.getZ() >> 4)) {
                this.neighborUpdates.set(updateIndex, null);
                continue;
            }

            final BlockState state = level.getBlockState(updatePos);
            final Block block = state.getBlock();
            if (!RedstoneBlockUtil.needsRedstoneUpdate(state)) {
                redundantUpdates.set(updateIndex);
            }
            // Never update redstone wires tracked by the network
            if (state.is(Blocks.REDSTONE_WIRE) && wireIndexes.containsKey(updatePos)) {
                this.neighborUpdates.set(updateIndex, null);
                continue;
            }
            if (!RedstoneBlockUtil.needsShapeUpdate(state, block)) {
                continue;
            }
            for (final Direction direction : NeighborUpdater.UPDATE_ORDER) {
                final int neighborWireIndex = wireIndexes.getInt(updatePos.relative(direction));
                if (neighborWireIndex < 0) {
                    continue;
                }
                final RedstoneWireState neighborWireState = this.redstoneUpdates.get(neighborWireIndex);
                this.redstoneUpdates.set(neighborWireIndex, neighborWireState.updateShapes(true));
            }
        }

        if (fullDeduplication) {
            final LongOpenHashSet updates = new LongOpenHashSet(wireIndexes.size() * 4);
            for (int updateIndex = this.neighborUpdates.size() - 1; updateIndex >= 0; --updateIndex) {
                final BlockPos updatePos = this.neighborUpdates.get(updateIndex);
                if (updatePos != null && !updates.add(updatePos.asLong())) {
                    this.neighborUpdates.set(updateIndex, null);
                }
            }
        }

        return new RedstoneNetwork(this.redstoneChanges, this.redstoneUpdates, this.neighborUpdates, redundantUpdates, level.blockStateChangeTracker());
    }
}
