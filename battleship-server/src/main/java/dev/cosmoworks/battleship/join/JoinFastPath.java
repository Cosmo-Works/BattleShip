package dev.cosmoworks.battleship.join;

import ca.spottedleaf.concurrentutil.completable.CallbackCompletable;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import dev.cosmoworks.battleship.BattleshipServerConfig;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.jspecify.annotations.Nullable;

public final class JoinFastPath {
    private static final SpawnPositionPool SPAWN_POSITIONS = new SpawnPositionPool();

    private JoinFastPath() {
    }

    public static boolean skipLoadedChunkWaits() {
        return BattleshipServerConfig.join().skipLoadedChunkWaits;
    }

    public static boolean chunksAtLeast(final ServerLevel level, final int chunkX, final int chunkZ, final int radius,
                                        final FullChunkStatus status) {
        final ChunkHolderManager holders = level.moonrise$getChunkTaskScheduler().chunkHolderManager;
        for (int dz = -radius; dz <= radius; ++dz) {
            for (int dx = -radius; dx <= radius; ++dx) {
                final NewChunkHolder holder = holders.getChunkHolder(CoordinateUtils.getChunkKey(chunkX + dx, chunkZ + dz));
                if (holder == null || !holder.getChunkStatus().isOrAfter(status)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void fudgeSpawnLocation(final ServerLevel level, final CallbackCompletable<Location> toComplete) {
        final BattleshipServerConfig.Join config = BattleshipServerConfig.join();
        final int seconds = config.spawnPositionCacheSeconds;
        if (seconds <= 0) {
            ServerPlayer.fudgeSpawnLocation(level, toComplete);
            return;
        }
        final int size = Math.max(1, config.spawnPositionCacheSize);
        final long maxAge = seconds * 1_000_000_000L;
        final Vec3 cached = SPAWN_POSITIONS.take(level, System.nanoTime(), maxAge, size,
            bound -> ThreadLocalRandom.current().nextInt(bound),
            () -> refill(level, size));
        if (cached != null) {
            toComplete.complete(location(level, cached));
            return;
        }
        final CallbackCompletable<Location> own = new CallbackCompletable<>();
        own.addWaiter((final Location loc, final Throwable thrown) -> {
            if (thrown != null) {
                toComplete.completeWithThrowable(thrown);
                return;
            }
            SPAWN_POSITIONS.offer(level, io.papermc.paper.util.MCUtil.toVec3(loc), System.nanoTime(), size);
            toComplete.complete(loc);
        });
        ServerPlayer.fudgeSpawnLocation(level, own);
    }

    private static void refill(final ServerLevel level, final int size) {
        final CallbackCompletable<Location> search = new CallbackCompletable<>();
        search.addWaiter((final @Nullable Location loc, final @Nullable Throwable thrown) -> {
            if (thrown != null || loc == null) {
                SPAWN_POSITIONS.refillFailed(level);
            } else {
                SPAWN_POSITIONS.refilled(level, io.papermc.paper.util.MCUtil.toVec3(loc), System.nanoTime(), size);
            }
        });
        ServerPlayer.fudgeSpawnLocation(level, search);
    }

    private static Location location(final ServerLevel level, final Vec3 position) {
        final var respawn = level.getLevelData().getRespawnData();
        return io.papermc.paper.util.MCUtil.toLocation(level, position, respawn.yaw(), respawn.pitch());
    }

    public static void clear() {
        SPAWN_POSITIONS.clear();
    }
}
