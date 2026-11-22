// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.utils.collections;

import java.util.Arrays;
import java.util.List;
import me.samsuik.sakura.tracking.ChangeListener;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class SlotCountingItemList extends NonNullList<ItemStack> {
    public static SlotCountingItemList withSize(final int size, final ItemStack defaultItem) {
        final ItemStack[] items = new ItemStack[size];
        Arrays.fill(items, defaultItem);
        return new SlotCountingItemList(Arrays.asList(items), defaultItem);
    }

    public static SlotCountingItemList copy(final NonNullList<ItemStack> items) {
        final SlotCountingItemList copied = withSize(items.size(), ItemStack.EMPTY);
        for (int index = 0; index < items.size(); ++index) {
            copied.set(index, items.get(index));
        }
        return copied;
    }

    private final @Nullable ItemWatcher[] watchers;
    private int emptySlots;
    private int fullSlots;

    private SlotCountingItemList(final List<ItemStack> items, final @Nullable ItemStack defaultItem) {
        super(items, defaultItem);
        this.watchers = new ItemWatcher[items.size()];
        this.recalculate();
    }

    public boolean hasSpaceAvailable() {
        return this.fullSlots != this.size();
    }

    public boolean isFull() {
        return this.fullSlots == this.size();
    }

    @Override
    public boolean isEmpty() {
        return this.emptySlots == this.size();
    }

    @Override
    public ItemStack set(final int index, final ItemStack newItemStack) {
        final ItemStack oldItemStack = super.set(index, newItemStack);
        this.replaceSlot(index, oldItemStack, newItemStack);
        return oldItemStack;
    }

    @Override
    public void clear() {
        super.clear();
        this.recalculate();
    }

    @Override
    public boolean add(final ItemStack itemStack) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ItemStack remove(final int index) {
        throw new UnsupportedOperationException();
    }

    private void recalculate() {
        this.emptySlots = this.size();
        this.fullSlots = 0;
        for (int slot = 0; slot < this.size(); ++slot) {
            this.replaceSlot(slot, ItemStack.EMPTY, this.get(slot));
        }
    }

    private void replaceSlot(final int index, final ItemStack oldItemStack, final ItemStack newItemStack) {
        final ItemWatcher watcher = this.watchers[index];
        if (watcher != null) {
            oldItemStack.unwatch(watcher);
            this.watchers[index] = null;
        }

        if (!newItemStack.isEmpty()) {
            this.watchers[index] = new ItemWatcher(newItemStack);
        }

        this.updateCounts(oldItemStack.isEmpty(), isFullStack(oldItemStack), newItemStack);
    }

    private void updateCounts(final boolean wasEmpty, final boolean wasFull, final ItemStack itemStack) {
        if (wasEmpty != itemStack.isEmpty()) {
            this.emptySlots += wasEmpty ? -1 : 1;
        }
        if (wasFull != isFullStack(itemStack)) {
            this.fullSlots += wasFull ? -1 : 1;
        }
    }

    private static boolean isFullStack(final ItemStack itemStack) {
        return !itemStack.isEmpty() && itemStack.getCount() == itemStack.getMaxStackSize();
    }

    private final class ItemWatcher implements ChangeListener<ItemStack> {
        private final ItemStack itemStack;
        private boolean empty;
        private boolean full;

        private ItemWatcher(final ItemStack itemStack) {
            this.itemStack = itemStack;
            this.remember(itemStack);
            itemStack.watch(this);
        }

        @Override
        public void changed(final ItemStack itemStack) {
            SlotCountingItemList.this.updateCounts(this.empty, this.full, this.itemStack);
            this.remember(itemStack);
        }

        private void remember(final ItemStack itemStack) {
            this.empty = itemStack.isEmpty();
            this.full = isFullStack(itemStack);
        }
    }
}
