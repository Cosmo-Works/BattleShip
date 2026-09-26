package gg.shrekmc.join;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class SpawnPositionPool {
    private record Entry(Vec3 position, long createdNanos) {
    }

    private static final class Slot {
        final ArrayDeque<Entry> entries = new ArrayDeque<>();
        boolean refilling;
    }

    private final Map<Object, Slot> slots = new ConcurrentHashMap<>();

    private Slot slot(final Object key) {
        return this.slots.computeIfAbsent(key, k -> new Slot());
    }

    private static void expire(final Slot slot, final long nowNanos, final long maxAgeNanos) {
        final Iterator<Entry> it = slot.entries.iterator();
        while (it.hasNext()) {
            if (nowNanos - it.next().createdNanos() > maxAgeNanos) {
                it.remove();
            }
        }
    }

    public @Nullable Vec3 take(final Object key, final long nowNanos, final long maxAgeNanos, final int size,
                               final IntUnaryOperator random, final Runnable refill) {
        final Slot slot = this.slot(key);
        final Vec3 ret;
        boolean startRefill = false;
        synchronized (slot) {
            expire(slot, nowNanos, maxAgeNanos);
            final int count = slot.entries.size();
            if (count == 0) {
                ret = null;
            } else {
                final int index = count == 1 ? 0 : random.applyAsInt(count);
                int i = 0;
                Vec3 found = null;
                for (final Entry entry : slot.entries) {
                    if (i++ == index) {
                        found = entry.position();
                        break;
                    }
                }
                ret = found;

                if (count < size && !slot.refilling) {
                    slot.refilling = true;
                    startRefill = true;
                }
            }
        }
        if (startRefill) {
            try {
                refill.run();
            } catch (final Throwable thrown) {
                this.refillFailed(key);
                throw thrown;
            }
        }
        return ret;
    }

    public void offer(final Object key, final Vec3 position, final long nowNanos, final int size) {
        final Slot slot = this.slot(key);
        synchronized (slot) {
            add(slot, position, nowNanos, size);
        }
    }

    public void refilled(final Object key, final Vec3 position, final long nowNanos, final int size) {
        final Slot slot = this.slot(key);
        synchronized (slot) {
            slot.refilling = false;
            add(slot, position, nowNanos, size);
        }
    }

    public void refillFailed(final Object key) {
        final Slot slot = this.slot(key);
        synchronized (slot) {
            slot.refilling = false;
        }
    }

    private static void add(final Slot slot, final Vec3 position, final long nowNanos, final int size) {
        slot.entries.addLast(new Entry(position, nowNanos));
        while (slot.entries.size() > Math.max(1, size)) {
            slot.entries.removeFirst();
        }
    }

    public void clear() {
        this.slots.clear();
    }

    public List<Vec3> positions(final Object key) {
        final Slot slot = this.slot(key);
        synchronized (slot) {
            final List<Vec3> ret = new ArrayList<>(slot.entries.size());
            for (final Entry entry : slot.entries) {
                ret.add(entry.position());
            }
            return ret;
        }
    }
}
