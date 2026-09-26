package gg.shrekmc;

import gg.shrekmc.chunk.ChunkSimulation;
import gg.shrekmc.spawn.SpawnCrowd;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ShrekAPI {
    private static volatile @Nullable ChunkSimulation chunkSimulation;
    private static volatile @Nullable SpawnCrowd spawnCrowd;

    private ShrekAPI() {
    }

    public static @Nullable ChunkSimulation chunkSimulation() {
        return chunkSimulation;
    }

    @ApiStatus.Internal
    public static void chunkSimulation(final ChunkSimulation service) {
        chunkSimulation = service;
    }

    public static @Nullable SpawnCrowd spawnCrowd() {
        return spawnCrowd;
    }

    @ApiStatus.Internal
    public static void spawnCrowd(final SpawnCrowd service) {
        spawnCrowd = service;
    }
}
