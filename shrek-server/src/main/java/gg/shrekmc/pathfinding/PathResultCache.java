package gg.shrekmc.pathfinding;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Arrays;

public final class PathResultCache {
    private static final int MAX_ENTRIES = 4096;

    public static final int MAX_SECTIONS = 512;

    public record Key(
        Object entityType,
        float bbWidth,
        float bbHeight,
        RegionPathCache.MobProfile profile,
        boolean canFloat,
        boolean canWalkOverFences,
        float maxUpStep,
        int maxFallDistance,
        long start,
        long mobBlock,
        long[] targets,
        float maxPathLength,
        int reachRange,
        int maxVisitedNodes
    ) {
        @Override
        public boolean equals(final Object other) {
            return other instanceof Key that
                && this.entityType == that.entityType
                && Float.compare(this.bbWidth, that.bbWidth) == 0 && Float.compare(this.bbHeight, that.bbHeight) == 0
                && this.canFloat == that.canFloat && this.canWalkOverFences == that.canWalkOverFences
                && Float.compare(this.maxUpStep, that.maxUpStep) == 0 && this.maxFallDistance == that.maxFallDistance
                && this.start == that.start && this.mobBlock == that.mobBlock
                && Float.compare(this.maxPathLength, that.maxPathLength) == 0
                && this.reachRange == that.reachRange && this.maxVisitedNodes == that.maxVisitedNodes
                && Arrays.equals(this.targets, that.targets)
                && this.profile.equals(that.profile);
        }

        @Override
        public int hashCode() {
            int hash = Long.hashCode(this.start) * 31 + Long.hashCode(this.mobBlock);
            hash = hash * 31 + Arrays.hashCode(this.targets);
            hash = hash * 31 + this.maxVisitedNodes;
            return hash * 31 + System.identityHashCode(this.entityType);
        }
    }

    public record Entry(
        int time,
        long[] sections,
        int[] versions,
        int minChunkX,
        int minChunkZ,
        int maxChunkX,
        int maxChunkZ,
        boolean positionSensitive,
        double x,
        double y,
        double z,
        Object mainHand,
        Object feet,
        int[] nodes,
        float[] costs,
        long target,
        boolean reached
    ) {}

    private final Object2ObjectOpenHashMap<Key, Entry> entries = new Object2ObjectOpenHashMap<>();
    private long lookups;
    private long hits;
    private long stores;

    public Entry get(final Key key) {
        this.lookups++;
        return this.entries.get(key);
    }

    public long lookups() {
        return this.lookups;
    }

    public void hit() {
        this.hits++;
    }

    public void put(final Key key, final Entry entry) {
        if (this.entries.size() >= MAX_ENTRIES) {
            this.entries.clear();
        }
        this.entries.put(key, entry);
        this.stores++;
    }

    public void remove(final Key key) {
        this.entries.remove(key);
    }

    public void clear() {
        this.entries.clear();
    }

    public long hits() {
        return this.hits;
    }

    public long stores() {
        return this.stores;
    }
}
