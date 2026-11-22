package dev.cosmoworks.battleship.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("Battleship")
class ChunkPacketCacheTest {
    private static final long MAX_AGE = TimeUnit.SECONDS.toNanos(5);

    private static final class FakeChunk implements ChunkPacketCache.Holder<List<Integer>> {
        final List<Integer> blocks = new ArrayList<>();
        int version;
        ChunkPacketCache.@Nullable Entry<List<Integer>> entry;
        int builds;

        void change(final int index, final int value) {
            this.blocks.set(index, value);
            this.version++;
            this.entry = null;
        }

        List<Integer> build() {
            this.builds++;
            return List.copyOf(this.blocks);
        }

        @Override
        public int battleship$packetVersion() {
            return this.version;
        }

        @Override
        public ChunkPacketCache.@Nullable Entry<List<Integer>> battleship$packetEntry() {
            return this.entry;
        }

        @Override
        public void battleship$packetEntry(final ChunkPacketCache.@Nullable Entry<List<Integer>> entry) {
            this.entry = entry;
        }
    }

    private static FakeChunk chunk(final int size) {
        final FakeChunk chunk = new FakeChunk();
        for (int i = 0; i < size; ++i) {
            chunk.blocks.add(0);
        }
        return chunk;
    }

    @Test
    void cachedPacketAlwaysEqualsAFreshOneAfterMutations() {
        final ChunkPacketCache.Store<List<Integer>> store = new ChunkPacketCache.Store<>();
        final FakeChunk chunk = chunk(64);
        final Random random = new Random(42);
        long now = 0;
        int reused = 0;
        for (int step = 0; step < 10_000; ++step) {
            now += TimeUnit.MILLISECONDS.toNanos(random.nextInt(50));
            if (random.nextInt(4) == 0) {
                chunk.change(random.nextInt(64), random.nextInt(1000));
            }
            final int buildsBefore = chunk.builds;
            final List<Integer> sent = store.get(chunk, chunk::build, now, MAX_AGE, 4096);
            assertEquals(chunk.blocks, sent, "step " + step);
            if (chunk.builds == buildsBefore) {
                ++reused;
            }
        }
        assertTrue(reused > 1000, "the cache should be used: " + reused);
    }

    @Test
    void firstSendOnlyLeavesAMarker() {
        final ChunkPacketCache.Store<List<Integer>> store = new ChunkPacketCache.Store<>();
        final FakeChunk chunk = chunk(4);
        final List<Integer> first = store.get(chunk, chunk::build, 0, MAX_AGE, 4096);
        assertEquals(0, store.size());
        final List<Integer> second = store.get(chunk, chunk::build, 1, MAX_AGE, 4096);
        assertNotSame(first, second);
        assertEquals(1, store.size());
        final List<Integer> third = store.get(chunk, chunk::build, 2, MAX_AGE, 4096);
        assertSame(second, third);
        assertEquals(2, chunk.builds);
    }

    @Test
    void expiresAfterMaxAge() {
        final ChunkPacketCache.Store<List<Integer>> store = new ChunkPacketCache.Store<>();
        final FakeChunk chunk = chunk(4);
        store.get(chunk, chunk::build, 0, MAX_AGE, 4096);
        final List<Integer> kept = store.get(chunk, chunk::build, 1, MAX_AGE, 4096);
        assertSame(kept, store.get(chunk, chunk::build, MAX_AGE, MAX_AGE, 4096));
        assertNotSame(kept, store.get(chunk, chunk::build, MAX_AGE + 2, MAX_AGE, 4096));
        assertEquals(0, store.size(), "expired packets are dropped");
    }

    @Test
    void boundedByMaxEntries() {
        final ChunkPacketCache.Store<List<Integer>> store = new ChunkPacketCache.Store<>();
        final List<FakeChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 20; ++i) {
            final FakeChunk chunk = chunk(1);
            chunks.add(chunk);
            store.get(chunk, chunk::build, i, MAX_AGE, 8);
            store.get(chunk, chunk::build, i, MAX_AGE, 8);
        }
        store.get(chunks.getFirst(), chunks.getFirst()::build, 100, MAX_AGE, 8);
        assertTrue(store.size() <= 8, "size " + store.size());
    }

    @Test
    void changeWhileBuildingIsNotKept() {
        final ChunkPacketCache.Store<List<Integer>> store = new ChunkPacketCache.Store<>();
        final FakeChunk chunk = chunk(4);
        store.get(chunk, chunk::build, 0, MAX_AGE, 4096);

        final List<Integer> built = store.get(chunk, () -> {
            final List<Integer> snapshot = chunk.build();
            chunk.change(0, 7);
            return snapshot;
        }, 1, MAX_AGE, 4096);
        assertEquals(0, built.getFirst());
        assertEquals(List.of(7, 0, 0, 0), store.get(chunk, chunk::build, 2, MAX_AGE, 4096));
    }
}
