// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.player.visibility;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.List;
import java.util.Locale;
import me.samsuik.sakura.player.gui.ItemStackUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class VisibilityGuiItems {
    static final Reference2ObjectMap<VisibilityType, ObjectImmutableList<ItemStack>> GUI_ITEMS = new Reference2ObjectOpenHashMap<>();
    static final Reference2ObjectMap<VisibilityState, ItemStack> TOGGLE_BUTTON_ITEMS = new Reference2ObjectOpenHashMap<>();
    static final List<VisibilityType> BUTTON_ORDER = new ObjectArrayList<>();

    static {
        final Reference2ObjectMap<VisibilityType, ItemStack> items = new Reference2ObjectOpenHashMap<>();
        items.put(VisibilityTypes.TNT, ItemStackUtil.itemWithName(Material.TNT, Component.text("Tnt", NamedTextColor.RED)));
        items.put(VisibilityTypes.WIND_CHARGE, ItemStackUtil.itemWithName(Material.WIND_CHARGE, Component.text("Wind Charge", NamedTextColor.GRAY)));
        items.put(VisibilityTypes.SAND, ItemStackUtil.itemWithName(Material.SAND, Component.text("Sand", NamedTextColor.YELLOW)));
        items.put(VisibilityTypes.SULFUR_CUBE, ItemStackUtil.itemWithName(Material.SULFUR_BRICKS, Component.text("Sulfur Cube", NamedTextColor.YELLOW)));
        items.put(VisibilityTypes.EXPLOSIONS, ItemStackUtil.itemWithName(Material.COBWEB, Component.text("Explosions", NamedTextColor.WHITE)));
        items.put(VisibilityTypes.SPAWNERS, ItemStackUtil.itemWithName(Material.SPAWNER, Component.text("Spawners", NamedTextColor.DARK_GRAY)));
        items.put(VisibilityTypes.PISTONS, ItemStackUtil.itemWithName(Material.PISTON, Component.text("Pistons", NamedTextColor.GOLD)));

        BUTTON_ORDER.addAll(List.of(
            VisibilityTypes.TNT,
            VisibilityTypes.WIND_CHARGE,
            VisibilityTypes.SAND,
            VisibilityTypes.SULFUR_CUBE,
            VisibilityTypes.PISTONS,
            VisibilityTypes.EXPLOSIONS,
            VisibilityTypes.SPAWNERS
        ));

        for (final VisibilityType type : VisibilityTypes.types()) {
            final ItemStack item = items.get(type);
            final ObjectImmutableList<ItemStack> stateItems = type.states().stream()
                .map(state -> createItemForState(item, state))
                .collect(ObjectImmutableList.toList());
            GUI_ITEMS.put(type, stateItems);
        }

        TOGGLE_BUTTON_ITEMS.put(VisibilityState.ON, ItemStackUtil.itemWithName(Material.GREEN_STAINED_GLASS_PANE, Component.text("Enabled", NamedTextColor.GREEN)));
        TOGGLE_BUTTON_ITEMS.put(VisibilityState.MODIFIED, ItemStackUtil.itemWithName(Material.MAGENTA_STAINED_GLASS_PANE, Component.text("Modified", NamedTextColor.LIGHT_PURPLE)));
        TOGGLE_BUTTON_ITEMS.put(VisibilityState.OFF, ItemStackUtil.itemWithName(Material.RED_STAINED_GLASS_PANE, Component.text("Disabled", NamedTextColor.RED)));
    }

    private static String lowerCaseThenCapitalise(final String name) {
        final String lowerCaseName = name.toLowerCase(Locale.ENGLISH);
        return StringUtils.capitalize(lowerCaseName);
    }

    private static ItemStack createItemForState(final ItemStack in, final VisibilityState state) {
        final String capitalisedName = lowerCaseThenCapitalise(state.name());
        final Component component = Component.text(" | " + capitalisedName, NamedTextColor.GRAY);
        final ItemStack itemCopy = in.clone();
        itemCopy.editMeta(meta -> meta.itemName(meta.itemName().append(component)));
        return itemCopy;
    }
}
