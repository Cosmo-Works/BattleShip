package gg.shrekmc.parallel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.papermc.paper.threadedregions.RegionizedWorldData;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

@AllFeatures
class RegionParallelTest {
    @Test
    @SuppressWarnings("unchecked")
    void helpersActForTheCallingRegionAndTheCallerWaits() throws Exception {
        final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region = mock(ThreadedRegionizer.ThreadedRegion.class);
        final RegionizedWorldData worldData = mock(RegionizedWorldData.class);
        final int batches = 64;
        final AtomicInteger ran = new AtomicInteger();
        final Set<Thread> threads = ConcurrentHashMap.newKeySet();
        final AtomicInteger wrongContext = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final TickRegionScheduler.TickThreadRunner regionThread = new TickRegionScheduler.TickThreadRunner(null, () -> {
            try {
                final TickRegionScheduler.TickThreadRunner self = (TickRegionScheduler.TickThreadRunner) Thread.currentThread();
                self.currentTickingRegion = region;
                self.currentTickingWorldRegionizedData = worldData;
                RegionParallel.run(batches, batch -> {
                    threads.add(Thread.currentThread());
                    if (TickRegionScheduler.getCurrentRegion() != region
                        || TickRegionScheduler.getCurrentRegionizedWorldData() != worldData
                        || !RegionParallel.inPhase()) {
                        wrongContext.incrementAndGet();
                    }
                    try {
                        Thread.sleep(1);
                    } catch (final InterruptedException ignored) {
                    }
                    ran.incrementAndGet();
                });

                assertEquals(batches, ran.get());
                assertFalse(RegionParallel.inPhase());
            } catch (final Throwable thrown) {
                failure.set(thrown);
            }
        }, "test region thread");
        regionThread.start();
        regionThread.join();

        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        assertEquals(0, wrongContext.get());
        assertTrue(threads.size() > 1, "helpers should take part: " + threads.size());
        assertTrue(threads.contains(regionThread));
    }

    @Test
    void outsideARegionEverythingRunsInline() {
        final AtomicInteger ran = new AtomicInteger();
        final Thread caller = Thread.currentThread();
        RegionParallel.run(10, batch -> {
            assertSame(caller, Thread.currentThread());
            ran.incrementAndGet();
        });
        assertEquals(10, ran.get());
    }
}
