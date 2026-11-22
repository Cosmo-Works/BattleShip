// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.entity;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@NullMarked
public final class EntityTravelLimits {
    private EntityTravelLimits() {
    }

    public static void discardIfTravelledTooFar(final Entity entity) {
        if (travelledTooFar(entity)) {
            entity.discard(EntityRemoveEvent.Cause.DESPAWN);
        }
    }

    private static boolean travelledTooFar(final Entity entity) {
        if (!sameWorldAsOrigin(entity)) {
            return false;
        }

        final Integer chunks = chunkLimit(entity);
        if (chunks == null || chunks <= 0) {
            return false;
        }

        final Vec3 origin = entity.origin;
        final double travelledX = entity.getX() - origin.x;
        final double travelledZ = entity.getZ() - origin.z;
        final double limit = chunks * 16.0;
        return Math.max(travelledX * travelledX, travelledZ * travelledZ) >= limit * limit;
    }

    private static @Nullable Integer chunkLimit(final Entity entity) {
        final Map<EntityType<?>, Integer> limits = entity.level().battleshipConfig().entities.parsedChunkTravelLimit.valueSafe();
        return limits != null ? limits.get(entity.getType()) : null;
    }

    private static boolean sameWorldAsOrigin(final Entity entity) {
        if (entity.origin == null) {
            return false;
        }
        if (entity.originWorld == null) {
            return true;
        }
        return entity.originWorld.equals(entity.level().getWorld().getUID());
    }

    public static Map<EntityType<?>, Integer> parseChunkLimits(
        final Map<String, Integer> entries,
        final Logger logger,
        final String worldName
    ) {
        final Map<EntityType<?>, Integer> limits = new Reference2ObjectOpenHashMap<>();
        for (final Map.Entry<String, Integer> entry : entries.entrySet()) {
            final EntityType<?> type = findEntityType(entry.getKey());
            if (type == null) {
                logger.warn("Unknown chunk-travel-limit entity \"{}\" for level {}", entry.getKey(), worldName);
                continue;
            }
            if (entry.getValue() == null) {
                logger.warn("Missing chunk-travel-limit for \"{}\" on level {}", entry.getKey(), worldName);
                continue;
            }
            limits.put(type, entry.getValue());
        }
        return limits;
    }

    private static @Nullable EntityType<?> findEntityType(final String token) {
        final Identifier identifier = Identifier.tryParse(token.toLowerCase(Locale.ENGLISH));
        if (identifier == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
    }
}
