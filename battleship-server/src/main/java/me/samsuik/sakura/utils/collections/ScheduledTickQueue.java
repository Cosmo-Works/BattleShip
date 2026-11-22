// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.utils.collections;

import it.unimi.dsi.fastutil.objects.ObjectArrays;
import net.minecraft.world.ticks.ScheduledTick;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

@SuppressWarnings({"unchecked", "DataFlowIssue"})
@NullMarked
public final class ScheduledTickQueue<T extends ScheduledTick<?>> extends AbstractQueue<T> {
    private static final ScheduledTick<?>[] EMPTY_ARRAY = new ScheduledTick[0];

    private @Nullable T[] elements = (T[]) EMPTY_ARRAY;
    private int head;
    private int tail;
    private int size;
    private int mask;

    private boolean sorted = true;

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public boolean offer(final T element) {
        if (this.size == this.elements.length) this.grow();
        this.maintainOrder(element);

        this.elements[this.tail] = element;
        this.tail = (this.tail + 1) & this.mask;
        this.size++;
        return true;
    }

    @Override
    public @Nullable T poll() {
        if (this.size == 0) return null;
        if (!this.sorted) this.sort();

        final T result = this.elements[this.head];
        this.elements[this.head] = null;
        this.head = (this.head + 1) & this.mask;
        this.size--;
        return result;
    }

    @Override
    public @Nullable T peek() {
        if (this.size == 0) return null;
        if (!this.sorted) this.sort();

        return this.elements[this.head];
    }

    private void resizeCompact(final int newSize) {
        final @Nullable T[] elements = this.elements;
        final T[] newElements = (T[]) new ScheduledTick[newSize];
        if (this.head < this.tail) {
            System.arraycopy(elements, this.head, newElements, 0, this.size);
        } else {
            final int trailing = elements.length - this.head;
            System.arraycopy(elements, this.head, newElements, 0, trailing);
            System.arraycopy(elements, 0, newElements, trailing, this.tail);
        }

        this.elements = newElements;
        this.head = 0;
        this.mask = newElements.length - 1;
        this.tail = this.size & this.mask;
    }

    private void grow() {
        this.resizeCompact(Math.max(8, this.elements.length << 1));
    }

    private void sort() {
        if (this.tail < this.head || this.head == this.tail) {
            // compact before sorting
            this.resizeCompact(this.elements.length);
        }

        final int sortUpTo = this.head + this.size;
        ObjectArrays.quickSort(this.elements, this.head, sortUpTo, ScheduledTick.DRAIN_ORDER);
        this.sorted = true;
    }

    private void maintainOrder(final T newElement) {
        if (!this.sorted) {
            return;
        }

        final T lastElement = this.elements[(this.tail - 1) & this.mask];
        if (lastElement != null && ScheduledTick.DRAIN_ORDER.compare(lastElement, newElement) > 0) {
            this.sorted = false;
        }
    }

    // Battleship start - upstream's in-place removal skipped elements and returned null after a remove.
    // The iterator works on a compacted array, so removing only shifts the elements behind it
    private void removeAt(final int index) {
        final int last = this.size - 1;
        System.arraycopy(this.elements, index + 1, this.elements, index, last - index);
        this.elements[last] = null;
        this.size = last;
        this.tail = last & this.mask;
    }

    @Override
    public Iterator<T> iterator() {
        if (!this.sorted) this.sort();
        if (this.head != 0) this.resizeCompact(this.elements.length);
        return new Itr();
    }

    private final class Itr implements Iterator<T> {
        private int cursor = 0;
        private int lastRet = -1;

        @Override
        public boolean hasNext() {
            return this.cursor < ScheduledTickQueue.this.size;
        }

        @Override
        public T next() {
            if (!this.hasNext()) throw new NoSuchElementException();

            this.lastRet = this.cursor;
            return ScheduledTickQueue.this.elements[this.cursor++];
        }

        @Override
        public void remove() {
            if (this.lastRet == -1) throw new IllegalStateException();
            ScheduledTickQueue.this.removeAt(this.lastRet);

            this.cursor = this.lastRet;
            this.lastRet = -1;
        }
    }
    // Battleship end
}
