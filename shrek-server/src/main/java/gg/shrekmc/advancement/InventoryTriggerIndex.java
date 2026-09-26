package gg.shrekmc.advancement;

import gg.shrekmc.ShrekServerConfig;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class InventoryTriggerIndex {
    private record Entry(int order, PlayerAdvancements.TriggerInstanceKey key, InventoryChangeTrigger.TriggerInstance instance) {
    }

    private static final Entry[] NONE = new Entry[0];

    private final int version;
    private final Map<PlayerAdvancements.TriggerInstanceKey, InventoryChangeTrigger.TriggerInstance> listeners;
    private final Entry[] generic;
    private final Reference2ObjectOpenHashMap<Item, Entry[]> byItem;

    private InventoryTriggerIndex(
        final int version,
        final Map<PlayerAdvancements.TriggerInstanceKey, InventoryChangeTrigger.TriggerInstance> listeners,
        final Entry[] generic,
        final Reference2ObjectOpenHashMap<Item, Entry[]> byItem
    ) {
        this.version = version;
        this.listeners = listeners;
        this.generic = generic;
        this.byItem = byItem;
    }

    public static boolean trigger(
        final InventoryChangeTrigger trigger,
        final ServerPlayer player,
        final Inventory inventory,
        final ItemStack changedItem,
        final int slotsFull,
        final int slotsEmpty,
        final int slotsOccupied
    ) {
        if (!ShrekServerConfig.advancements().indexInventoryChangeListeners) {
            return false;
        }
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(player, "Cannot trigger criterion async");
        final PlayerAdvancements advancements = player.getAdvancements();
        final Map<PlayerAdvancements.TriggerInstanceKey, InventoryChangeTrigger.TriggerInstance> listeners = advancements.getTriggerMapForType(trigger);
        if (listeners == null || listeners.isEmpty()) {
            return true;
        }
        InventoryTriggerIndex index = advancements.shrek$inventoryTriggerIndex instanceof InventoryTriggerIndex cached ? cached : null;
        if (index == null || index.version != advancements.shrek$listenerVersion || index.listeners != listeners) {
            index = build(advancements.shrek$listenerVersion, listeners);
            advancements.shrek$inventoryTriggerIndex = index;
        }

        final Entry[] forItem = changedItem.isEmpty() ? NONE : index.byItem.getOrDefault(changedItem.getItem(), NONE);
        final Entry[] generic = index.generic;
        LootContext playerContext = null;
        List<PlayerAdvancements.TriggerInstanceKey> matched = null;

        int g = 0;
        int f = 0;
        while (g < generic.length || f < forItem.length) {
            final Entry entry;
            if (f >= forItem.length || (g < generic.length && generic[g].order < forItem[f].order)) {
                entry = generic[g++];
            } else {
                entry = forItem[f++];
            }
            final InventoryChangeTrigger.TriggerInstance instance = entry.instance();
            if (!instance.matches(inventory, changedItem, slotsFull, slotsEmpty, slotsOccupied)) {
                continue;
            }
            final Optional<Holder<LootItemCondition>> predicate = instance.player();
            if (predicate.isPresent()) {
                if (playerContext == null) {
                    playerContext = EntityPredicate.createContext(player, player);
                }
                if (!predicate.get().value().test(playerContext)) {
                    continue;
                }
            }
            if (matched == null) {
                matched = new ArrayList<>();
            }
            matched.add(entry.key());
        }
        if (matched != null) {
            for (final PlayerAdvancements.TriggerInstanceKey key : matched) {
                advancements.award(key.advancement(), key.criterion());
            }
        }
        return true;
    }

    static List<PlayerAdvancements.TriggerInstanceKey> candidates(
        final Map<PlayerAdvancements.TriggerInstanceKey, InventoryChangeTrigger.TriggerInstance> listeners,
        final ItemStack changedItem
    ) {
        final InventoryTriggerIndex index = build(0, listeners);
        final Entry[] forItem = changedItem.isEmpty() ? NONE : index.byItem.getOrDefault(changedItem.getItem(), NONE);
        final List<Entry> merged = new ArrayList<>(List.of(index.generic));
        merged.addAll(List.of(forItem));
        merged.sort(java.util.Comparator.comparingInt(Entry::order));
        return merged.stream().map(Entry::key).toList();
    }

    private static InventoryTriggerIndex build(
        final int version,
        final Map<PlayerAdvancements.TriggerInstanceKey, InventoryChangeTrigger.TriggerInstance> listeners
    ) {
        final List<Entry> generic = new ArrayList<>();
        final Reference2ObjectOpenHashMap<Item, List<Entry>> byItem = new Reference2ObjectOpenHashMap<>();
        int order = 0;
        for (final Map.Entry<PlayerAdvancements.TriggerInstanceKey, InventoryChangeTrigger.TriggerInstance> listener : listeners.entrySet()) {
            final Entry entry = new Entry(order++, listener.getKey(), listener.getValue());
            final @Nullable HolderSet<Item> items = restriction(listener.getValue());
            if (items == null) {
                generic.add(entry);
                continue;
            }
            for (final Holder<Item> item : items) {
                final List<Entry> list = byItem.computeIfAbsent(item.value(), _ -> new ArrayList<>());
                if (list.isEmpty() || list.getLast() != entry) {
                    list.add(entry);
                }
            }
        }
        final Reference2ObjectOpenHashMap<Item, Entry[]> itemArrays = new Reference2ObjectOpenHashMap<>(byItem.size());
        for (final Reference2ObjectOpenHashMap.Entry<Item, List<Entry>> item : byItem.reference2ObjectEntrySet()) {
            itemArrays.put(item.getKey(), item.getValue().toArray(Entry[]::new));
        }
        return new InventoryTriggerIndex(version, listeners, generic.toArray(Entry[]::new), itemArrays);
    }

    private static @Nullable HolderSet<Item> restriction(final InventoryChangeTrigger.TriggerInstance instance) {
        final List<ItemPredicate> items = instance.items();
        if (items.size() != 1) {
            return null;
        }
        return items.getFirst().items().orElse(null);
    }
}
