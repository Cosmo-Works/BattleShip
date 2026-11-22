package dev.cosmoworks.battleship.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

@AllFeatures
class InventoryTicksTest {
    @Test
    void onlyItemsOverridingTheHookTick() {
        assertTrue(Items.COMPASS.battleship$inventoryTicks);
        assertTrue(Items.FILLED_MAP.battleship$inventoryTicks);
        assertFalse(Items.DIAMOND.battleship$inventoryTicks);
        assertFalse(Items.STONE.battleship$inventoryTicks);
        assertFalse(Items.DIAMOND_SWORD.battleship$inventoryTicks);
        for (final Item item : BuiltInRegistries.ITEM) {
            final boolean overrides = item instanceof CompassItem || item instanceof MapItem;
            if (overrides != item.battleship$inventoryTicks) {
                assertTrue(InventoryTicks.overridesInventoryTick(item.getClass()) == item.battleship$inventoryTicks, item.toString());
            }
        }
    }
}
