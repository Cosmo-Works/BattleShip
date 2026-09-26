package gg.shrekmc.chunk;

import gg.shrekmc.ShrekServerConfig;
import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ChunkPacketCache {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("ShrekChunkPacketCache");

    public static final class Entry<P> {
        final int version;
        final long createdNanos;

        volatile @Nullable Object packet;

        Entry(final int version, final long createdNanos, final @Nullable P packet, final boolean soft) {
            this.version = version;
            this.createdNanos = createdNanos;
            this.packet = packet == null ? null : soft ? new SoftReference<>(packet) : packet;
        }

        @SuppressWarnings("unchecked")
        @Nullable P packet() {
            final Object packet = this.packet;
            return packet instanceof SoftReference<?> reference ? (P) reference.get() : (P) packet;
        }
    }

    public interface Holder<P> {
        int shrek$packetVersion();

        @Nullable Entry<P> shrek$packetEntry();

        void shrek$packetEntry(@Nullable Entry<P> entry);
    }

    public static final class Store<P> {
        private final ConcurrentLinkedQueue<Entry<P>> kept = new ConcurrentLinkedQueue<>();
        private final AtomicInteger size = new AtomicInteger();
        public final LongAdder hits = new LongAdder();
        public final LongAdder misses = new LongAdder();

        public P get(final Holder<P> holder, final Supplier<P> build, final long nowNanos, final long maxAgeNanos, final int maxEntries) {
            return this.get(holder, build, nowNanos, maxAgeNanos, maxEntries, false, false);
        }

        public P get(
            final Holder<P> holder,
            final Supplier<P> build,
            final long nowNanos,
            final long maxAgeNanos,
            final int maxEntries,
            final boolean cacheFirstSend,
            final boolean soft
        ) {
            this.evict(nowNanos, maxAgeNanos, maxEntries);
            final int version = holder.shrek$packetVersion();
            final Entry<P> entry = holder.shrek$packetEntry();
            final boolean recent = entry != null && entry.version == version && nowNanos - entry.createdNanos <= maxAgeNanos;
            if (recent) {
                final P packet = entry.packet();
                if (packet != null) {
                    this.hits.increment();
                    return packet;
                }
            }
            this.misses.increment();
            final P packet = build.get();
            if (holder.shrek$packetVersion() != version) {
                return packet;
            }
            if (recent || cacheFirstSend) {
                final Entry<P> kept = new Entry<>(version, nowNanos, packet, soft);
                holder.shrek$packetEntry(kept);
                this.kept.add(kept);
                this.size.incrementAndGet();
                this.evict(nowNanos, maxAgeNanos, maxEntries);
            } else {
                holder.shrek$packetEntry(new Entry<>(version, nowNanos, null, false));
            }
            return packet;
        }

        void evict(final long nowNanos, final long maxAgeNanos, final int maxEntries) {
            Entry<P> head;
            while ((head = this.kept.peek()) != null
                && (nowNanos - head.createdNanos > maxAgeNanos || (maxEntries > 0 && this.size.get() > maxEntries))) {
                if (this.kept.remove(head)) {
                    this.size.decrementAndGet();
                    head.packet = null;
                }
            }
        }

        public int size() {
            return this.size.get();
        }
    }

    public static final Store<ClientboundLevelChunkWithLightPacket> STORE = new Store<>();

    private ChunkPacketCache() {
    }

    public static ClientboundLevelChunkWithLightPacket packet(final ServerPlayer player, final ServerLevel level, final LevelChunk chunk) {
        final ShrekServerConfig.ChunkPacketCache config = ShrekServerConfig.chunkPacketCache();
        if (!config.enabled) {
            return build(level, chunk);
        }

        final long now = System.nanoTime();
        final ClientboundLevelChunkWithLightPacket packet = STORE.get(
            chunk,
            () -> build(level, chunk),
            now,
            TimeUnit.MILLISECONDS.toNanos(Math.max(0, config.maxAge)),
            config.maxEntries,
            config.cacheFirstSend,
            config.softReferences
        );
        logStats(now, config.statsLogInterval);
        return packet;
    }

    private static final AtomicLong LAST_STATS = new AtomicLong(System.nanoTime());
    private static long loggedHits;
    private static long loggedMisses;

    private static int statsCalls;

    private static void logStats(final long now, final int intervalSeconds) {
        if (intervalSeconds <= 0 || (++statsCalls & 255) != 0) {
            return;
        }
        final long last = LAST_STATS.get();
        if (now - last < TimeUnit.SECONDS.toNanos(intervalSeconds) || !LAST_STATS.compareAndSet(last, now)) {
            return;
        }
        final long hits = STORE.hits.sum();
        final long misses = STORE.misses.sum();
        final long newHits;
        final long newMisses;
        synchronized (ChunkPacketCache.class) {
            newHits = hits - loggedHits;
            newMisses = misses - loggedMisses;
            loggedHits = hits;
            loggedMisses = misses;
        }
        final long total = newHits + newMisses;
        LOGGER.info("Chunk packet cache: {} hits, {} misses ({}% hits) in the last {}s, {} packets kept",
            newHits, newMisses, total == 0 ? 0 : newHits * 100 / total, intervalSeconds, STORE.size());
    }

    private static ClientboundLevelChunkWithLightPacket build(final ServerLevel level, final LevelChunk chunk) {
        final ShrekServerConfig.ChunkPacketCache config = ShrekServerConfig.chunkPacketCache();
        if (config.asyncSerialization) {
            final ClientboundLevelChunkWithLightPacket packet = ClientboundLevelChunkWithLightPacket.shrek$snapshot(chunk, level.getLightEngine());
            serializer(config.asyncSerializationThreads).execute(() -> {
                try {
                    packet.chunkData().shrek$serialize();
                } catch (final Throwable thrown) {
                    LOGGER.error("Couldn't serialize chunk {} {}", packet.x(), packet.z(), thrown);
                }
            });
            return packet;
        }
        return new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null, false);
    }

    private static volatile java.util.concurrent.@Nullable ExecutorService serializer;
    private static int serializerThreads;

    private static java.util.concurrent.ExecutorService serializer(final int threads) {
        java.util.concurrent.ExecutorService current = serializer;
        if (current != null && serializerThreads == threads) {
            return current;
        }
        synchronized (ChunkPacketCache.class) {
            current = serializer;
            if (current != null && serializerThreads == threads) {
                return current;
            }
            final java.util.concurrent.atomic.AtomicInteger ids = new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.ExecutorService created = java.util.concurrent.Executors.newFixedThreadPool(Math.max(1, threads), runnable -> {
                final Thread thread = new Thread(runnable, "Shrek Chunk Serializer #" + ids.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
            if (current != null) {
                current.shutdown();
            }
            serializerThreads = threads;
            serializer = created;
            return created;
        }
    }
}
