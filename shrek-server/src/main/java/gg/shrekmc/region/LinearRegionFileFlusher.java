package gg.shrekmc.region;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import gg.shrekmc.ShrekServerConfig;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinearRegionFileFlusher {

    private static final Logger LOGGER = LoggerFactory.getLogger("Shrek-Linear");

    private final Queue<LinearRegionFile> savingQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService scheduler;
    private final ExecutorService executor;

    public LinearRegionFileFlusher() {
        int flushThreads = ShrekServerConfig.flushMaxThreads();
        final int flushFrequency = Math.max(ShrekServerConfig.flushFrequency(), 1);

        if (flushThreads < 0) {
            flushThreads = Math.max(Runtime.getRuntime().availableProcessors() + flushThreads, 1);
        } else {
            flushThreads = Math.max(flushThreads, 1);
        }

        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("shrek-linear-flush-scheduler").build()
        );
        this.executor = Executors.newFixedThreadPool(
            flushThreads,
            new ThreadFactoryBuilder().setNameFormat("shrek-linear-flusher-%d").build()
        );

        LOGGER.info("Using {} threads for linear region flushing", flushThreads);
        this.scheduler.scheduleAtFixedRate(this::pollAndFlush, 0L, flushFrequency, TimeUnit.SECONDS);
    }

    public void scheduleSave(final LinearRegionFile regionFile) {
        if (this.savingQueue.contains(regionFile)) {
            return;
        }
        this.savingQueue.add(regionFile);
    }

    private void pollAndFlush() {
        while (!this.savingQueue.isEmpty()) {
            final LinearRegionFile regionFile = this.savingQueue.poll();
            if (regionFile != null && !regionFile.closed && regionFile.isMarkedToSave()) {
                this.executor.execute(regionFile::flushWrapper);
            }
        }
    }

    public void shutdown() {
        this.executor.shutdown();
        this.scheduler.shutdown();
    }
}
