package gg.shrekmc.spawn;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Arrays;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CrowdSelection {
    private final IntOpenHashSet selected = new IntOpenHashSet();

    private final Int2DoubleOpenHashMap candidates = new Int2DoubleOpenHashMap();

    private final Int2LongOpenHashMap offeredAt = new Int2LongOpenHashMap();

    private int[] ids = new int[0];
    private double[] distances = new double[0];
    private long[] kept = new long[0];
    private long[] others = new long[0];

    public void offer(final int id, final double distanceSquared) {
        this.candidates.put(id, distanceSquared);
    }

    public void offer(final int id, final double distanceSquared, final long tick) {
        this.candidates.put(id, distanceSquared);
        this.offeredAt.put(id, tick);
    }

    public boolean isSelected(final int id) {
        return this.selected.contains(id);
    }

    public boolean admit(final int id, final int cap) {
        if (this.selected.size() < cap) {
            this.selected.add(id);
            return true;
        }
        return false;
    }

    public void updateDistance(final int id, final double distanceSquared) {
        if (this.candidates.containsKey(id)) {
            this.candidates.put(id, distanceSquared);
        }
    }

    public int[] recentCandidates(final long oldestTick) {
        final it.unimi.dsi.fastutil.ints.IntArrayList ids = new it.unimi.dsi.fastutil.ints.IntArrayList(this.offeredAt.size());
        for (final var entry : this.offeredAt.int2LongEntrySet()) {
            if (entry.getLongValue() >= oldestTick) {
                ids.add(entry.getIntKey());
            }
        }
        return ids.toIntArray();
    }

    public int forceSelect(final int id, final int cap) {
        if (this.selected.contains(id)) {
            return Integer.MIN_VALUE;
        }
        int dropped = Integer.MIN_VALUE;
        if (this.selected.size() >= cap) {
            double farthest = -1.0;
            for (final int selectedId : this.selected) {
                final double distance = this.candidates.getOrDefault(selectedId, Double.MAX_VALUE);
                if (distance > 0.0 && distance > farthest) {
                    farthest = distance;
                    dropped = selectedId;
                }
            }
            if (dropped == Integer.MIN_VALUE) {
                return Integer.MIN_VALUE;
            }
            this.selected.remove(dropped);
        }
        this.selected.add(id);
        return dropped;
    }

    public boolean isCandidate(final int id) {
        return this.candidates.containsKey(id);
    }

    public void forget(final int id) {
        this.candidates.remove(id);
        this.offeredAt.remove(id);
        this.selected.remove(id);
    }

    public void deselect(final int id) {
        this.selected.remove(id);
    }

    public it.unimi.dsi.fastutil.ints.IntSet selected() {
        return this.selected;
    }

    public int selectedCount() {
        return this.selected.size();
    }

    public int candidateCount() {
        return this.candidates.size();
    }

    public void clearCandidates() {
        this.candidates.clear();
        this.offeredAt.clear();
    }

    public void clear() {
        this.selected.clear();
        this.candidates.clear();
        this.offeredAt.clear();
    }

    public void rebuild(final int cap, final double swapDistance, final long oldestTick) {
        final var iterator = this.offeredAt.int2LongEntrySet().fastIterator();
        while (iterator.hasNext()) {
            final var entry = iterator.next();
            if (entry.getLongValue() < oldestTick) {
                this.candidates.remove(entry.getIntKey());
                iterator.remove();
            }
        }
        this.select(cap, swapDistance);
    }

    public void rebuild(final int cap, final double swapDistance) {
        this.select(cap, swapDistance);
        this.clearCandidates();
    }

    private void select(final int cap, final double swapDistance) {
        final int count = this.candidates.size();
        if (count <= cap) {
            this.selected.clear();
            this.selected.addAll(this.candidates.keySet());
            return;
        }

        this.ensureCapacity(count);
        final int[] ids = this.ids;
        final double[] distances = this.distances;
        final long[] kept = this.kept;
        final long[] others = this.others;
        int keptCount = 0;
        int otherCount = 0;
        int index = 0;
        for (final Int2DoubleMap.Entry entry : this.candidates.int2DoubleEntrySet()) {
            final int id = entry.getIntKey();
            final double distanceSquared = entry.getDoubleValue();
            ids[index] = id;
            distances[index] = distanceSquared;

            final long key = ((long) Float.floatToIntBits((float) distanceSquared) << 32) | index;
            if (this.selected.contains(id)) {
                kept[keptCount++] = key;
            } else {
                others[otherCount++] = key;
            }
            ++index;
        }
        Arrays.sort(kept, 0, keptCount);
        Arrays.sort(others, 0, otherCount);

        final int keep = Math.min(keptCount, cap);
        final int fill = Math.min(cap - keep, otherCount);

        int furthestKept = keep - 1;
        int nearestOther = fill;
        while (furthestKept >= 0 && nearestOther < otherCount) {
            final double keptDistance = Math.sqrt(distances[(int) kept[furthestKept]]);
            final double otherDistance = Math.sqrt(distances[(int) others[nearestOther]]);
            if (otherDistance + swapDistance > keptDistance) {
                break;
            }
            kept[furthestKept--] = others[nearestOther++];
        }

        this.selected.clear();
        for (int i = 0; i < keep; ++i) {
            this.selected.add(ids[(int) kept[i]]);
        }
        for (int i = 0; i < fill; ++i) {
            this.selected.add(ids[(int) others[i]]);
        }
    }

    private void ensureCapacity(final int count) {
        if (this.ids.length >= count) {
            return;
        }
        final int size = Math.max(count, this.ids.length * 2);
        this.ids = new int[size];
        this.distances = new double[size];
        this.kept = new long[size];
        this.others = new long[size];
    }
}
