package dev.cosmoworks.battleship.parallel;

import dev.cosmoworks.battleship.BattleshipServerConfig;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntConsumer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public final class RegionParallel {
    private static final Logger LOGGER = LoggerFactory.getLogger("BattleshipRegionParallel");
    private static final ThreadLocal<Boolean> IN_PHASE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final AtomicBoolean WARNED_EVENT = new AtomicBoolean();

    private static volatile @Nullable ThreadPoolExecutor pool;
    private static int poolSize;

    private RegionParallel() {
    }

    public static boolean inPhase() {
        return IN_PHASE.get();
    }

    public static void checkEventOutsidePhase(final Object event) {
        if (inPhase() && WARNED_EVENT.compareAndSet(false, true)) {
            LOGGER.error("Event {} was fired inside a parallel phase; please report this", event.getClass().getName(), new Throwable());
        }
    }

    public static int helperThreads() {
        final int configured = BattleshipServerConfig.parallel().helperThreads;
        if (configured > 0) {
            return configured;
        }
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 4));
    }

    private static ThreadPoolExecutor pool() {
        final int size = helperThreads();
        ThreadPoolExecutor current = pool;
        if (current != null && poolSize == size) {
            return current;
        }
        synchronized (RegionParallel.class) {
            current = pool;
            if (current != null && poolSize == size) {
                return current;
            }
            final ThreadPoolExecutor created = new ThreadPoolExecutor(
                size, size, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                runnable -> {
                    final TickRegionScheduler.TickThreadRunner thread = new TickRegionScheduler.TickThreadRunner(
                        null, runnable, "Battleship Region Helper #" + THREAD_IDS.incrementAndGet()
                    );
                    thread.setDaemon(true);
                    return thread;
                }
            );
            created.allowCoreThreadTimeOut(true);
            if (current != null) {
                current.shutdown();
            }
            poolSize = size;
            pool = created;
            return created;
        }
    }

    public static void run(final int batches, final IntConsumer body) {
        if (batches <= 0) {
            return;
        }
        final Thread caller = Thread.currentThread();
        if (batches == 1 || inPhase() || BattleshipServerConfig.parallel().debugSingleThreaded
            || !(caller instanceof TickRegionScheduler.TickThreadRunner owner) || owner.currentTickingRegion == null) {
            runInline(batches, body);
            return;
        }

        final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region = owner.currentTickingRegion;
        final RegionizedWorldData worldData = owner.currentTickingWorldRegionizedData;
        final ca.spottedleaf.concurrentutil.scheduler.SchedulableTick task = owner.currentTickingTask;
        final Phase phase = new Phase(batches, body, caller);

        final Runnable helper = () -> {
            final TickRegionScheduler.TickThreadRunner self = (TickRegionScheduler.TickThreadRunner) Thread.currentThread();
            self.currentTickingRegion = region;
            self.currentTickingWorldRegionizedData = worldData;
            self.currentTickingTask = task;
            IN_PHASE.set(Boolean.TRUE);
            try {
                phase.work();
            } finally {
                IN_PHASE.set(Boolean.FALSE);
                self.currentTickingRegion = null;
                self.currentTickingWorldRegionizedData = null;
                self.currentTickingTask = null;
            }
        };
        final ThreadPoolExecutor executor = pool();
        final int helpers = Math.min(poolSize, batches - 1);
        for (int i = 0; i < helpers; ++i) {
            executor.execute(helper);
        }

        IN_PHASE.set(Boolean.TRUE);
        try {
            phase.work();
        } finally {
            IN_PHASE.set(Boolean.FALSE);
        }
        final long battleship$waitStart = dev.cosmoworks.battleship.debug.RegionPhaseTiming.start(); // region phase timing
        phase.await();
        dev.cosmoworks.battleship.debug.RegionPhaseTiming.end(dev.cosmoworks.battleship.debug.RegionPhaseTiming.Phase.HELPER_WAIT, battleship$waitStart);
    }

    private static void runInline(final int batches, final IntConsumer body) {
        final boolean nested = inPhase();
        IN_PHASE.set(Boolean.TRUE);
        try {
            for (int i = 0; i < batches; ++i) {
                try {
                    body.accept(i);
                } catch (final Throwable thrown) {
                    LOGGER.error("Error in a parallel phase batch", thrown);
                }
            }
        } finally {
            IN_PHASE.set(nested);
        }
    }

    public static int batchesFor(final int count, final int minPerBatch) {
        return Math.max(1, Math.min(helperThreads() + 1, count / Math.max(1, minPerBatch)));
    }

    private static final class Phase {
        private final int batches;
        private final IntConsumer body;
        private final Thread caller;
        private final AtomicInteger next = new AtomicInteger();
        private final AtomicInteger done = new AtomicInteger();

        private Phase(final int batches, final IntConsumer body, final Thread caller) {
            this.batches = batches;
            this.body = body;
            this.caller = caller;
        }

        void work() {
            int batch;
            while ((batch = this.next.getAndIncrement()) < this.batches) {
                try {
                    this.body.accept(batch);
                } catch (final Throwable thrown) {
                    LOGGER.error("Error in a parallel phase batch", thrown);
                }
                if (this.done.incrementAndGet() == this.batches) {
                    LockSupport.unpark(this.caller);
                }
            }
        }

        void await() {
            while (this.done.get() < this.batches) {
                LockSupport.parkNanos(this, 100_000L);
            }
        }
    }
}
