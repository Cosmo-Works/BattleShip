package dev.cosmoworks.battleship;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class Battleship {

    public static void load() {
        BattleshipServerConfig.ensureLoaded();
        BattleshipWorldConfig.ensureLoaded();
        BattleshipAPI.chunkSimulation(dev.cosmoworks.battleship.chunk.BattleshipChunkSimulation.INSTANCE);
        BattleshipAPI.spawnCrowd(dev.cosmoworks.battleship.spawn.BattleshipSpawnCrowd.INSTANCE);
    }

    private Battleship() {
    }
}
