package me.samsuik.sakura.entity;

import gg.shrekmc.ShrekWorldConfig;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@NullMarked
public final class BrainActivation {
    public static void considerPlayer(final Entity entity, final Player player) {
        if (entity.level() != player.level() || !(entity instanceof Mob)) {
            return;
        }

        final ShrekWorldConfig.Entities.BrainActivation settings = settings(entity);
        if (!settings.enabled || blacklisted(entity, settings) || leaveAloneInWater(entity, settings)) {
            entity.activatedPriority = 1;
            entity.activatedPriorityReset = true;
            return;
        }

        if (!entity.activatedPriorityReset) {
            entity.activatedPriorityReset = true;
            entity.activatedPriority = Math.max(1, settings.maxTickFreq);
        }

        final int distanceSquared = (int) entity.distanceToSqr(player);
        if (distanceSquared <= settings.startDistanceSquared()) {
            entity.activatedPriority = 1;
            return;
        }

        final int fromDistance = distanceSquared >> settings.activationDistMod;
        entity.activatedPriority = Math.max(1, Math.min(fromDistance, entity.activatedPriority));
    }

    public static boolean shouldTickBrain(final LivingEntity entity) {
        if (!settings(entity).enabled) {
            return true;
        }
        final int interval = Math.max(1, entity.activatedPriority);
        return ++entity.brainActivationTick % interval == 0;
    }

    public static int goalSelectorRate(final Entity entity, final boolean inactive) {
        if (!settings(entity).enabled) {
            return inactive ? 3 : 1;
        }
        return Math.max(1, Math.min(entity.activatedPriority, 3));
    }

    public static void parseBlacklist(
        final ShrekWorldConfig.Entities.BrainActivation settings,
        final Logger logger,
        final String worldName
    ) {
        final Set<EntityType<?>> types = new ReferenceOpenHashSet<>();
        for (final String token : settings.blacklistedEntities) {
            final EntityType<?> type = findEntityType(token);
            if (type == null) {
                logger.warn("Unknown brain-activation blacklist entity \"{}\" for level {}", token, worldName);
                continue;
            }
            types.add(type);
        }
        settings.parsedBlacklist.setValue(types);
    }

    private static ShrekWorldConfig.Entities.BrainActivation settings(final Entity entity) {
        return entity.level().shrekConfig().entities.brainActivation;
    }

    private static boolean blacklisted(final Entity entity, final ShrekWorldConfig.Entities.BrainActivation settings) {
        final Set<EntityType<?>> types = settings.parsedBlacklist.valueSafe();
        return types != null && types.contains(entity.getType());
    }

    private static boolean leaveAloneInWater(final Entity entity, final ShrekWorldConfig.Entities.BrainActivation settings) {
        return settings.skipInWater
            && entity.isInWater()
            && !entity.is(EntityTypeTags.CAN_BREATHE_UNDER_WATER);
    }

    private static @Nullable EntityType<?> findEntityType(final String token) {
        String id = token.trim().toLowerCase(Locale.ENGLISH);
        if (id.isEmpty()) {
            return null;
        }
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        final Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
    }

    private BrainActivation() {
    }
}
