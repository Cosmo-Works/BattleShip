package me.samsuik.sakura.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public final class KeepLoadedChunks {
    private static final Logger LOGGER = LoggerFactory.getLogger("ShrekWorlds");
    private static final int MAX_SIZE = 64;
    private static final Map<ServerLevel, LongSet> applied = new IdentityHashMap<>();

    public static synchronized void sync(final ServerLevel level) {
        if (level.getChunkSource() == null) {
            return;
        }

        final LongSet next = chunksFromConfig(level);
        final LongSet previous = applied.getOrDefault(level, LongSets.emptySet());
        final ServerChunkCache chunks = level.getChunkSource();

        for (final long key : previous) {
            if (!next.contains(key)) {
                dropTicket(chunks, key);
            }
        }
        for (final long key : next) {
            if (!previous.contains(key)) {
                holdTicket(chunks, key);
            }
        }

        if (next.isEmpty()) {
            applied.remove(level);
        } else {
            applied.put(level, next);
            LOGGER.info("Keeping {} chunks loaded in {}", next.size(), level.dimension().identifier());
        }
    }

    private static LongSet chunksFromConfig(final ServerLevel level) {
        final LongSet chunks = new LongOpenHashSet();
        final Map<String, KeepLoadedArea> areas = level.shrekConfig().chunks.keepLoaded;
        if (areas == null) {
            return chunks;
        }

        final String worldName = level.dimension().identifier().toString();
        for (final Map.Entry<String, KeepLoadedArea> entry : areas.entrySet()) {
            addArea(chunks, entry.getKey(), entry.getValue(), worldName);
        }
        return chunks;
    }

    private static void addArea(
        final LongSet chunks,
        final String name,
        final KeepLoadedArea area,
        final String worldName
    ) {
        if (area == null || area.size <= 0) {
            return;
        }

        final int size = Math.min(area.size, MAX_SIZE);
        if (size != area.size) {
            LOGGER.warn("keep-loaded \"{}\" size {} on {} is too large, using {}", name, area.size, worldName, MAX_SIZE);
        }

        final int originChunkX = area.x >> 4;
        final int originChunkZ = area.z >> 4;
        final int minChunkX = originChunkX - size / 2;
        final int minChunkZ = originChunkZ - size / 2;
        final int maxChunkX = minChunkX + size - 1;
        final int maxChunkZ = minChunkZ + size - 1;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks.add(ChunkPos.pack(chunkX, chunkZ));
            }
        }
    }

    private static void holdTicket(final ServerChunkCache chunks, final long key) {
        chunks.addTicketAtLevel(TicketType.SHREK_KEEP_LOADED, ChunkPos.unpack(key), ChunkLevel.ENTITY_TICKING_LEVEL);
    }

    private static void dropTicket(final ServerChunkCache chunks, final long key) {
        chunks.removeTicketAtLevel(TicketType.SHREK_KEEP_LOADED, ChunkPos.unpack(key), ChunkLevel.ENTITY_TICKING_LEVEL);
    }

    private KeepLoadedChunks() {
    }
}
