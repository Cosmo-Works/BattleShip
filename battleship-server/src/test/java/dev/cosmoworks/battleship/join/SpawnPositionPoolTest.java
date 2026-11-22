package dev.cosmoworks.battleship.join;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("Battleship")
class SpawnPositionPoolTest {

    private static final long SECOND = 1_000_000_000L;
    private static final Object LEVEL = new Object();

    @Test
    void emptyPoolMissesWithoutStartingARefill() {
        final SpawnPositionPool pool = new SpawnPositionPool();
        final AtomicInteger refills = new AtomicInteger();
        assertNull(pool.take(LEVEL, 0L, 10 * SECOND, 4, b -> 0, refills::incrementAndGet));
        assertEquals(0, refills.get(), "a miss searches for itself; no background search");
    }

    @Test
    void offeredPositionIsReusedAndStartsOneRefill() {
        final SpawnPositionPool pool = new SpawnPositionPool();
        final AtomicInteger refills = new AtomicInteger();
        final Vec3 a = new Vec3(1, 64, 1);
        pool.offer(LEVEL, a, 0L, 4);
        assertEquals(a, pool.take(LEVEL, SECOND, 10 * SECOND, 4, b -> 0, refills::incrementAndGet));
        assertEquals(a, pool.take(LEVEL, SECOND, 10 * SECOND, 4, b -> 0, refills::incrementAndGet));
        assertEquals(1, refills.get(), "only one background search at a time");
        pool.refilled(LEVEL, new Vec3(2, 64, 2), SECOND, 4);
        pool.take(LEVEL, SECOND, 10 * SECOND, 4, b -> 0, refills::incrementAndGet);
        assertEquals(2, refills.get(), "the next take after a finished search may start another");
    }

    @Test
    void failedRefillAllowsAnother() {
        final SpawnPositionPool pool = new SpawnPositionPool();
        final AtomicInteger refills = new AtomicInteger();
        pool.offer(LEVEL, Vec3.ZERO, 0L, 4);
        pool.take(LEVEL, 0L, 10 * SECOND, 4, b -> 0, refills::incrementAndGet);
        pool.refillFailed(LEVEL);
        pool.take(LEVEL, 0L, 10 * SECOND, 4, b -> 0, refills::incrementAndGet);
        assertEquals(2, refills.get());
    }

    @Test
    void fullPoolDoesNotRefill() {
        final SpawnPositionPool pool = new SpawnPositionPool();
        final AtomicInteger refills = new AtomicInteger();
        pool.offer(LEVEL, new Vec3(1, 0, 0), 0L, 2);
        pool.offer(LEVEL, new Vec3(2, 0, 0), 0L, 2);
        pool.take(LEVEL, 0L, 10 * SECOND, 2, b -> 0, refills::incrementAndGet);
        assertEquals(0, refills.get());
    }

    @Test
    void oldPositionsExpire() {
        final SpawnPositionPool pool = new SpawnPositionPool();
        pool.offer(LEVEL, Vec3.ZERO, 0L, 4);
        assertNull(pool.take(LEVEL, 11 * SECOND, 10 * SECOND, 4, b -> 0, () -> {}));
        assertTrue(pool.positions(LEVEL).isEmpty());
    }

    @Test
    void sizeKeepsNewestAndTakeSpreadsOverAll() {
        final SpawnPositionPool pool = new SpawnPositionPool();
        for (int i = 0; i < 5; ++i) {
            pool.offer(LEVEL, new Vec3(i, 0, 0), i, 3);
        }
        final List<Vec3> held = pool.positions(LEVEL);
        assertEquals(List.of(new Vec3(2, 0, 0), new Vec3(3, 0, 0), new Vec3(4, 0, 0)), held);
        final Set<Vec3> seen = new HashSet<>();
        for (int i = 0; i < 3; ++i) {
            final int index = i;
            seen.add(pool.take(LEVEL, 10L, 10 * SECOND, 3, b -> index % b, () -> {}));
        }
        assertEquals(new HashSet<>(held), seen);
    }

    @Test
    void levelsAreSeparate() {
        final SpawnPositionPool pool = new SpawnPositionPool();
        pool.offer(LEVEL, Vec3.ZERO, 0L, 4);
        assertNull(pool.take(new Object(), 0L, 10 * SECOND, 4, b -> 0, () -> {}));
    }
}
