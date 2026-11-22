package dev.cosmoworks.battleship.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

@AllFeatures
class InventoryTriggerIndexTest {
    @Test
    void candidatesContainEveryMatchInVanillaOrder() {
        final List<Item> items = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).toList();
        final Random random = new Random(3);
        final Map<PlayerAdvancements.TriggerInstanceKey, InventoryChangeTrigger.TriggerInstance> listeners = new HashMap<>();
        for (int i = 0; i < 600; ++i) {
            final InventoryChangeTrigger.TriggerInstance instance = switch (random.nextInt(6)) {
                case 0 -> InventoryChangeTrigger.TriggerInstance.hasItems(items.get(random.nextInt(items.size()))).triggerInstance();
                case 1 -> InventoryChangeTrigger.TriggerInstance.hasItems(items.get(random.nextInt(items.size())), items.get(random.nextInt(items.size()))).triggerInstance();
                case 2 -> InventoryChangeTrigger.TriggerInstance.hasItems(ItemPredicate.Builder.item().of(BuiltInRegistries.ITEM, ItemTags.LOGS)).triggerInstance();
                case 3 -> InventoryChangeTrigger.TriggerInstance.hasItems(ItemPredicate.Builder.item()).triggerInstance();
                case 4 -> InventoryChangeTrigger.TriggerInstance.hasItems(new ItemPredicate[0]).triggerInstance();
                default -> InventoryChangeTrigger.TriggerInstance.hasItems(ItemPredicate.Builder.item().of(BuiltInRegistries.ITEM, items.get(random.nextInt(items.size())), items.get(random.nextInt(items.size())))).triggerInstance();
            };
            listeners.put(new PlayerAdvancements.TriggerInstanceKey(null, "c" + i), instance);
        }

        int matchedTotal = 0;
        for (int round = 0; round < 3000; ++round) {
            final ItemStack changed = random.nextInt(4) == 0 ? ItemStack.EMPTY : new ItemStack(items.get(random.nextInt(items.size())));
            final List<PlayerAdvancements.TriggerInstanceKey> candidates = InventoryTriggerIndex.candidates(listeners, changed);

            final List<PlayerAdvancements.TriggerInstanceKey> vanillaMatches = new ArrayList<>();
            final List<PlayerAdvancements.TriggerInstanceKey> order = new ArrayList<>(listeners.keySet());
            for (final Map.Entry<PlayerAdvancements.TriggerInstanceKey, InventoryChangeTrigger.TriggerInstance> entry : listeners.entrySet()) {
                if (entry.getValue().items().size() == 1 && !changed.isEmpty() && entry.getValue().items().getFirst().test(changed)) {
                    vanillaMatches.add(entry.getKey());
                } else if (entry.getValue().items().size() != 1) {
                    vanillaMatches.add(entry.getKey());
                }
            }
            for (final PlayerAdvancements.TriggerInstanceKey key : vanillaMatches) {
                assertTrue(candidates.contains(key), "missing candidate " + key + " for " + changed);
            }
            int last = -1;
            for (final PlayerAdvancements.TriggerInstanceKey key : candidates) {
                final int index = order.indexOf(key);
                assertTrue(index > last, "candidates out of vanilla order");
                last = index;
            }
            matchedTotal += vanillaMatches.size();
        }
        assertTrue(matchedTotal > 0);
        assertEquals(listeners.size(), InventoryTriggerIndex.candidates(listeners, new ItemStack(Items.OAK_LOG)).size()
            + (int) listeners.values().stream().filter(v -> v.items().size() == 1 && v.items().getFirst().items().isPresent()
                && !v.items().getFirst().test(new ItemStack(Items.OAK_LOG))).count());
    }
}
