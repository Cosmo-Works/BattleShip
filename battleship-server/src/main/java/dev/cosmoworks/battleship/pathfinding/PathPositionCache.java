package dev.cosmoworks.battleship.pathfinding;

import it.unimi.dsi.fastutil.HashCommon;

import java.util.Arrays;

public final class PathPositionCache {
    public static final long MISSING = Long.MIN_VALUE;

    private long[] keys;
    private long[] values;
    private int[] stamps;
    private int mask;
    private int size;
    private int generation = 1;

    public PathPositionCache(final int capacity) {
        this.allocate(Integer.highestOneBit(Math.max(16, capacity) - 1) << 1);
    }

    public int size() {
        return this.size;
    }

    public void clear() {
        this.size = 0;
        if (++this.generation == Integer.MAX_VALUE) {
            Arrays.fill(this.stamps, 0);
            this.generation = 1;
        }
    }

    public long get(final long key) {
        int slot = (int) HashCommon.mix(key) & this.mask;
        while (this.stamps[slot] == this.generation) {
            if (this.keys[slot] == key) {
                return this.values[slot];
            }
            slot = (slot + 1) & this.mask;
        }
        return MISSING;
    }

    public void put(final long key, final long value) {
        int slot = (int) HashCommon.mix(key) & this.mask;
        while (this.stamps[slot] == this.generation) {
            if (this.keys[slot] == key) {
                this.values[slot] = value;
                return;
            }
            slot = (slot + 1) & this.mask;
        }
        this.keys[slot] = key;
        this.values[slot] = value;
        this.stamps[slot] = this.generation;
        if (++this.size * 2 > this.keys.length) {
            this.grow();
        }
    }

    private void grow() {
        long[] oldKeys = this.keys;
        long[] oldValues = this.values;
        int[] oldStamps = this.stamps;
        int live = this.generation;
        this.allocate(oldKeys.length << 1);
        this.size = 0;
        for (int index = 0; index < oldKeys.length; index++) {
            if (oldStamps[index] == live) {
                this.put(oldKeys[index], oldValues[index]);
            }
        }
    }

    private void allocate(final int capacity) {
        this.keys = new long[capacity];
        this.values = new long[capacity];
        this.stamps = new int[capacity];
        this.mask = capacity - 1;
        this.generation = 1;
    }
}
