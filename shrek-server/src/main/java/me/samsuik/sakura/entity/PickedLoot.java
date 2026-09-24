// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.entity;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PickedLoot {
    public static void afterPickup(final Mob mob, final EquipmentSlot slot) {
        remember(mob, slot);
        if (!stayDespawnable(mob)) {
            mob.persistenceRequired = true;
        }
    }

    public static void dropOnDespawn(final Mob mob) {
        if (mob.pickedUpEquipment == 0 || !ownedByThisRegion(mob)) {
            return;
        }
        if (!(mob.level() instanceof final ServerLevel level)) {
            return;
        }

        for (final EquipmentSlot slot : EquipmentSlot.VALUES) {
            if ((mob.pickedUpEquipment & slotBit(slot)) == 0) {
                continue;
            }

            final ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            mob.spawnAtLocation(level, stack);
            mob.setItemSlot(slot, ItemStack.EMPTY);
        }
        mob.pickedUpEquipment = 0;
    }

    private static boolean stayDespawnable(final Mob mob) {
        return mob.level().shrekConfig().entities.despawnAfterPickingUpItems && !mob.hasCustomName();
    }

    private static void remember(final Mob mob, final EquipmentSlot slot) {
        mob.pickedUpEquipment |= slotBit(slot);
    }

    private static boolean ownedByThisRegion(final Mob mob) {
        return TickThread.isTickThreadFor(mob);
    }

    private static int slotBit(final EquipmentSlot slot) {
        return 1 << slot.ordinal();
    }

    private PickedLoot() {
    }
}
