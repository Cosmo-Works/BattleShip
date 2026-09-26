package gg.shrekmc.inventory;

import gg.shrekmc.ShrekServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class InventoryTicks {
    private InventoryTicks() {
    }

    public static boolean ticks(final Item item) {
        if (!ShrekServerConfig.inventorySync().skipNoOpInventoryTicks) {
            return true;
        }
        return item.shrek$inventoryTicks;
    }

    public static boolean overridesInventoryTick(final Class<?> itemClass) {
        for (Class<?> type = itemClass; type != null && type != Item.class; type = type.getSuperclass()) {
            try {
                type.getDeclaredMethod("inventoryTick", ItemStack.class, ServerLevel.class, Entity.class, EquipmentSlot.class);
                return true;
            } catch (final NoSuchMethodException ignored) {
            }
        }
        return false;
    }
}
