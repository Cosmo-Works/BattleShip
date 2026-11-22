package dev.cosmoworks.battleship.parallel;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity;
import ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity;
import ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity;
import dev.cosmoworks.battleship.BattleshipServerConfig;
import dev.cosmoworks.battleship.spawn.BattleshipSpawnCrowd;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public final class ParallelTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger("BattleshipParallelTracker");

    private ParallelTracker() {
    }

    public static final class Scan {
        final ChunkMap.TrackedEntity tracker;
        boolean clear;
        @Nullable List<ServerPlayer> remove;
        @Nullable List<ServerPlayer> add;
        @Nullable List<ServerPlayer> crowd;

        private Scan(final ChunkMap.TrackedEntity tracker) {
            this.tracker = tracker;
        }

        public static Scan clear(final ChunkMap.TrackedEntity tracker) {
            final Scan scan = new Scan(tracker);
            scan.clear = true;
            return scan;
        }

        private static Scan of(final @Nullable Scan scan, final ChunkMap.TrackedEntity tracker) {
            return scan != null ? scan : new Scan(tracker);
        }

        public static Scan remove(final @Nullable Scan scan, final ChunkMap.TrackedEntity tracker, final ServerPlayer player) {
            final Scan result = of(scan, tracker);
            if (result.remove == null) {
                result.remove = new ArrayList<>(4);
            }
            result.remove.add(player);
            return result;
        }

        public static Scan add(final @Nullable Scan scan, final ChunkMap.TrackedEntity tracker, final ServerPlayer player) {
            final Scan result = of(scan, tracker);
            if (result.add == null) {
                result.add = new ArrayList<>(4);
            }
            result.add.add(player);
            return result;
        }

        public static Scan crowd(final @Nullable Scan scan, final ChunkMap.TrackedEntity tracker, final ServerPlayer player) {
            final Scan result = of(scan, tracker);
            if (result.crowd == null) {
                result.crowd = new ArrayList<>(8);
            }
            result.crowd.add(player);
            return result;
        }

        void apply() {
            final ChunkMap.TrackedEntity tracker = this.tracker;
            if (this.clear) {
                ((EntityTrackerTrackedEntity) tracker).moonrise$clearPlayers();
                return;
            }
            if (this.remove != null) {
                for (final ServerPlayer player : this.remove) {
                    tracker.removePlayer(player);
                }
            }
            if (this.crowd != null && tracker.battleship$entity() instanceof ServerPlayer tracked) {
                for (final ServerPlayer player : this.crowd) {
                    final boolean seen = tracker.seenBy.contains(player.connection);
                    tracker.battleship$apply(player, BattleshipSpawnCrowd.canTrack(player, tracked, seen));
                }
            }
            if (this.add != null) {
                for (final ServerPlayer player : this.add) {
                    tracker.battleship$apply(player, true);
                }
            }
        }
    }

    public static boolean shouldRun(final int trackedEntities) {
        final BattleshipServerConfig.Parallel config = BattleshipServerConfig.parallel();
        return config.entityTracker && trackedEntities >= config.minEntitiesPerBatch * 2;
    }

    private static boolean serialTracker(final ChunkMap.TrackedEntity tracker) {
        return tracker.getClass() != ChunkMap.TrackedEntity.class;
    }

    private static boolean serialSend(final Entity entity, final ChunkMap.TrackedEntity tracker) {
        return serialTracker(tracker)
            || entity instanceof ItemFrame
            || (entity instanceof ServerPlayer && entity.syncVelocity);
    }

    private static void sendChanges(final Entity entity, final ChunkMap.TrackedEntity tracker) {
        final FullChunkStatus status = ((ChunkSystemEntity) entity).moonrise$getChunkStatus();
        if (((EntityTrackerTrackedEntity) tracker).moonrise$hasPlayers() || (status != null && status.isOrAfter(FullChunkStatus.ENTITY_TICKING))) {
            tracker.serverEntity.sendChanges();
        }
    }

    public static void tick(final ServerLevel level, final RegionizedWorldData worldData, final NearbyPlayers nearbyPlayers) {
        final BattleshipServerConfig.Parallel config = BattleshipServerConfig.parallel();
        final ReferenceList<Entity> trackerEntities = worldData.trackerEntities;

        if (config.crowdSelection) {
            final List<ServerPlayer> viewers = new ArrayList<>(worldData.getLocalPlayers());
            final int viewerCount = viewers.size();
            final int viewerBatches = RegionParallel.batchesFor(viewerCount, Math.max(1, config.minPlayersPerBatch / 4));
            RegionParallel.run(viewerBatches, batch -> {
                final int from = (int) ((long) viewerCount * batch / viewerBatches);
                final int to = (int) ((long) viewerCount * (batch + 1) / viewerBatches);
                for (int i = from; i < to; ++i) {
                    BattleshipSpawnCrowd.prepareDeferred(viewers.get(i));
                }
            });
            for (final ServerPlayer player : viewers) {
                BattleshipSpawnCrowd.applyDeferred(player);
            }
        } else {
        for (final ServerPlayer player : worldData.getLocalPlayers()) {
            BattleshipSpawnCrowd.prepare(player);
        }
        }

        final Entity[] scanEntities = trackerEntities.getRawDataUnchecked();
        final int scanCount = Math.min(scanEntities.length, trackerEntities.size());
        final int scanBatches = RegionParallel.batchesFor(scanCount, config.minEntitiesPerBatch);
        @SuppressWarnings("unchecked")
        final List<Object>[] results = new List[scanBatches];
        RegionParallel.run(scanBatches, batch -> {
            final int from = (int) ((long) scanCount * batch / scanBatches);
            final int to = (int) ((long) scanCount * (batch + 1) / scanBatches);
            List<Object> out = null;
            for (int i = from; i < to; ++i) {
                final Entity entity = scanEntities[i];
                if (entity == null) {
                    continue;
                }
                final ChunkMap.TrackedEntity tracker = ((EntityTrackerEntity) entity).moonrise$getTrackedEntity();
                if (tracker == null) {
                    continue;
                }
                final Object result;
                if (serialTracker(tracker)) {
                    result = tracker;
                } else if (tracker.shouldUpdatePlayers()) {
                    result = tracker.battleship$scan(nearbyPlayers.getChunk(entity.chunkPosition()));
                } else {
                    result = null;
                }
                if (result != null) {
                    if (out == null) {
                        out = new ArrayList<>();
                    }
                    out.add(result);
                }
            }
            results[batch] = out;
        });

        for (final List<Object> batch : results) {
            if (batch == null) {
                continue;
            }
            for (final Object result : batch) {
                try {
                    if (result instanceof Scan scan) {
                        scan.apply();
                    } else if (result instanceof ChunkMap.TrackedEntity tracker && tracker.shouldUpdatePlayers()) {
                        final Entity entity = tracker.battleship$entity();
                        ((EntityTrackerTrackedEntity) tracker).moonrise$tick(nearbyPlayers.getChunk(entity.chunkPosition()));
                    }
                } catch (final Throwable thrown) {
                    LOGGER.error("Error applying entity tracker changes", thrown);
                }
            }
        }

        final Entity[] sendEntities = trackerEntities.getRawDataUnchecked();
        final int sendCount = Math.min(sendEntities.length, trackerEntities.size());
        final int sendBatches = RegionParallel.batchesFor(sendCount, config.minEntitiesPerBatch);
        RegionParallel.run(sendBatches, batch -> {
            final int from = (int) ((long) sendCount * batch / sendBatches);
            final int to = (int) ((long) sendCount * (batch + 1) / sendBatches);
            for (int i = from; i < to; ++i) {
                final Entity entity = sendEntities[i];
                if (entity == null) {
                    continue;
                }
                final ChunkMap.TrackedEntity tracker = ((EntityTrackerEntity) entity).moonrise$getTrackedEntity();
                if (tracker == null || serialSend(entity, tracker)) {
                    continue;
                }
                sendChanges(entity, tracker);
            }
        });

        for (int i = 0; i < sendCount; ++i) {
            final Entity entity = sendEntities[i];
            if (entity == null) {
                continue;
            }
            final ChunkMap.TrackedEntity tracker = ((EntityTrackerEntity) entity).moonrise$getTrackedEntity();
            if (tracker != null && serialSend(entity, tracker)) {
                try {
                    sendChanges(entity, tracker);
                } catch (final Throwable thrown) {
                    LOGGER.error("Error sending entity tracker changes", thrown);
                }
            }
        }
    }
}
