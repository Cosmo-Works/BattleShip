package me.samsuik.sakura.utils;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.Arrays;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class SimpleHashTable<K, V> {
    private final @Nullable V[] table;
    private final int mask;

    @SuppressWarnings("unchecked")
    public SimpleHashTable(final int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("Table size cannot be negative");
        }

        final int size = HashCommon.nextPowerOfTwo(expectedSize - 1);
        this.table = (V[]) new Object[size];
        this.mask = size - 1;
    }

    public @Nullable V get(final K key) {
        return this.table[this.index(key)];
    }

    public @Nullable V put(final K key, final @Nullable V value) {
        final int index = this.index(key);
        final V present = this.table[index];
        this.table[index] = value;
        return present;
    }

    public @Nullable V remove(final K key) {
        return this.put(key, null);
    }

    public void clear() {
        Arrays.fill(this.table, null);
    }

    private int index(final K key) {
        return key.hashCode() & this.mask;
    }
}
