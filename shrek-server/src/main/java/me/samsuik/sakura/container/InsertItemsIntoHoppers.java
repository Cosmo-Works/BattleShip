// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.container;

import ca.spottedleaf.moonrise.common.util.TickThread;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class InsertItemsIntoHoppers {
    public static void onMobDeath(final ServerLevel level, final Entity entity, final List<Entity.DefaultDrop> drops) {
        if (!hopperOwnedByThisRegion(level, entity)) {
            return;
        }

        final BlockEntity blockEntity = level.getBlockEntity(entity.getBlockPosBelowThatAffectsMyMovement());
        if (!(blockEntity instanceof final HopperBlockEntity hopper)) {
            return;
        }

        insertDrops(level, entity, hopper, drops);
    }

    private static boolean hopperOwnedByThisRegion(final ServerLevel level, final Entity entity) {
        if (entity.level() != level) {
            return false;
        }

        final BlockPos hopperPos = entity.getBlockPosBelowThatAffectsMyMovement();
        return TickThread.isTickThreadFor(level, hopperPos.getX() >> 4, hopperPos.getZ() >> 4);
    }

    private static void insertDrops(
        final ServerLevel level,
        final Entity entity,
        final HopperBlockEntity hopper,
        final List<Entity.DefaultDrop> drops
    ) {
        for (int index = 0; index < drops.size(); index++) {
            final Entity.DefaultDrop drop = drops.get(index);
            if (drop == null) {
                continue;
            }

            final ItemStack stack = drop.stack();
            if (stack.isEmpty()) {
                continue;
            }

            final ItemEntity itemEntity = new ItemEntity(
                level,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                CraftItemStack.asNMSCopy(stack)
            );
            if (HopperBlockEntity.addItem(hopper, itemEntity)) {
                drops.remove(index--);
            } else {
                drops.set(
                    index,
                    new Entity.DefaultDrop(
                        itemEntity.getItem().getItem(),
                        CraftItemStack.asCraftMirror(itemEntity.getItem()),
                        drop.dropConsumer()
                    )
                );
            }
        }
    }

    private InsertItemsIntoHoppers() {
    }
}
