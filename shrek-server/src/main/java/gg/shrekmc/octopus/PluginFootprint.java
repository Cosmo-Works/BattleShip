package gg.shrekmc.octopus;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

final class PluginFootprint {
    private PluginFootprint() {
    }

    static PluginGate.Footprint count() {
        final Map<String, Integer> entities = new TreeMap<>();
        final Map<String, Integer> goals = new TreeMap<>();
        final Map<String, Integer> trackers = new TreeMap<>();
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return new PluginGate.Footprint(entities, goals, trackers);
        }
        for (final ServerLevel level : server.getAllLevels()) {
            final Entity[] all;
            try {
                all = level.moonrise$getEntityLookup().getAllCopy();
            } catch (final Throwable ignored) {
                continue;
            }
            for (final Entity entity : all) {
                if (entity == null) {
                    continue;
                }
                final String entityPlugin = PluginGate.pluginOf(entity.getClass());
                if (entityPlugin != null) {
                    entities.merge(entityPlugin + ":" + entity.getClass().getSimpleName(), 1, Integer::sum);
                }
                if (entity instanceof Mob mob) {
                    try {
                        for (final WrappedGoal goal : mob.shrek$goals()) {
                            final String goalPlugin = PluginGate.pluginOf(goal.getGoal().getClass());
                            if (goalPlugin != null) {
                                goals.merge(goalPlugin + ":" + goal.getGoal().getClass().getSimpleName(), 1, Integer::sum);
                            }
                        }
                    } catch (final RuntimeException ignored) {
                    }
                }
                final ChunkMap.TrackedEntity tracker = entity.moonrise$getTrackedEntity();
                if (tracker != null) {
                    final String trackerPlugin = PluginGate.pluginOf(tracker.getClass());
                    if (trackerPlugin != null) {
                        trackers.merge(trackerPlugin + ":" + tracker.getClass().getSimpleName(), 1, Integer::sum);
                    }
                }
            }
        }
        return new PluginGate.Footprint(entities, goals, trackers);
    }
}
