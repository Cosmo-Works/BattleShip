package dev.cosmoworks.battleship.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cosmoworks.battleship.BattleshipServerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.inventory.InventoryView;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

@AllFeatures
class SlotSyncTest {
    private static final int SLOTS = 12;
    private static final Item[] ITEMS = {Items.DIAMOND_SWORD, Items.STONE, Items.GOLDEN_APPLE, Items.BOW, Items.DIAMOND};
    private static final HashedPatchMap.HashGenerator HASHER = component -> component.value().hashCode();

    private static final class TestMenu extends AbstractContainerMenu {
        final List<String> log = new ArrayList<>();
        boolean skipping;

        int editOnChange = -1;

        TestMenu(final SimpleContainer container) {
            super(null, 0);
            for (int i = 0; i < container.getContainerSize(); ++i) {
                this.addSlot(new Slot(container, i, 0, 0));
            }
            this.setSynchronizer(new ContainerSynchronizer() {
                @Override
                public void sendInitialData(final AbstractContainerMenu menu, final List<ItemStack> items, final ItemStack carried, final int[] data) {
                    TestMenu.this.log.add("initial " + items.stream().map(SlotSyncTest::describe).toList());
                }

                @Override
                public void sendSlotChange(final AbstractContainerMenu menu, final int slot, final ItemStack stack) {
                    TestMenu.this.log.add("slot " + slot + " " + describe(stack));
                }

                @Override
                public void sendCarriedChange(final AbstractContainerMenu menu, final ItemStack stack) {
                    TestMenu.this.log.add("carried " + describe(stack));
                }

                @Override
                public void sendDataChange(final AbstractContainerMenu menu, final int id, final int value) {
                }

                @Override
                public RemoteSlot createSlot() {
                    return new RemoteSlot.Synchronized(HASHER);
                }
            });
            this.addSlotListener(new ContainerListener() {
                @Override
                public void slotChanged(final AbstractContainerMenu menu, final int slot, final ItemStack stack) {
                    TestMenu.this.log.add("listener " + slot + " " + describe(stack));
                    if (slot == TestMenu.this.editOnChange) {
                        TestMenu.this.editOnChange = -1;
                        final ItemStack edited = menu.getSlot(slot).getItem();
                        if (!edited.isEmpty()) {
                            edited.grow(1);
                        }
                    }
                }

                @Override
                public void dataChanged(final AbstractContainerMenu menu, final int id, final int value) {
                }
            });
        }

        void broadcast() {
            BattleshipServerConfig.inventorySync().skipUnchangedSlots = this.skipping;
            this.broadcastChanges();
        }

        @Override
        public InventoryView getBukkitView() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemStack quickMoveStack(final Player player, final int slotIndex) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(final Player player) {
            return true;
        }
    }

    private static String describe(final ItemStack stack) {
        return stack.isEmpty() ? "empty" : stack.getItem() + "x" + stack.getCount() + stack.getComponentsPatch();
    }

    private static ItemStack randomStack(final Random random) {
        if (random.nextInt(5) == 0) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = new ItemStack(ITEMS[random.nextInt(ITEMS.length)], 1 + random.nextInt(3));
        if (random.nextBoolean()) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("n" + random.nextInt(3)));
        }
        return stack;
    }

    private static void change(final long seed, final SimpleContainer container, final TestMenu menu) {
        final Random random = new Random(seed);
        final int slot = random.nextInt(SLOTS);
        final ItemStack stack = container.getItem(slot);
        switch (random.nextInt(11)) {
            case 0, 1 -> container.setItem(slot, randomStack(random));
            case 2 -> {
                if (!stack.isEmpty()) {
                    stack.grow(1);
                }
            }
            case 3 -> {
                if (!stack.isEmpty()) {
                    stack.shrink(1);
                }
            }
            case 4 -> {
                if (!stack.isEmpty()) {
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal("n" + random.nextInt(3)));
                }
            }
            case 5 -> {
                if (!stack.isEmpty()) {
                    stack.remove(DataComponents.CUSTOM_NAME);
                }
            }
            case 6 -> {
                if (!stack.isEmpty()) {
                    stack.set(DataComponents.DAMAGE, random.nextInt(3));
                }
            }
            case 7 -> menu.setRemoteSlotUnsafe(slot, HashedStack.create(randomStack(random), HASHER));
            case 8 -> menu.setRemoteSlot(slot, randomStack(random));
            case 9 -> {
                if (random.nextBoolean()) {
                    menu.suppressRemoteUpdates();
                } else {
                    menu.resumeRemoteUpdates();
                }
            }
            default -> menu.editOnChange = slot;
        }
    }

    private static TestMenu menu(final SimpleContainer container, final boolean skipping) {
        final TestMenu menu = new TestMenu(container);
        menu.skipping = skipping;
        menu.sendAllDataToRemote();
        return menu;
    }

    @Test
    void sendsExactlyWhatVanillaSends() {
        final BattleshipServerConfig.InventorySync config = BattleshipServerConfig.inventorySync();
        final boolean skip = config.skipUnchangedSlots;
        final int interval = config.fullCheckInterval;
        config.fullCheckInterval = 0;
        try {
            final SimpleContainer vanillaContainer = new SimpleContainer(SLOTS);
            final SimpleContainer skippingContainer = new SimpleContainer(SLOTS);
            final Random setup = new Random(1);
            for (int i = 0; i < SLOTS; ++i) {
                final ItemStack stack = randomStack(setup);
                vanillaContainer.setItem(i, stack.copy());
                skippingContainer.setItem(i, stack.copy());
            }
            final TestMenu vanilla = menu(vanillaContainer, false);
            final TestMenu skipping = menu(skippingContainer, true);

            final Random random = new Random(7);
            int updates = 0;
            for (int tick = 0; tick < 20_000; ++tick) {
                final int changes = random.nextInt(4) == 0 ? 1 + random.nextInt(3) : 0;
                for (int i = 0; i < changes; ++i) {
                    final long seed = random.nextLong();
                    change(seed, vanillaContainer, vanilla);
                    change(seed, skippingContainer, skipping);
                }
                vanilla.broadcast();
                skipping.broadcast();
                assertEquals(vanilla.log, skipping.log, "tick " + tick);
                updates += vanilla.log.size();
                vanilla.log.clear();
                skipping.log.clear();
            }
            assertTrue(updates > 1000, "the test should produce updates: " + updates);
        } finally {
            config.skipUnchangedSlots = skip;
            config.fullCheckInterval = interval;
        }
    }

    @Test
    void benchmark() {
        final BattleshipServerConfig.InventorySync config = BattleshipServerConfig.inventorySync();
        final boolean skip = config.skipUnchangedSlots;
        try {
            final Item[] gear = {Items.DIAMOND_SWORD, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.BOW, Items.GOLDEN_APPLE, Items.ENDER_PEARL, Items.COBBLESTONE};
            final double[] nanos = new double[2];
            for (int mode = 0; mode < 2; ++mode) {
                final SimpleContainer container = new SimpleContainer(46);
                for (int i = 0; i < 36; ++i) {
                    final ItemStack stack = new ItemStack(gear[i % gear.length]);
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal("Item " + i));
                    stack.set(DataComponents.DAMAGE, i);
                    container.setItem(i, stack);
                }
                final TestMenu menu = menu(container, mode == 1);
                for (int warmup = 0; warmup < 50_000; ++warmup) {
                    menu.broadcast();
                }
                final int rounds = 200_000;
                final long start = System.nanoTime();
                for (int round = 0; round < rounds; ++round) {
                    menu.broadcast();
                }
                nanos[mode] = (System.nanoTime() - start) / (double) rounds;
                menu.log.clear();
            }
            System.out.printf("[bench] broadcastChanges, 46 slots / 36 named+damaged items: every slot %.0f ns, skip unchanged %.0f ns (%.1fx)%n",
                nanos[0], nanos[1], nanos[0] / nanos[1]);
        } finally {
            config.skipUnchangedSlots = skip;
        }
    }
}
