package gg.shrekmc;

import gg.shrekmc.chunk.ChunkSimulation;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ShrekAPI {
    private static volatile @Nullable ChunkSimulation chunkSimulation;

    private ShrekAPI() {
    }

    public static @Nullable ChunkSimulation chunkSimulation() {
        return chunkSimulation;
    }

    @ApiStatus.Internal
    public static void chunkSimulation(final ChunkSimulation service) {
        chunkSimulation = service;
    }
}
