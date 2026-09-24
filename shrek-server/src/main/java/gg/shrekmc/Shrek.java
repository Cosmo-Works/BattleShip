package gg.shrekmc;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class Shrek {

    public static void load() {
        ShrekServerConfig.ensureLoaded();
        ShrekWorldConfig.ensureLoaded();
        ShrekAPI.chunkSimulation(gg.shrekmc.chunk.ShrekChunkSimulation.INSTANCE);
    }

    private Shrek() {
    }
}
