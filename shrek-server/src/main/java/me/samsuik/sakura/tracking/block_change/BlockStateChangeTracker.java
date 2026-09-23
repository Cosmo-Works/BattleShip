package me.samsuik.sakura.tracking.block_change;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import me.samsuik.sakura.tracking.block_change.callback.BlockChangeCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.LongConsumer;

@SuppressWarnings("ConstantValue")
@NullMarked
public final class BlockStateChangeTracker {
    private final Long2ObjectMap<ListeningToPositions> identifiersInUse = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<LongOpenHashSet> chunkAssociatedIdentifiers = new Long2ObjectOpenHashMap<>();
    private final Level level;
    private long identifier = Long.MIN_VALUE;

    public BlockStateChangeTracker(final Level level) {
        this.level = level;
    }

    public long firstChange(final Set<BlockPos> positions, final BlockFilter filter, final Runnable callback) {
        final LongConsumer removeAfterUse = identifier -> {
            callback.run();
            this.stopListening(identifier);
        };

        return this.listenForChanges(positions, filter, BlockChangeCallback.identifier(removeAfterUse));
    }

    public long allChanges(final Set<BlockPos> positions, final BlockFilter filter, final LongConsumer callback) {
        return this.listenForChanges(positions, filter, BlockChangeCallback.identifier(callback));
    }

    public long listenForChanges(final Set<BlockPos> positions, final BlockFilter filter, final BlockChangeCallback callback) {
        final ObjectImmutableList<BlockPos> immutablePositions = new ObjectImmutableList<>(positions);
        final long identifier = this.identifier++;
        final ListeningToPositions listener = new ListeningToPositions(immutablePositions, identifier, filter, callback);
        this.register(listener, true);
        return identifier;
    }

    public void stopListening(final long identifier) {
        final ListeningToPositions listener = this.identifiersInUse.remove(identifier);
        if (listener == null) {
            return;
        }

        this.unregisterFromChunks(listener, true);
    }

    public List<ListeningToPositions> getListenersAtChunk(final LevelChunk chunk) {
        final LongOpenHashSet identifiers = this.chunkAssociatedIdentifiers.get(chunk.coordinateKey);
        if (identifiers == null) {
            return Collections.emptyList();
        }

        final List<ListeningToPositions> listeners = new ObjectArrayList<>();
        for (final long identifier : identifiers) {
            listeners.add(this.identifiersInUse.get(identifier));
        }

        return listeners;
    }

    public void mergeFrom(final BlockStateChangeTracker from) {
        for (final ListeningToPositions listener : from.identifiersInUse.values()) {
            this.adopt(listener);
        }
        from.clear();
    }

    public void splitInto(
        final int chunkToRegionShift,
        final Long2ReferenceOpenHashMap<RegionizedWorldData> regionToData
    ) {
        for (final ListeningToPositions listener : this.identifiersInUse.values()) {
            if (listener.positions().isEmpty()) {
                continue;
            }

            final BlockPos pos = listener.positions().get(0);
            final RegionizedWorldData into = regionToData.get(CoordinateUtils.getChunkKey(
                (pos.getX() >> 4) >> chunkToRegionShift,
                (pos.getZ() >> 4) >> chunkToRegionShift
            ));
            if (into != null) {
                into.shrekBlockStateChangeTracker.adopt(listener);
            }
        }

        this.clear();
    }

    public void adopt(final ListeningToPositions listener) {
        this.register(listener.withIdentifier(this.identifier++), false);
    }

    private void register(final ListeningToPositions listener, final boolean attachLoadedChunks) {
        this.identifiersInUse.put(listener.identifier(), listener);

        for (final BlockPos pos : listener.positions()) {
            final long chunkKey = ChunkPos.pack(pos);
            final LongOpenHashSet chunkIdentifiers = this.chunkAssociatedIdentifiers.computeIfAbsent(chunkKey, k -> new LongOpenHashSet());
            if (!chunkIdentifiers.add(listener.identifier())) {
                continue;
            }

            if (!attachLoadedChunks) {
                continue;
            }

            final LevelChunk chunk = this.level.getChunkIfLoaded(pos);
            if (chunk != null) {
                chunk.getBlockStateWatcher().startListening(listener);
            }
        }
    }

    private void unregisterFromChunks(final ListeningToPositions listener, final boolean detachLoadedChunks) {
        for (final BlockPos pos : listener.positions()) {
            final long chunkKey = ChunkPos.pack(pos);
            final LongOpenHashSet chunkIdentifiers = this.chunkAssociatedIdentifiers.get(chunkKey);
            if (chunkIdentifiers == null || !chunkIdentifiers.remove(listener.identifier())) {
                continue;
            }

            if (!detachLoadedChunks) {
                continue;
            }

            final LevelChunk chunk = this.level.getChunkIfLoaded(pos);
            if (chunk != null) {
                chunk.getBlockStateWatcher().stopListening(listener);
            }
        }
    }

    private void clear() {
        this.identifiersInUse.clear();
        this.chunkAssociatedIdentifiers.clear();
    }
}
