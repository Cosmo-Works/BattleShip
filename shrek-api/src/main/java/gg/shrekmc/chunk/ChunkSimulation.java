package gg.shrekmc.chunk;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ChunkSimulation {
    boolean simulate(World world, int chunkX, int chunkZ, NamespacedKey owner, SimulationLevel level);

    boolean stop(World world, int chunkX, int chunkZ, NamespacedKey owner);

    int stopAll(NamespacedKey owner);

    boolean isSimulated(World world, int chunkX, int chunkZ);
}
