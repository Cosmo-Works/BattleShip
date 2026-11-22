package dev.cosmoworks.battleship.octopus;

import ca.spottedleaf.moonrise.common.util.TickThread;
import dev.cosmoworks.battleship.BattleshipServerConfig;
import dev.cosmoworks.battleship.debug.RegionPhaseTiming;
import io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PluginGate {
    private static final Logger LOGGER = LoggerFactory.getLogger("BattleshipGate");

    public static volatile boolean OBSERVE;

    private PluginGate() {
    }

    public static void configure(final boolean observe) {
        OBSERVE = observe;
        if (observe) {
            startReporter();
        }
    }

    static final int KIND_TICK_OTHER = RegionPhaseTiming.Phase.values().length;
    static final int KIND_ASYNC = KIND_TICK_OTHER + 1;
    static final int KINDS = KIND_ASYNC + 1;

    static String kindName(final int kind) {
        if (kind == KIND_TICK_OTHER) {
            return "tick_other";
        }
        if (kind == KIND_ASYNC) {
            return "async";
        }
        return RegionPhaseTiming.Phase.values()[kind].name().toLowerCase(Locale.ROOT);
    }

    static int currentKind() {
        if (!TickThread.isTickThread()) {
            return KIND_ASYNC;
        }
        final RegionPhaseTiming.Phase phase = RegionPhaseTiming.currentPhase();
        return phase == null ? KIND_TICK_OTHER : phase.ordinal();
    }

    private static final class Frames {
        final long[] child = new long[64];
        int depth;
    }

    private static final ThreadLocal<Frames> FRAMES = ThreadLocal.withInitial(Frames::new);

    public static long enter() {
        final Frames frames = FRAMES.get();
        if (frames.depth < frames.child.length) {
            frames.child[frames.depth] = 0L;
        }
        ++frames.depth;
        return System.nanoTime();
    }

    private static long exit(final long start) {
        final long total = System.nanoTime() - start;
        final Frames frames = FRAMES.get();
        final int depth = --frames.depth;
        final long children = depth < frames.child.length ? frames.child[depth] : 0L;
        if (depth > 0 && depth - 1 < frames.child.length) {
            frames.child[depth - 1] += total;
        }
        return Math.max(0L, total - children);
    }

    static final class Stats {
        final LongAdder calls = new LongAdder();
        final LongAdder nanos = new LongAdder();
        final Histogram histogram = new Histogram();

        void add(final long ns) {
            this.calls.increment();
            this.nanos.add(ns);
            this.histogram.add(ns);
        }
    }

    private static final Map<Class<?>, Map<Plugin, Stats[]>> LISTENERS = new ConcurrentHashMap<>();
    private static final Map<Plugin, Boolean> FOLIA_SUPPORTED = new ConcurrentHashMap<>();

    public static void listener(final Event event, final Plugin plugin, final long start) {
        final long ns = exit(start);
        final Map<Plugin, Stats[]> byPlugin = LISTENERS.computeIfAbsent(event.getClass(), k -> new ConcurrentHashMap<>());
        Stats[] kinds = byPlugin.get(plugin);
        if (kinds == null) {
            kinds = byPlugin.computeIfAbsent(plugin, k -> new Stats[KINDS]);
        }
        final int kind = currentKind();
        Stats stats = kinds[kind];
        if (stats == null) {
            synchronized (kinds) {
                stats = kinds[kind];
                if (stats == null) {
                    stats = kinds[kind] = new Stats();
                }
            }
        }
        stats.add(ns);
    }

    public enum Scheduler { ENTITY, REGION, GLOBAL, ASYNC }

    private static final Map<Plugin, Stats[]> TASKS = new ConcurrentHashMap<>();

    public static void task(final Scheduler scheduler, final Plugin plugin, final long start) {
        final long ns = exit(start);
        final Stats[] stats = TASKS.computeIfAbsent(plugin, k -> {
            final Stats[] ret = new Stats[Scheduler.values().length];
            for (int i = 0; i < ret.length; ++i) {
                ret[i] = new Stats();
            }
            return ret;
        });
        stats[scheduler.ordinal()].add(ns);
    }

    static boolean foliaSupported(final Plugin plugin) {
        return FOLIA_SUPPORTED.computeIfAbsent(plugin, p -> {
            try {
                return p.getPluginMeta().isFoliaSupported();
            } catch (final Throwable ignored) {
                return false;
            }
        });
    }

    static final class Histogram {
        private final AtomicLongArray buckets = new AtomicLongArray(64 * 4);

        static int index(final long ns) {
            if (ns <= 0) {
                return 0;
            }
            final int log = 63 - Long.numberOfLeadingZeros(ns);
            final int sub = log >= 2 ? (int) ((ns >>> (log - 2)) & 3) : 0;
            return Math.min(64 * 4 - 1, log * 4 + sub);
        }

        static long upperBound(final int index) {
            final int log = index / 4;
            final int sub = index % 4;
            if (log < 2) {
                return 1L << (log + 1);
            }
            return (4L + sub + 1) << (log - 2);
        }

        void add(final long ns) {
            this.buckets.incrementAndGet(index(ns));
        }

        long[] takeCounts() {
            final long[] ret = new long[this.buckets.length()];
            for (int i = 0; i < ret.length; ++i) {
                ret[i] = this.buckets.getAndSet(i, 0L);
            }
            return ret;
        }

        static long percentile(final long[] counts, final double fraction) {
            long total = 0;
            for (final long c : counts) {
                total += c;
            }
            if (total == 0) {
                return 0L;
            }
            final long target = (long) Math.ceil(total * fraction);
            long seen = 0;
            for (int i = 0; i < counts.length; ++i) {
                seen += counts[i];
                if (seen >= target) {
                    return upperBound(i);
                }
            }
            return upperBound(counts.length - 1);
        }
    }

    record ListenerRow(String kind, String event, String plugin, boolean foliaSupported, long calls, long nanos, long p99) {
    }

    record TaskRow(String plugin, String scheduler, boolean foliaSupported, long calls, long nanos, long p99) {
    }

    record Footprint(Map<String, Integer> entities, Map<String, Integer> goals, Map<String, Integer> trackers) {
    }

    record Report(double seconds, List<ListenerRow> listeners, List<TaskRow> tasks, Footprint footprint,
                  long[] phaseNanos, long regionTickNanos, int players, HeadCost head) {
    }

    record HeadCost(double vanillaMs, double listenerMs, double schedulerMs, double totalMs, double perPlayerUs, boolean phaseTimingOn) {
    }

    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile List<String> lastLines = List.of("No report yet: enable octopus.gate.observe and wait one interval.");
    private static final List<Report> HISTORY = new ArrayList<>();

    private static void startReporter() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        final Thread thread = new Thread(PluginGate::loop, "Battleship Gate Report");
        thread.setDaemon(true);
        thread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(PluginGate::dumpOnShutdown, "Battleship Gate Dump"));
    }

    private static long lastReport = System.nanoTime();

    private static void loop() {
        while (true) {
            try {
                Thread.sleep(Math.max(5, BattleshipServerConfig.octopus().gate.reportInterval) * 1000L);
            } catch (final InterruptedException e) {
                return;
            }
            if (!OBSERVE) {
                continue;
            }
            try {
                final Report report = take();
                final List<String> lines = format(report, BattleshipServerConfig.octopus().gate.reportRows);
                lastLines = lines;
                for (final String line : lines) {
                    LOGGER.info(line);
                }
            } catch (final Throwable thrown) {
                LOGGER.warn("Couldn't build the gate report", thrown);
            }
        }
    }

    public static List<String> lastReport() {
        return lastLines;
    }

    static synchronized Report take() {
        final long now = System.nanoTime();
        final double seconds = Math.max(0.001, (now - lastReport) / 1.0e9);
        lastReport = now;

        final List<ListenerRow> listeners = new ArrayList<>();
        for (final Map.Entry<Class<?>, Map<Plugin, Stats[]>> byEvent : LISTENERS.entrySet()) {
            for (final Map.Entry<Plugin, Stats[]> byPlugin : byEvent.getValue().entrySet()) {
                final Stats[] kinds = byPlugin.getValue();
                for (int kind = 0; kind < kinds.length; ++kind) {
                    final Stats stats = kinds[kind];
                    if (stats == null) {
                        continue;
                    }
                    final long calls = stats.calls.sumThenReset();
                    final long nanos = stats.nanos.sumThenReset();
                    final long p99 = Histogram.percentile(stats.histogram.takeCounts(), 0.99);
                    if (calls > 0) {
                        listeners.add(new ListenerRow(kindName(kind), byEvent.getKey().getSimpleName(), byPlugin.getKey().getName(),
                            foliaSupported(byPlugin.getKey()), calls, nanos, p99));
                    }
                }
            }
        }
        listeners.sort((a, b) -> Long.compare(b.nanos(), a.nanos()));

        final List<TaskRow> tasks = new ArrayList<>();
        for (final Map.Entry<Plugin, Stats[]> byPlugin : TASKS.entrySet()) {
            for (final Scheduler scheduler : Scheduler.values()) {
                final Stats stats = byPlugin.getValue()[scheduler.ordinal()];
                final long calls = stats.calls.sumThenReset();
                final long nanos = stats.nanos.sumThenReset();
                final long p99 = Histogram.percentile(stats.histogram.takeCounts(), 0.99);
                if (calls > 0) {
                    tasks.add(new TaskRow(byPlugin.getKey().getName(), scheduler.name().toLowerCase(Locale.ROOT),
                        foliaSupported(byPlugin.getKey()), calls, nanos, p99));
                }
            }
        }
        tasks.sort((a, b) -> Long.compare(b.nanos(), a.nanos()));

        final long[] phases = RegionPhaseTiming.takeAllRegionTotals();
        final long regionTick = RegionPhaseTiming.takeAllRegionTickTime();
        final int players = onlinePlayers();
        final Report report = new Report(seconds, listeners, tasks, PluginFootprint.count(), phases, regionTick, players,
            headCost(seconds, listeners, tasks, phases, players, RegionPhaseTiming.enabled()));
        synchronized (HISTORY) {
            HISTORY.add(report);
        }
        return report;
    }

    static final RegionPhaseTiming.Phase[] HEAD_PHASES = {
        RegionPhaseTiming.Phase.PACKETS, RegionPhaseTiming.Phase.CONNECTIONS, RegionPhaseTiming.Phase.ENTITY_PLAYERS,
        RegionPhaseTiming.Phase.ENTITY_SCHEDULERS, RegionPhaseTiming.Phase.PLUGIN_REGION_TASKS
    };

    static boolean headPhase(final String kind) {
        for (final RegionPhaseTiming.Phase phase : HEAD_PHASES) {
            if (phase.name().toLowerCase(Locale.ROOT).equals(kind)) {
                return true;
            }
        }
        return false;
    }

    static HeadCost headCost(final double seconds, final List<ListenerRow> listeners, final List<TaskRow> tasks,
                             final long[] phases, final int players, final boolean phaseTimingOn) {
        final double ticks = seconds * 20.0;
        long vanilla = 0;
        for (final RegionPhaseTiming.Phase phase : HEAD_PHASES) {
            vanilla += phases[phase.ordinal()];
        }
        long listenerNs = 0;
        for (final ListenerRow row : listeners) {
            if (row.kind().equals("async")) {
                continue;
            }
            if (phaseTimingOn && headPhase(row.kind())) {
                continue;
            }
            listenerNs += row.nanos();
        }
        long schedulerNs = 0;
        for (final TaskRow row : tasks) {
            if (row.scheduler().equals("global") || (!phaseTimingOn && (row.scheduler().equals("entity") || row.scheduler().equals("region")))) {
                schedulerNs += row.nanos();
            }
        }
        final double vanillaMs = vanilla / ticks / 1.0e6;
        final double listenerMs = listenerNs / ticks / 1.0e6;
        final double schedulerMs = schedulerNs / ticks / 1.0e6;
        final double total = vanillaMs + listenerMs + schedulerMs;
        return new HeadCost(vanillaMs, listenerMs, schedulerMs, total, players == 0 ? 0.0 : total * 1000.0 / players, phaseTimingOn);
    }

    private static int onlinePlayers() {
        try {
            return org.bukkit.Bukkit.getOnlinePlayers().size();
        } catch (final Throwable ignored) {
            return 0;
        }
    }

    static List<String> format(final Report report, final int rows) {
        final List<String> lines = new ArrayList<>();
        final double seconds = report.seconds();
        final HeadCost head = report.head();
        lines.add(String.format(Locale.ROOT,
            "Head cost estimate: %.1f ms/tick (vanilla head work %.1f + listeners elsewhere %.1f + global tasks %.1f), %d players, %.1f us/player/tick%s",
            head.totalMs(), head.vanillaMs(), head.listenerMs(), head.schedulerMs(), report.players(), head.perPlayerUs(),
            head.phaseTimingOn() ? "" : " [debug.region-phase-timing is off: vanilla part missing]"));
        long listenerTotal = 0;
        long listenerCalls = 0;
        for (final ListenerRow row : report.listeners()) {
            listenerTotal += row.nanos();
            listenerCalls += row.calls();
        }
        lines.add(String.format(Locale.ROOT, "Listeners: %.1f ms/s total, %d calls/s; top %d (kind, event, plugin, folia, calls/s, ms/s, p99 us):",
            listenerTotal / seconds / 1.0e6, Math.round(listenerCalls / seconds), Math.min(rows, report.listeners().size())));
        for (int i = 0; i < Math.min(rows, report.listeners().size()); ++i) {
            final ListenerRow row = report.listeners().get(i);
            lines.add(String.format(Locale.ROOT, "  %-20s %-40s %-18s %-5s %8d/s %8.2f ms/s %8.1f us",
                row.kind(), row.event(), row.plugin(), row.foliaSupported() ? "folia" : "-",
                Math.round(row.calls() / seconds), row.nanos() / seconds / 1.0e6, row.p99() / 1000.0));
        }
        lines.add("Scheduler tasks (plugin, scheduler, folia, runs/s, ms/s, p99 us):");
        for (int i = 0; i < Math.min(rows, report.tasks().size()); ++i) {
            final TaskRow row = report.tasks().get(i);
            lines.add(String.format(Locale.ROOT, "  %-18s %-7s %-5s %8d/s %8.2f ms/s %8.1f us",
                row.plugin(), row.scheduler(), row.foliaSupported() ? "folia" : "-",
                Math.round(row.calls() / seconds), row.nanos() / seconds / 1.0e6, row.p99() / 1000.0));
        }
        lines.add("Plugin classes in the world: entities " + report.footprint().entities() + ", goals " + report.footprint().goals()
            + ", trackers " + report.footprint().trackers());
        return lines;
    }

    private static void dumpOnShutdown() {
        if (!STARTED.get()) {
            return;
        }
        try {
            if (OBSERVE) {
                take();
            }
            final String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            final Path file = Path.of("logs", "gate-" + time + ".json");
            Files.createDirectories(file.getParent());
            final List<Report> history;
            synchronized (HISTORY) {
                history = new ArrayList<>(HISTORY);
            }
            Files.writeString(file, toJson(history), StandardCharsets.UTF_8);
        } catch (final IOException | RuntimeException e) {
            System.err.println("[BattleshipGate] Couldn't write the gate dump: " + e);
        }
    }

    static String toJson(final List<Report> history) {
        final Map<String, long[]> listeners = new HashMap<>();
        final Map<String, long[]> tasks = new HashMap<>();
        final Map<String, Boolean> folia = new HashMap<>();
        double seconds = 0;
        final StringBuilder windows = new StringBuilder("[");
        for (final Report report : history) {
            seconds += report.seconds();
            for (final ListenerRow row : report.listeners()) {
                final String key = row.kind() + "|" + row.event() + "|" + row.plugin();
                final long[] v = listeners.computeIfAbsent(key, k -> new long[3]);
                v[0] += row.calls();
                v[1] += row.nanos();
                v[2] = Math.max(v[2], row.p99());
                folia.put(row.plugin(), row.foliaSupported());
            }
            for (final TaskRow row : report.tasks()) {
                final String key = row.plugin() + "|" + row.scheduler();
                final long[] v = tasks.computeIfAbsent(key, k -> new long[3]);
                v[0] += row.calls();
                v[1] += row.nanos();
                v[2] = Math.max(v[2], row.p99());
                folia.put(row.plugin(), row.foliaSupported());
            }
            if (windows.length() > 1) {
                windows.append(',');
            }
            final HeadCost head = report.head();
            windows.append(String.format(Locale.ROOT,
                "{\"seconds\":%.3f,\"players\":%d,\"headMsPerTick\":%.3f,\"vanillaMs\":%.3f,\"listenerMs\":%.3f,\"schedulerMs\":%.3f,\"perPlayerUs\":%.3f,\"phaseTimingOn\":%b,\"footprint\":%s}",
                report.seconds(), report.players(), head.totalMs(), head.vanillaMs(), head.listenerMs(), head.schedulerMs(), head.perPlayerUs(),
                head.phaseTimingOn(), footprintJson(report.footprint())));
        }
        windows.append(']');
        final StringBuilder out = new StringBuilder("{\"seconds\":").append(String.format(Locale.ROOT, "%.3f", seconds)).append(",\"windows\":").append(windows);
        out.append(",\"listeners\":[");
        boolean first = true;
        for (final Map.Entry<String, long[]> entry : listeners.entrySet()) {
            final String[] parts = entry.getKey().split("\\|", 3);
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(String.format(Locale.ROOT, "{\"kind\":\"%s\",\"event\":\"%s\",\"plugin\":\"%s\",\"foliaSupported\":%b,\"calls\":%d,\"nanos\":%d,\"maxWindowP99Nanos\":%d}",
                json(parts[0]), json(parts[1]), json(parts[2]), folia.getOrDefault(parts[2], false), entry.getValue()[0], entry.getValue()[1], entry.getValue()[2]));
        }
        out.append("],\"tasks\":[");
        first = true;
        for (final Map.Entry<String, long[]> entry : tasks.entrySet()) {
            final String[] parts = entry.getKey().split("\\|", 2);
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(String.format(Locale.ROOT, "{\"plugin\":\"%s\",\"scheduler\":\"%s\",\"foliaSupported\":%b,\"calls\":%d,\"nanos\":%d,\"maxWindowP99Nanos\":%d}",
                json(parts[0]), json(parts[1]), folia.getOrDefault(parts[0], false), entry.getValue()[0], entry.getValue()[1], entry.getValue()[2]));
        }
        out.append("]}");
        return out.toString();
    }

    private static String footprintJson(final Footprint footprint) {
        return "{\"entities\":" + mapJson(footprint.entities()) + ",\"goals\":" + mapJson(footprint.goals()) + ",\"trackers\":" + mapJson(footprint.trackers()) + "}";
    }

    private static String mapJson(final Map<String, Integer> map) {
        final StringBuilder out = new StringBuilder("{");
        for (final Map.Entry<String, Integer> entry : map.entrySet()) {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append('"').append(json(entry.getKey())).append("\":").append(entry.getValue());
        }
        return out.append('}').toString();
    }

    private static String json(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static @Nullable String pluginOf(final Class<?> clazz) {
        final ClassLoader loader = clazz.getClassLoader();
        if (loader instanceof ConfiguredPluginClassLoader pluginLoader) {
            try {
                return pluginLoader.getConfiguration().getName();
            } catch (final Throwable ignored) {
                return "unknown-plugin";
            }
        }
        return null;
    }
}
