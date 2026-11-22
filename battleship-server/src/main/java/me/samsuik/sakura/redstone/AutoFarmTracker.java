// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.redstone;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

/**
 * Tracks the chunk sections that had recent stem growth. One tracker per region, the tracked
 * ticks follow the game time of that region.
 */
@NullMarked
public final class AutoFarmTracker {
    private static final int ACTIVE_FARM_TICKS = 60 * 5 * 20; // 5 minutes
    private static final long NOT_TRACKED = Long.MIN_VALUE;

    private final Long2LongOpenHashMap activeSections = new Long2LongOpenHashMap();
    private final Level level;
    private long lastCleanUp;

    public AutoFarmTracker(final Level level) {
        this.level = level;
        // A new region starts at tick zero, untracked sections must not look like recent growth
        this.activeSections.defaultReturnValue(NOT_TRACKED);
    }

    public void newGrowth(final BlockPos pos) {
        final long gameTime = this.level.getGameTime();
        if (gameTime - this.lastCleanUp > ACTIVE_FARM_TICKS) {
            this.activeSections.values().removeIf(lastGrowthTick -> gameTime - lastGrowthTick >= ACTIVE_FARM_TICKS);
            this.lastCleanUp = gameTime;
        }

        final long sectionPos = SectionPos.asLong(pos);
        this.activeSections.put(sectionPos, gameTime);
    }

    public boolean isTrackedAutoFarm(final BlockPos pos) {
        if (!this.level.sakuraConfig().technical.optimiseAutoFarms) {
            return false;
        }

        final long sectionPos = SectionPos.asLong(pos);
        final long lastGrowthTick = this.activeSections.get(sectionPos);
        return lastGrowthTick != NOT_TRACKED && this.level.getGameTime() - lastGrowthTick < ACTIVE_FARM_TICKS;
    }

    public void mergeFrom(final AutoFarmTracker from, final long fromTickOffset) {
        for (final Long2LongMap.Entry entry : from.activeSections.long2LongEntrySet()) {
            this.activeSections.put(entry.getLongKey(), entry.getLongValue() + fromTickOffset);
        }
        from.activeSections.clear();
    }

    public void splitInto(
        final int chunkToRegionShift,
        final Long2ReferenceOpenHashMap<RegionizedWorldData> regionToData
    ) {
        for (final Long2LongMap.Entry entry : this.activeSections.long2LongEntrySet()) {
            final long sectionPos = entry.getLongKey();
            final RegionizedWorldData into = regionToData.get(CoordinateUtils.getChunkKey(
                SectionPos.x(sectionPos) >> chunkToRegionShift,
                SectionPos.z(sectionPos) >> chunkToRegionShift
            ));
            if (into != null) {
                into.battleshipAutoFarmTracker.activeSections.put(sectionPos, entry.getLongValue());
                into.battleshipAutoFarmTracker.lastCleanUp = this.lastCleanUp;
            }
        }
        this.activeSections.clear();
    }
}
