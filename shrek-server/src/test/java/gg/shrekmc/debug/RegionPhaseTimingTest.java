package gg.shrekmc.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("Shrek")
class RegionPhaseTimingTest {
    private static RegionPhaseTiming.Stats region(final String name, final long totalMs, final int players) {
        final RegionPhaseTiming.Stats stats = new RegionPhaseTiming.Stats(name);
        stats.ticks.add(20);
        stats.total.add(totalMs * 1_000_000L);
        stats.windowMax = 90_000_000L;
        stats.players = players;
        return stats;
    }

    @Test
    void reportOrdersRegionsAndPhasesAndComputesOther() {
        final RegionPhaseTiming.Stats spawn = region("world #1 @[0, 0]", 1000, 400);
        spawn.phases[RegionPhaseTiming.Phase.TRACKER.ordinal()].add(400_000_000L);
        spawn.phases[RegionPhaseTiming.Phase.PACKETS.ordinal()].add(300_000_000L);
        spawn.phases[RegionPhaseTiming.Phase.HELPER_WAIT.ordinal()].add(100_000_000L);
        spawn.packets.put(String.class, new java.util.concurrent.atomic.LongAdder[] {adder(200_000_000L), adder(2000)});
        final RegionPhaseTiming.Stats base = region("world #2 @[300, 300]", 100, 3);
        base.phases[RegionPhaseTiming.Phase.ENTITY_MOBS.ordinal()].add(50_000_000L);
        final RegionPhaseTiming.Stats idle = region("world #3 @[900, 900]", 10, 0);

        final List<String> lines = RegionPhaseTiming.report(10.0, 2, List.of(base, idle, spawn));
        assertTrue(lines.getFirst().contains("3 regions ticked; top 2"), lines.getFirst());
        assertTrue(lines.get(1).startsWith("  world #1"), "busiest first: " + lines.get(1));
        assertTrue(lines.get(1).contains("400 players") && lines.get(1).contains("50.0 ms mean") && lines.get(1).contains("busy 10%"), lines.get(1));
        assertTrue(lines.get(2).startsWith("    tracker 40.0%, packets 30.0%, helper_wait 10.0%"), lines.get(2));
        assertTrue(lines.get(2).endsWith("other 30.0%"), lines.get(2));
        assertTrue(lines.get(3).contains("String 20.0% (200/s, 100.0 us each)"), lines.get(3));
        assertTrue(lines.get(4).startsWith("  world #2"), lines.get(4));
        assertEquals(6, lines.size(), String.join("\n", lines));

        assertEquals(0L, spawn.total.sum());
    }

    private static java.util.concurrent.atomic.LongAdder adder(final long value) {
        final java.util.concurrent.atomic.LongAdder adder = new java.util.concurrent.atomic.LongAdder();
        adder.add(value);
        return adder;
    }
}
