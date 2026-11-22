package dev.cosmoworks.battleship.inventory;

import dev.cosmoworks.battleship.BattleshipServerConfig;
import java.util.Arrays;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class SlotSync {
    private @Nullable ItemStack[] stacks = new ItemStack[0];
    private long[] versions = new long[0];
    private @Nullable ItemStack[] lastSlots = new ItemStack[0];
    private @Nullable RemoteSlot[] remoteSlots = new RemoteSlot[0];
    private int[] remoteVersions = new int[0];
    private int broadcasts;

    public static boolean skipUnchanged(final AbstractContainerMenu menu, final int slotCount) {
        final SlotSync sync = menu.battleship$slotSync;
        if (sync.stacks.length != slotCount) {
            sync.resize(slotCount);
        }
        final BattleshipServerConfig.InventorySync config = BattleshipServerConfig.inventorySync();
        if (!config.skipUnchangedSlots || menu.battleship$remoteUpdatesSuppressed()) {
            return false;
        }
        final int interval = config.fullCheckInterval;
        return interval <= 0 || ++sync.broadcasts % interval != 0;
    }

    private void resize(final int slotCount) {
        this.stacks = new ItemStack[slotCount];
        this.versions = new long[slotCount];
        this.lastSlots = new ItemStack[slotCount];
        this.remoteSlots = new RemoteSlot[slotCount];
        this.remoteVersions = new int[slotCount];
    }

    public boolean unchanged(final int i, final ItemStack current, final ItemStack lastSlot, final RemoteSlot remoteSlot) {
        return this.stacks[i] == current
            && this.versions[i] == current.battleship$version()
            && this.lastSlots[i] == lastSlot
            && this.remoteSlots[i] == remoteSlot
            && this.remoteVersions[i] == remoteSlot.battleship$version();
    }

    public void checked(
        final int i,
        final ItemStack current,
        final long version,
        final ItemStack lastSlot,
        final RemoteSlot remoteSlot,
        final boolean remoteUpdatesSuppressed
    ) {
        if (i >= this.stacks.length) {
            return;
        }
        if (remoteUpdatesSuppressed) {
            this.stacks[i] = null;
            return;
        }
        this.stacks[i] = current;
        this.versions[i] = version;
        this.lastSlots[i] = lastSlot;
        this.remoteSlots[i] = remoteSlot;
        this.remoteVersions[i] = remoteSlot.battleship$version();
    }

    public void reset() {
        Arrays.fill(this.stacks, null);
    }
}
