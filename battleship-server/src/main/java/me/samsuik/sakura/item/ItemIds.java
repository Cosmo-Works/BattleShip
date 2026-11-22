// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.item;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@NullMarked
public final class ItemIds {
    public static Set<Item> parse(final List<String> tokens, final Logger logger, final String worldName) {
        final Set<Item> items = new ReferenceOpenHashSet<>();
        for (final String token : tokens) {
            final Item item = findItem(token);
            if (item == null) {
                logger.warn("Unknown item \"{}\" for level {}", token, worldName);
                continue;
            }
            items.add(item);
        }
        return items;
    }

    private static @Nullable Item findItem(final String token) {
        final Identifier identifier = Identifier.tryParse(token.toLowerCase(Locale.ENGLISH));
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
            return null;
        }
        return BuiltInRegistries.ITEM.getValue(identifier);
    }

    private ItemIds() {
    }
}
