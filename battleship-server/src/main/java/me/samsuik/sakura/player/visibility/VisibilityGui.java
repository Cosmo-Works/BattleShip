// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.player.visibility;

import me.samsuik.sakura.configuration.GlobalConfiguration;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import me.samsuik.sakura.player.gui.FeatureGui;
import me.samsuik.sakura.player.gui.FeatureGuiInventory;
import me.samsuik.sakura.player.gui.components.ItemButton;
import me.samsuik.sakura.player.gui.components.ItemList;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jspecify.annotations.NullMarked;

import static me.samsuik.sakura.player.gui.ItemStackUtil.itemWithBlankName;

@NullMarked
public final class VisibilityGui extends FeatureGui {
    private static final NamespacedKey TOGGLE_BUTTON_KEY = new NamespacedKey("sakura", "toggle_button");
    private static final NamespacedKey MENU_ITEMS_KEY = new NamespacedKey("sakura", "menu_items");

    public VisibilityGui() {
        super(45, Component.text("FPS Settings"));
    }

    @Override
    protected void fillInventory(final Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); ++slot) {
            final int x = slot % 9;
            final int y = slot / 9;
            final int rx = x - 4;
            final int ry = y - 2;

            if (Math.sqrt(rx * rx + ry * ry) <= 3.25) {
                inventory.setItem(slot, itemWithBlankName(GlobalConfiguration.get().fps.material));
            } else if (x % 8 == 0) {
                inventory.setItem(slot, itemWithBlankName(Material.BLACK_STAINED_GLASS_PANE));
            } else {
                inventory.setItem(slot, itemWithBlankName(Material.WHITE_STAINED_GLASS_PANE));
            }
        }
    }

    @Override
    protected void afterFill(final Player player, final FeatureGuiInventory inventory) {
        final VisibilitySettings settings = player.getVisibility();
        final IntArrayFIFOQueue slots = this.availableSlots();
        this.updateToggleButton(settings, player, inventory);

        for (final VisibilityType type : VisibilityGuiItems.BUTTON_ORDER) {
            final VisibilityState state = settings.get(type);
            final int index = type.states().indexOf(state);
            final int slot = slots.dequeueInt();

            final ItemList itemList = new ItemList(
                VisibilityGuiItems.GUI_ITEMS.get(type),
                slot, index,
                (_, clickedInventory) -> {
                    settings.cycle(type);
                    this.updateToggleButton(settings, player, clickedInventory);
                }
            );

            inventory.addComponent(itemList, MENU_ITEMS_KEY);
        }
    }

    private void updateToggleButton(final VisibilitySettings settings, final Player player, final FeatureGuiInventory inventory) {
        inventory.removeComponents(TOGGLE_BUTTON_KEY);
        final VisibilityState settingsState = settings.currentState();
        final ItemButton button = new ItemButton(
            VisibilityGuiItems.TOGGLE_BUTTON_ITEMS.get(settingsState),
            (2 * 9) + 8,
            (_, clickedInventory) -> {
                settings.toggleAll();
                inventory.removeAllComponents();
                this.afterFill(player, clickedInventory);
            }
        );

        inventory.addComponent(button, TOGGLE_BUTTON_KEY);
    }

    private IntArrayFIFOQueue availableSlots() {
        final IntArrayFIFOQueue slots = new IntArrayFIFOQueue();
        for (int row = 1; row < 4; ++row) {
            for (int column = 3; column < 6; ++column) {
                if ((row % 2) == 1 || (column + row) % 2 == 0) {
                    slots.enqueue((row * 9) + column);
                }
            }
        }

        return slots;
    }
}
