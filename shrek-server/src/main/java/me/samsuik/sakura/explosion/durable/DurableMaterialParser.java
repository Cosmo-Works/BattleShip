// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.explosion.durable;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@NullMarked
public final class DurableMaterialParser {
    private DurableMaterialParser() {
    }

    public static Map<Block, DurableMaterial> parse(
        final Map<String, DurableMaterialSpec> entries,
        final Logger logger,
        final String worldName
    ) {
        final Map<Block, DurableMaterial> materials = new Reference2ObjectOpenHashMap<>();
        for (final Map.Entry<String, DurableMaterialSpec> entry : entries.entrySet()) {
            final Block block = findBlock(entry.getKey());
            if (block == null) {
                logger.warn("Unknown durable-material block \"{}\" for level {}", entry.getKey(), worldName);
                continue;
            }
            if (entry.getValue() == null) {
                logger.warn("Missing durable-material values for \"{}\" on level {}", entry.getKey(), worldName);
                continue;
            }
            materials.put(block, entry.getValue().toMaterial());
        }
        return materials;
    }

    public static long expirationSeconds(final String raw, final Logger logger, final String worldName) {
        final String trimmed = raw.trim().toLowerCase(Locale.ENGLISH);
        if (trimmed.isEmpty()) {
            return 60L;
        }

        try {
            return switch (trimmed.charAt(trimmed.length() - 1)) {
                case 's' -> Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
                case 'm' -> Long.parseLong(trimmed.substring(0, trimmed.length() - 1)) * 60L;
                case 'h' -> Long.parseLong(trimmed.substring(0, trimmed.length() - 1)) * 3600L;
                case 'd' -> Long.parseLong(trimmed.substring(0, trimmed.length() - 1)) * 86400L;
                default -> Long.parseLong(trimmed);
            };
        } catch (final NumberFormatException ignored) {
            logger.warn("Invalid durable-materials-expiration \"{}\" for level {}, falling back to 1m", raw, worldName);
            return 60L;
        }
    }

    public static int protectAboveY(final String raw, final Logger logger, final String worldName) {
        final String trimmed = raw.trim().toLowerCase(Locale.ENGLISH);
        if (trimmed.isEmpty() || trimmed.equals("disabled") || trimmed.equals("off") || trimmed.equals("none") || trimmed.equals("-1")) {
            return Integer.MIN_VALUE;
        }

        try {
            return Integer.parseInt(trimmed);
        } catch (final NumberFormatException ignored) {
            logger.warn("Invalid protect-blocks-above-y \"{}\" for level {}, falling back to disabled", raw, worldName);
            return Integer.MIN_VALUE;
        }
    }

    private static @Nullable Block findBlock(final String token) {
        final Identifier identifier = Identifier.tryParse(token.toLowerCase(Locale.ENGLISH));
        if (identifier == null || !BuiltInRegistries.BLOCK.containsKey(identifier)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getValue(identifier);
    }
}
