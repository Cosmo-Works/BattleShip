package dev.cosmoworks.battleship.debug;

import dev.cosmoworks.battleship.BattleshipServerConfig;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RegionPhaseTiming {
    private static final Logger LOGGER = LoggerFactory.getLogger("BattleshipRegions");

    public enum Phase {
        REGION_TASKS("region tasks (chunk/tick task queue, joins)"),
        PACKETS("player packets"),
        PLUGIN_REGION_TASKS("plugin region scheduler"),
        ENTITY_SCHEDULERS("entity schedulers (plugin + server)"),
        BLOCK_TICKS("scheduled block/fluid ticks"),
        CHUNK_TICKETS("ticket and distance updates"),
        CHUNK_LOADER("player chunk loader and sending"),
        CHUNK_TICK("chunk ticking (random ticks, spawning, changed blocks)"),
        TRACKER("entity tracker"),
        CHUNK_UNLOAD("chunk unload/save queue"),
        BLOCK_EVENTS("block events (pistons, note blocks)"),
        ENTITY_PLAYERS("entity tick: players"),
        ENTITY_MOBS("entity tick: mobs"),
        ENTITY_ITEMS("entity tick: items and xp"),
        ENTITY_OTHER("entity tick: other (tnt, falling blocks, projectiles, ...)"),
        BLOCK_ENTITIES("block entities (hoppers, spawners, ...)"),
        CONNECTIONS("connection ticks (player doTick, keep-alive, chat)"),
        FLUSH("connection flush"),
        HELPER_WAIT("waiting for helper threads (inside other phases)");

        final String description;

        Phase(final String description) {
            this.description = description;
        }
    }

    private static final Phase[] PHASES = Phase.values();

    static final class Stats {
        final String name;
        final LongAdder ticks = new LongAdder();
        final LongAdder total = new LongAdder();
        long windowMax;
        final LongAdder[] phases = new LongAdder[PHASES.length];
        final Map<Class<?>, LongAdder[]> packets = new ConcurrentHashMap<>();
        volatile int players;
        volatile int entities;
        volatile int chunks;
        volatile long lastTick = System.nanoTime();

        Stats(final String name) {
            this.name = name;
            for (int i = 0; i < this.phases.length; ++i) {
                this.phases[i] = new LongAdder();
            }
        }
    }

    private static final Map<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>, Stats> REGIONS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Stats> CURRENT = new ThreadLocal<>();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile List<String> lastReport = List.of("No report yet: enable debug.region-phase-timing and wait one interval.");

    private RegionPhaseTiming() {
    }

    public static boolean enabled() {
        return BattleshipServerConfig.debug().regionPhaseTiming;
    }

    public static long beginTick() {
        if (!enabled()) {
            CURRENT.remove();
            return 0L;
        }
        final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region = TickRegionScheduler.getCurrentRegion();
        if (region == null) {
            return 0L;
        }
        startReporter();
        final Stats stats = REGIONS.computeIfAbsent(region, r -> new Stats(r.getData().world.getWorld().getName() + " #" + r.getId() + " @" + r.getCenterChunk()));
        CURRENT.set(stats);
        return System.nanoTime();
    }

    public static void endTick(final long start) {
        final Stats stats = CURRENT.get();
        if (start == 0L || stats == null) {
            return;
        }
        final long now = System.nanoTime();
        final long took = now - start;
        stats.ticks.increment();
        stats.total.add(took);
        if (took > stats.windowMax) {
            stats.windowMax = took;
        }
        stats.lastTick = now;
        final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region = TickRegionScheduler.getCurrentRegion();
        if (region != null) {
            final TickRegions.RegionStats counts = region.getData().getRegionStats();
            stats.players = counts.getPlayerCount();
            stats.entities = counts.getEntityCount();
            stats.chunks = counts.getChunkCount();
        }
        CURRENT.remove();
    }

    public static long start() {
        return CURRENT.get() == null ? 0L : System.nanoTime();
    }

    public static long start(final Phase phase) {
        return start();
    }

    public static void end(final Phase phase, final long start) {
        if (start == 0L) {
            return;
        }
        final Stats stats = CURRENT.get();
        if (stats != null) {
            final long took = System.nanoTime() - start;
            stats.phases[phase.ordinal()].add(took);
        }
    }

    public static Phase entityPhase(final Entity entity) {
        if (entity instanceof ServerPlayer) {
            return Phase.ENTITY_PLAYERS;
        }
        if (entity instanceof Mob) {
            return Phase.ENTITY_MOBS;
        }
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
            return Phase.ENTITY_ITEMS;
        }
        return Phase.ENTITY_OTHER;
    }

    public static void packet(final Class<?> packetClass, final long start) {
        if (start == 0L) {
            return;
        }
        final Stats stats = CURRENT.get();
        if (stats == null) {
            return;
        }
        final long took = System.nanoTime() - start;
        stats.phases[Phase.PACKETS.ordinal()].add(took);
        final LongAdder[] adders = stats.packets.computeIfAbsent(packetClass, k -> new LongAdder[] {new LongAdder(), new LongAdder()});
        adders[0].add(took);
        adders[1].increment();
    }

    private static void startReporter() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        final Thread thread = new Thread(RegionPhaseTiming::loop, "Battleship Region Timing");
        thread.setDaemon(true);
        thread.start();
    }

    private static void loop() {
        long last = System.nanoTime();
        while (true) {
            final int interval = Math.max(5, BattleshipServerConfig.debug().regionPhaseLogInterval);
            try {
                Thread.sleep(interval * 1000L);
            } catch (final InterruptedException e) {
                return;
            }
            final long now = System.nanoTime();
            final double seconds = (now - last) / 1.0e9;
            last = now;
            try {
                final List<String> report = report(seconds, BattleshipServerConfig.debug().regionPhaseLogRegions);
                lastReport = report;
                if (enabled()) {
                    for (final String line : report) {
                        LOGGER.info(line);
                    }
                }
            } catch (final Throwable thrown) {
                LOGGER.warn("Couldn't build the region timing report", thrown);
            }
        }
    }

    public static List<String> lastReport() {
        return lastReport;
    }

    private record Row(Stats stats, long ticks, long total, long max, long[] phases, List<Map.Entry<Class<?>, long[]>> packets) {
    }

    static List<String> report(final double seconds, final int topRegions) {
        final long now = System.nanoTime();
        REGIONS.entrySet().removeIf(entry -> now - entry.getValue().lastTick > 120_000_000_000L);
        return report(seconds, topRegions, REGIONS.values());
    }

    static List<String> report(final double seconds, final int topRegions, final java.util.Collection<Stats> regions) {
        final List<Row> rows = new ArrayList<>();
        for (final Stats stats : regions) {
            final long ticks = stats.ticks.sumThenReset();
            final long total = stats.total.sumThenReset();
            final long max = stats.windowMax;
            stats.windowMax = 0L;
            final long[] phases = new long[PHASES.length];
            for (int i = 0; i < PHASES.length; ++i) {
                phases[i] = stats.phases[i].sumThenReset();
            }
            final List<Map.Entry<Class<?>, long[]>> packets = new ArrayList<>();
            for (final Map.Entry<Class<?>, LongAdder[]> entry : stats.packets.entrySet()) {
                final long nanos = entry.getValue()[0].sumThenReset();
                final long count = entry.getValue()[1].sumThenReset();
                if (count > 0) {
                    packets.add(Map.entry(entry.getKey(), new long[] {nanos, count}));
                }
            }
            packets.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
            if (ticks > 0) {
                rows.add(new Row(stats, ticks, total, max, phases, packets));
            }
        }
        rows.sort((a, b) -> Long.compare(b.total(), a.total()));
        final List<String> lines = new ArrayList<>();
        lines.add("Region tick phases, last %.0f s, %d regions ticked; top %d by tick time:".formatted(seconds, rows.size(), Math.min(topRegions, rows.size())));
        for (int r = 0; r < Math.min(topRegions, rows.size()); ++r) {
            final Row row = rows.get(r);
            final double busyPercent = row.total() / (seconds * 1.0e9) * 100.0;
            final double mean = row.total() / (double) row.ticks() / 1.0e6;
            lines.add("  %s: %d players, %d entities, %d chunks | %d ticks, %.1f ms mean, %.1f ms max, thread busy %.0f%%".formatted(
                row.stats().name, row.stats().players, row.stats().entities, row.stats().chunks, row.ticks(), mean, row.max() / 1.0e6, busyPercent
            ));
            long covered = 0;
            final StringBuilder phases = new StringBuilder("    ");
            final List<Integer> order = new ArrayList<>();
            for (int i = 0; i < PHASES.length; ++i) {
                if (PHASES[i] != Phase.HELPER_WAIT) {
                    covered += row.phases()[i];
                }
                order.add(i);
            }
            order.sort((a, b) -> Long.compare(row.phases()[b], row.phases()[a]));
            for (final int i : order) {
                if (row.phases()[i] == 0L) {
                    continue;
                }
                phases.append(PHASES[i].name().toLowerCase(java.util.Locale.ROOT)).append(' ')
                    .append(percent(row.phases()[i], row.total())).append(", ");
            }
            phases.append("other ").append(percent(Math.max(0L, row.total() - covered), row.total()));
            lines.add(phases.toString());
            if (!row.packets().isEmpty()) {
                final StringBuilder packets = new StringBuilder("    packets: ");
                for (int i = 0; i < Math.min(5, row.packets().size()); ++i) {
                    final Map.Entry<Class<?>, long[]> entry = row.packets().get(i);
                    final String name = entry.getKey().getName();
                    packets.append(name.substring(name.lastIndexOf('.') + 1)).append(' ')
                        .append(percent(entry.getValue()[0], row.total())).append(" (")
                        .append(Math.round(entry.getValue()[1] / seconds)).append("/s, ")
                        .append("%.1f".formatted(entry.getValue()[0] / (double) entry.getValue()[1] / 1000.0)).append(" us each)");
                    if (i + 1 < Math.min(5, row.packets().size())) {
                        packets.append(", ");
                    }
                }
                lines.add(packets.toString());
            }
        }
        return lines;
    }

    private static String percent(final long part, final long total) {
        return total <= 0L ? "0%" : "%.1f%%".formatted(part * 100.0 / total);
    }

    static @Nullable Stats current() {
        return CURRENT.get();
    }
}
