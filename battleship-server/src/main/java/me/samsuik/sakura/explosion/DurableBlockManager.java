// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.explosion;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import me.samsuik.sakura.explosion.durable.DurableMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DurableBlockManager {
    private static final int MAX_DURABLE_BLOCKS = 65535;
    private static final long DEFAULT_EXPIRATION_SECONDS = 60L;

    private final Level level;
    private Cache<BlockPos, DurableBlock> durableBlocks;
    private long expirationSeconds = DEFAULT_EXPIRATION_SECONDS;

    public DurableBlockManager(final Level level) {
        this.level = level;
        this.rebuildCache(expirationFrom(level));
    }

    public boolean damage(final BlockPos blockPos, final DurableMaterial material) {
        this.syncExpiration();
        final BlockPos key = blockPos.immutable();
        DurableBlock block = this.durableBlocks.getIfPresent(key);
        if (block == null) {
            this.durableBlocks.put(key, block = new DurableBlock(material.durability()));
        }

        final boolean destroyedBlock = block.damage();
        if (destroyedBlock) {
            this.durableBlocks.invalidate(key);
        }

        return destroyedBlock;
    }

    public int durability(final BlockPos pos, final DurableMaterial material) {
        this.syncExpiration();
        final DurableBlock block = this.durableBlocks.getIfPresent(pos.immutable());
        return block != null ? block.durability() : material.durability();
    }

    public void mergeFrom(final DurableBlockManager from) {
        for (final Map.Entry<BlockPos, DurableBlock> entry : List.copyOf(from.durableBlocks.asMap().entrySet())) {
            this.durableBlocks.put(entry.getKey(), entry.getValue());
        }
        from.durableBlocks.invalidateAll();
    }

    public void splitInto(
        final int chunkToRegionShift,
        final Long2ReferenceOpenHashMap<RegionizedWorldData> regionToData
    ) {
        for (final Map.Entry<BlockPos, DurableBlock> entry : List.copyOf(this.durableBlocks.asMap().entrySet())) {
            final BlockPos pos = entry.getKey();
            final RegionizedWorldData into = regionToData.get(CoordinateUtils.getChunkKey(
                (pos.getX() >> 4) >> chunkToRegionShift,
                (pos.getZ() >> 4) >> chunkToRegionShift
            ));
            if (into != null) {
                into.battleshipDurableBlockManager.durableBlocks.put(pos, entry.getValue());
            }
        }
        this.durableBlocks.invalidateAll();
    }

    private void syncExpiration() {
        final long seconds = expirationFrom(this.level);
        if (seconds != this.expirationSeconds) {
            this.rebuildCache(seconds);
        }
    }

    private void rebuildCache(final long seconds) {
        this.expirationSeconds = Math.max(1L, seconds);
        this.durableBlocks = CacheBuilder.newBuilder()
            .expireAfterAccess(this.expirationSeconds, TimeUnit.SECONDS)
            .maximumSize(MAX_DURABLE_BLOCKS)
            .build();
    }

    private static long expirationFrom(final Level level) {
        if (level.battleshipConfig() == null) {
            return DEFAULT_EXPIRATION_SECONDS;
        }

        final Long parsed = level.battleshipConfig().cannons.explosion.parsedExpirationSeconds.valueSafe();
        return parsed != null ? parsed : DEFAULT_EXPIRATION_SECONDS;
    }

    private static final class DurableBlock {
        private int durability;

        private DurableBlock(final int durability) {
            this.durability = durability;
        }

        private int durability() {
            return this.durability;
        }

        private boolean damage() {
            return --this.durability <= 0;
        }
    }
}
