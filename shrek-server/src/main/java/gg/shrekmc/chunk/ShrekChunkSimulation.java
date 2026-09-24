package gg.shrekmc.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ShrekChunkSimulation implements ChunkSimulation {
    public static final ShrekChunkSimulation INSTANCE = new ShrekChunkSimulation();

    private static final int COORD_LIMIT = 30_000_000 >> 4;

    private final Map<ServerLevel, WorldState> worlds = new ConcurrentHashMap<>();

    private ShrekChunkSimulation() {
    }

    private static final class WorldState {
        private final ServerLevel level;

        private final Long2ObjectOpenHashMap<Map<String, SimulationLevel>> chunks = new Long2ObjectOpenHashMap<>();

        private volatile LongSet spawnerChunks = LongSets.EMPTY_SET;

        private WorldState(final ServerLevel level) {
            this.level = level;
        }

        private synchronized boolean put(final long chunk, final String owner, final SimulationLevel simulationLevel) {
            final Map<String, SimulationLevel> owners = this.chunks.computeIfAbsent(chunk, _ -> new HashMap<>());
            final Set<SimulationLevel> before = levels(owners);
            if (owners.put(owner, simulationLevel) == simulationLevel) {
                return true;
            }
            this.apply(chunk, before, levels(owners));
            return true;
        }

        private synchronized boolean remove(final long chunk, final String owner) {
            final Map<String, SimulationLevel> owners = this.chunks.get(chunk);
            if (owners == null) {
                return false;
            }
            final Set<SimulationLevel> before = levels(owners);
            if (owners.remove(owner) == null) {
                return false;
            }
            if (owners.isEmpty()) {
                this.chunks.remove(chunk);
            }
            this.apply(chunk, before, levels(owners));
            return true;
        }

        private synchronized int removeOwner(final String owner) {
            final long[] held = this.chunks.long2ObjectEntrySet().stream()
                .filter(entry -> entry.getValue().containsKey(owner))
                .mapToLong(Long2ObjectOpenHashMap.Entry::getLongKey)
                .toArray();
            for (final long chunk : held) {
                this.remove(chunk, owner);
            }
            return held.length;
        }

        private synchronized boolean contains(final long chunk) {
            return this.chunks.containsKey(chunk);
        }

        private void apply(final long chunk, final Set<SimulationLevel> before, final Set<SimulationLevel> after) {
            final ChunkPos pos = ChunkPos.unpack(chunk);
            for (final SimulationLevel simulationLevel : after) {
                if (!before.contains(simulationLevel)) {
                    this.level.getChunkSource().addTicketAtLevel(TicketType.SHREK_SIMULATION, pos, ticketLevel(simulationLevel));
                }
            }
            for (final SimulationLevel simulationLevel : before) {
                if (!after.contains(simulationLevel)) {
                    this.level.getChunkSource().removeTicketAtLevel(TicketType.SHREK_SIMULATION, pos, ticketLevel(simulationLevel));
                }
            }
            if (before.contains(SimulationLevel.ENTITY_TICKING) != after.contains(SimulationLevel.ENTITY_TICKING)) {
                final LongSet spawners = new LongOpenHashSet();
                for (final Long2ObjectOpenHashMap.Entry<Map<String, SimulationLevel>> entry : this.chunks.long2ObjectEntrySet()) {
                    if (entry.getValue().containsValue(SimulationLevel.ENTITY_TICKING)) {
                        spawners.add(entry.getLongKey());
                    }
                }
                this.spawnerChunks = spawners.isEmpty() ? LongSets.EMPTY_SET : LongSets.unmodifiable(spawners);
            }
        }

        private static Set<SimulationLevel> levels(final Map<String, SimulationLevel> owners) {
            return owners.isEmpty() ? EnumSet.noneOf(SimulationLevel.class) : EnumSet.copyOf(owners.values());
        }
    }

    private static int ticketLevel(final SimulationLevel level) {
        return switch (level) {
            case ENTITY_TICKING -> ChunkLevel.ENTITY_TICKING_LEVEL;
            case BLOCK_TICKING -> ChunkLevel.BLOCK_TICKING_LEVEL;
            case LOADED -> ChunkLevel.FULL_CHUNK_LEVEL;
        };
    }

    private static boolean valid(final World world, final int chunkX, final int chunkZ) {
        return world instanceof CraftWorld && Math.abs(chunkX) <= COORD_LIMIT && Math.abs(chunkZ) <= COORD_LIMIT;
    }

    private WorldState state(final World world) {
        final ServerLevel level = ((CraftWorld) world).getHandle();
        return this.worlds.computeIfAbsent(level, WorldState::new);
    }

    @Override
    public boolean simulate(final World world, final int chunkX, final int chunkZ, final NamespacedKey owner, final SimulationLevel level) {
        if (!valid(world, chunkX, chunkZ)) {
            return false;
        }
        return this.state(world).put(ChunkPos.pack(chunkX, chunkZ), owner.asString(), level);
    }

    @Override
    public boolean stop(final World world, final int chunkX, final int chunkZ, final NamespacedKey owner) {
        if (!valid(world, chunkX, chunkZ)) {
            return false;
        }
        final WorldState state = this.worlds.get(((CraftWorld) world).getHandle());
        return state != null && state.remove(ChunkPos.pack(chunkX, chunkZ), owner.asString());
    }

    @Override
    public int stopAll(final NamespacedKey owner) {
        int stopped = 0;
        for (final WorldState state : this.worlds.values()) {
            stopped += state.removeOwner(owner.asString());
        }
        return stopped;
    }

    @Override
    public boolean isSimulated(final World world, final int chunkX, final int chunkZ) {
        if (!valid(world, chunkX, chunkZ)) {
            return false;
        }
        final WorldState state = this.worlds.get(((CraftWorld) world).getHandle());
        return state != null && state.contains(ChunkPos.pack(chunkX, chunkZ));
    }

    public static boolean activatesSpawner(final Level level, final BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        final WorldState state = INSTANCE.worlds.get(serverLevel);
        return state != null && state.spawnerChunks.contains(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
    }

    public void forget(final ServerLevel level) {
        this.worlds.remove(level);
    }
}
