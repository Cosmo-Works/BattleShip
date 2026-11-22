package dev.cosmoworks.battleship.octopus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.cosmoworks.battleship.debug.RegionPhaseTiming;
import java.util.List;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("Battleship")
class PluginGateTest {
    static final class TestEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        @Override
        public @NonNull HandlerList getHandlers() {
            return HANDLERS;
        }
    }

    private static Plugin plugin(final String name, final boolean folia) {
        final Plugin plugin = mock(Plugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getName()).thenReturn(name);
        when(plugin.getPluginMeta().isFoliaSupported()).thenReturn(folia);
        return plugin;
    }

    private static void busy(final long nanos) {
        final long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }

    @Test
    void nestedListenerTimeIsExclusive() {
        final Plugin outer = plugin("Outer", false);
        final Plugin inner = plugin("Inner", true);
        PluginGate.take();
        final long a = PluginGate.enter();
        busy(2_000_000);
        final long b = PluginGate.enter();
        busy(5_000_000);
        PluginGate.listener(new TestEvent(), inner, b);
        busy(2_000_000);
        PluginGate.listener(new TestEvent(), outer, a);

        final PluginGate.Report report = PluginGate.take();
        long outerNs = -1;
        long innerNs = -1;
        for (final PluginGate.ListenerRow row : report.listeners()) {
            if (row.plugin().equals("Outer")) {
                outerNs = row.nanos();
                assertEquals(false, row.foliaSupported());
            } else if (row.plugin().equals("Inner")) {
                innerNs = row.nanos();
                assertEquals(true, row.foliaSupported());
                assertEquals("async", row.kind(), "a test thread is not a tick thread");
            }
        }
        assertTrue(innerNs >= 5_000_000 && innerNs < 7_000_000, "inner " + innerNs);
        assertTrue(outerNs >= 4_000_000 && outerNs < 6_000_000, "outer excludes the inner listener: " + outerNs);
    }

    @Test
    void histogramPercentile() {
        final long[] counts = new long[64 * 4];
        for (int i = 0; i < 990; ++i) {
            counts[PluginGate.Histogram.index(1_000)]++;
        }
        for (int i = 0; i < 10; ++i) {
            counts[PluginGate.Histogram.index(1_000_000)]++;
        }
        final long p99 = PluginGate.Histogram.percentile(counts, 0.99);
        assertTrue(p99 >= 1_000 && p99 <= 1_300, "p99 " + p99);
        final long p999 = PluginGate.Histogram.percentile(counts, 0.999);
        assertTrue(p999 >= 1_000_000 && p999 <= 1_300_000, "p99.9 " + p999);
        for (final long ns : new long[] {1, 7, 1_000, 123_456, 9_999_999_999L}) {
            assertTrue(PluginGate.Histogram.upperBound(PluginGate.Histogram.index(ns)) >= ns, "bucket bound for " + ns);
        }
    }

    @Test
    void headCostCountsHeadPhasesPlusListenersElsewhereWithoutDoubleCounting() {
        final long[] phases = new long[RegionPhaseTiming.Phase.values().length];
        phases[RegionPhaseTiming.Phase.PACKETS.ordinal()] = 200_000_000L;
        phases[RegionPhaseTiming.Phase.CONNECTIONS.ordinal()] = 100_000_000L;
        phases[RegionPhaseTiming.Phase.TRACKER.ordinal()] = 900_000_000L;
        final List<PluginGate.ListenerRow> listeners = List.of(
            new PluginGate.ListenerRow("packets", "PlayerMoveEvent", "WorldGuard", false, 100, 50_000_000L, 0),
            new PluginGate.ListenerRow("chunk_tick", "BlockGrowEvent", "Factions", false, 10, 20_000_000L, 0),
            new PluginGate.ListenerRow("async", "AsyncChatEvent", "ZelChat", true, 10, 500_000_000L, 0)
        );
        final List<PluginGate.TaskRow> tasks = List.of(
            new PluginGate.TaskRow("SideRealms", "global", true, 20, 40_000_000L, 0),
            new PluginGate.TaskRow("SideRealms", "entity", true, 20, 40_000_000L, 0),
            new PluginGate.TaskRow("LuckPerms", "async", false, 20, 80_000_000L, 0)
        );
        final PluginGate.HeadCost head = PluginGate.headCost(1.0, listeners, tasks, phases, 100, true);
        assertEquals(15.0, head.vanillaMs(), 1e-9);
        assertEquals(1.0, head.listenerMs(), 1e-9);
        assertEquals(2.0, head.schedulerMs(), 1e-9);
        assertEquals(18.0, head.totalMs(), 1e-9);
        assertEquals(180.0, head.perPlayerUs(), 1e-9);
    }

    @Test
    void jsonDumpHasTotals() {
        final PluginGate.Report report = new PluginGate.Report(60.0,
            List.of(new PluginGate.ListenerRow("packets", "PlayerMoveEvent", "WorldGuard", false, 6000, 60_000_000L, 12_000)),
            List.of(new PluginGate.TaskRow("SideRealms", "global", true, 1200, 6_000_000L, 9_000)),
            new PluginGate.Footprint(java.util.Map.of("Citizens:EntityHumanNPC", 3), java.util.Map.of(), java.util.Map.of()),
            new long[RegionPhaseTiming.Phase.values().length], 0L, 500,
            new PluginGate.HeadCost(30, 3, 1, 34, 68, true));
        final String json = PluginGate.toJson(List.of(report, report));
        assertTrue(json.contains("\"seconds\":120.000"), json);
        assertTrue(json.contains("\"event\":\"PlayerMoveEvent\",\"plugin\":\"WorldGuard\",\"foliaSupported\":false,\"calls\":12000,\"nanos\":120000000"), json);
        assertTrue(json.contains("\"plugin\":\"SideRealms\",\"scheduler\":\"global\",\"foliaSupported\":true,\"calls\":2400"), json);
        assertTrue(json.contains("\"Citizens:EntityHumanNPC\":3"), json);
        assertTrue(json.contains("\"headMsPerTick\":34.000"), json);
    }
}
