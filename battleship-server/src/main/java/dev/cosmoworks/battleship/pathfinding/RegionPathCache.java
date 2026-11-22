package dev.cosmoworks.battleship.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.SectionPos;

import java.util.Arrays;

public final class RegionPathCache {
    public static final int TTL_UNITS = 30;
    private static final int TIME_MASK = 0xFFFFFF;
    private static final int MAX_POSITIONS = 1 << 20;
    private static final int MAX_SECTIONS = 1 << 18;
    private static final int MAX_PROFILES = 64;

    public record MobProfile(int width, int height, int depth, boolean canPassDoors, boolean canOpenDoors, float[] malus) {
        @Override
        public boolean equals(final Object other) {
            return other instanceof MobProfile that
                && this.width == that.width && this.height == that.height && this.depth == that.depth
                && this.canPassDoors == that.canPassDoors && this.canOpenDoors == that.canOpenDoors
                && Arrays.equals(this.malus, that.malus);
        }

        @Override
        public int hashCode() {
            int hash = ((this.width * 31 + this.height) * 31 + this.depth) * 4
                + (this.canPassDoors ? 2 : 0) + (this.canOpenDoors ? 1 : 0);
            return hash * 31 + Arrays.hashCode(this.malus);
        }
    }

    private final Long2LongOpenHashMap sectionVersions = new Long2LongOpenHashMap();
    private final PathPositionCache staticTypes = new PathPositionCache(1 << 14);
    private final Object2ObjectOpenHashMap<MobProfile, PathPositionCache> mobTypes = new Object2ObjectOpenHashMap<>();
    private final PathResultCache results = new PathResultCache();

    private long epoch;

    public int beginSearch() {
        this.epoch++;
        if (this.epoch >= Integer.MAX_VALUE || this.sectionVersions.size() > MAX_SECTIONS
            || this.staticTypes.size() > MAX_POSITIONS || this.mobTypes.size() > MAX_PROFILES) {
            this.clear();
        }
        return now();
    }

    public static int now() {
        return (int) (System.nanoTime() >>> 30) & TIME_MASK;
    }

    public int sectionVersion(final long sectionKey) {
        return (int) this.sectionVersions.get(sectionKey);
    }

    public void blockChanged(final int x, final int y, final int z) {
        final long version = this.epoch + 1L;
        for (int sx = (x - 4) >> 4; sx <= (x + 1) >> 4; sx++) {
            for (int sy = (y - 4) >> 4; sy <= (y + 1) >> 4; sy++) {
                for (int sz = (z - 4) >> 4; sz <= (z + 1) >> 4; sz++) {
                    final long key = SectionPos.asLong(sx, sy, sz);

                    if (this.sectionVersions.get(key) != version) {
                        this.sectionVersions.put(key, version);
                    }
                }
            }
        }
    }

    public PathPositionCache staticTypes() {
        return this.staticTypes;
    }

    public PathPositionCache mobTypes(final MobProfile profile) {
        PathPositionCache cache = this.mobTypes.get(profile);
        if (cache == null) {
            cache = new PathPositionCache(1 << 12);
            this.mobTypes.put(profile, cache);
        }
        return cache;
    }

    public void clear() {
        this.epoch = 0L;
        this.sectionVersions.clear();
        this.staticTypes.clear();
        this.mobTypes.clear();
        this.results.clear();
    }

    public PathResultCache results() {
        return this.results;
    }

    public static boolean freshTime(final int time, final int now) {
        return ((now - time) & TIME_MASK) <= TTL_UNITS;
    }

    public static long pack(final int ordinal, final int version, final int time) {
        return ((long) version << 32) | ((long) (time & TIME_MASK) << 8) | (ordinal & 0xFF);
    }

    public static int ordinal(final long entry) {
        return (int) (entry & 0xFF);
    }

    public static boolean fresh(final long entry, final int version, final int now) {
        return (int) (entry >>> 32) == version && ((now - (int) ((entry >>> 8) & TIME_MASK)) & TIME_MASK) <= TTL_UNITS;
    }
}
