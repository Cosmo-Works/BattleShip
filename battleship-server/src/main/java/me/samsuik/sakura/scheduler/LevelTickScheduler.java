package me.samsuik.sakura.scheduler;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class LevelTickScheduler {
    private static final long UNLOCATED_CHUNK = Long.MIN_VALUE;

    private final Long2ObjectOpenHashMap<ScheduledTaskBucket> scheduledTaskBuckets = new Long2ObjectOpenHashMap<>();
    private final Reference2LongOpenHashMap<TickTask> tickTasks = new Reference2LongOpenHashMap<>();
    private final Deque<TickTask> removeLater = new ArrayDeque<>();
    private long schedulerTick;

    public LevelTickScheduler() {
        this.tickTasks.defaultReturnValue(Long.MAX_VALUE);
    }

    public void tick(final ServerLevel serverLevel) {
        this.drainRemovals();

        final long schedulerTick = this.schedulerTick++;
        final List<ScheduledTickTask> reschedule = new ObjectArrayList<>();
        final Iterator<ScheduledTaskBucket> bucketIterator = this.scheduledTaskBuckets.values().iterator();
        while (bucketIterator.hasNext()) {
            final ScheduledTaskBucket bucket = bucketIterator.next();
            if (bucket.scheduledTick > schedulerTick) {
                continue;
            }

            bucketIterator.remove();
            for (final ScheduledTickTask scheduledTask : bucket.tasksToRun) {
                if (scheduledTask.tick(serverLevel, schedulerTick)) {
                    reschedule.add(scheduledTask);
                } else {
                    this.tickTasks.removeLong(scheduledTask.task);
                }
            }
        }

        reschedule.forEach(this::addTask);
    }

    public void run(final Runnable task) {
        this.run(TickTask.wrap(task));
    }

    public void run(final TickTask task) {
        this.addTask(ScheduledTickTask.once(task, 0));
    }

    public void runLater(final Runnable task, final int delay) {
        this.runLater(TickTask.wrap(task), delay);
    }

    public void runLater(final TickTask task, final int delay) {
        this.addTask(ScheduledTickTask.once(task, delay));
    }

    public void runLater(final BlockPos pos, final TickTask task, final int delay) {
        this.addTask(ScheduledTickTask.once(task, delay, ChunkPos.pack(pos)));
    }

    public void repeat(final TickTaskWithCallback task, final int delay, final int count) {
        this.addTask(ScheduledTickTask.repeat(task, delay, count));
    }

    public void endless(final TickTask task, final int delay) {
        this.addTask(ScheduledTickTask.endless(task, delay));
    }

    public void endless(final TickTaskWithCallback task, final int delay) {
        this.addTask(ScheduledTickTask.endless(task, delay));
    }

    public void remove(final TickTask task) {
        this.removeLater.add(task);
    }

    public void mergeFrom(final LevelTickScheduler from) {
        from.drainRemovals();
        this.drainRemovals();

        for (final ScheduledTaskBucket bucket : from.scheduledTaskBuckets.values()) {
            final int remaining = remainingDelay(bucket.scheduledTick, from.schedulerTick);
            for (final ScheduledTickTask scheduledTask : bucket.tasksToRun) {
                this.addTask(scheduledTask, remaining);
            }
        }

        from.clear();
    }

    public void splitInto(
        final int chunkToRegionShift,
        final Long2ReferenceOpenHashMap<RegionizedWorldData> regionToData,
        final ReferenceOpenHashSet<RegionizedWorldData> dataSet
    ) {
        this.drainRemovals();

        final RegionizedWorldData unlocated = firstRegion(dataSet);
        for (final ScheduledTaskBucket bucket : this.scheduledTaskBuckets.values()) {
            final int remaining = remainingDelay(bucket.scheduledTick, this.schedulerTick);
            for (final ScheduledTickTask scheduledTask : bucket.tasksToRun) {
                final RegionizedWorldData into = regionForTask(scheduledTask, chunkToRegionShift, regionToData, unlocated);
                if (into != null) {
                    into.battleshipTickScheduler.addTask(scheduledTask, remaining);
                }
            }
        }

        this.clear();
    }

    private void addTask(final ScheduledTickTask task) {
        this.addTask(task, task.delay);
    }

    private void addTask(final ScheduledTickTask task, final int remaining) {
        final long executionTick = this.schedulerTick + remaining;
        this.scheduledTaskBuckets.computeIfAbsent(executionTick, ScheduledTaskBucket::new)
            .addTask(task);
        this.tickTasks.put(task.task, executionTick);
    }

    private void drainRemovals() {
        TickTask taskToRemove;
        while ((taskToRemove = this.removeLater.poll()) != null) {
            this.removeTask(taskToRemove);
        }
    }

    private void removeTask(final TickTask task) {
        final long scheduledTick = this.tickTasks.removeLong(task);
        final ScheduledTaskBucket bucket = this.scheduledTaskBuckets.get(scheduledTick);
        if (bucket != null) {
            bucket.removeTask(task);
        }
    }

    private void clear() {
        this.scheduledTaskBuckets.clear();
        this.tickTasks.clear();
        this.removeLater.clear();
    }

    private static int remainingDelay(final long scheduledTick, final long schedulerTick) {
        return (int) Math.max(0L, scheduledTick - schedulerTick);
    }

    private static @Nullable RegionizedWorldData regionForTask(
        final ScheduledTickTask scheduledTask,
        final int chunkToRegionShift,
        final Long2ReferenceOpenHashMap<RegionizedWorldData> regionToData,
        final @Nullable RegionizedWorldData unlocated
    ) {
        if (scheduledTask.chunkKey == UNLOCATED_CHUNK) {
            return unlocated;
        }

        final int chunkX = ChunkPos.getX(scheduledTask.chunkKey);
        final int chunkZ = ChunkPos.getZ(scheduledTask.chunkKey);
        return regionToData.get(CoordinateUtils.getChunkKey(chunkX >> chunkToRegionShift, chunkZ >> chunkToRegionShift));
    }

    private static @Nullable RegionizedWorldData firstRegion(final ReferenceOpenHashSet<RegionizedWorldData> dataSet) {
        final Iterator<RegionizedWorldData> iterator = dataSet.iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    private static final class ScheduledTaskBucket {
        private final List<ScheduledTickTask> tasksToRun = new ObjectArrayList<>();
        private final long scheduledTick;

        private ScheduledTaskBucket(final long scheduledTick) {
            this.scheduledTick = scheduledTick;
        }

        public void addTask(final ScheduledTickTask task) {
            this.tasksToRun.add(task);
        }

        public void removeTask(final TickTask task) {
            this.tasksToRun.removeIf(scheduledTask -> scheduledTask.task == task);
        }
    }

    private static final class ScheduledTickTask {
        private final TickTask task;
        private final int delay;
        private final long chunkKey;
        private int count;

        public static ScheduledTickTask once(final TickTask task, final int delay) {
            return once(task, delay, UNLOCATED_CHUNK);
        }

        public static ScheduledTickTask once(final TickTask task, final int delay, final long chunkKey) {
            return new ScheduledTickTask(task, delay, 0, chunkKey);
        }

        public static ScheduledTickTask repeat(final TickTask task, final int delay, final int count) {
            return new ScheduledTickTask(task, delay, count, UNLOCATED_CHUNK);
        }

        public static ScheduledTickTask endless(final TickTask task, final int delay) {
            return new ScheduledTickTask(task, delay, Integer.MAX_VALUE, UNLOCATED_CHUNK);
        }

        private ScheduledTickTask(final TickTask task, final int delay, final int count, final long chunkKey) {
            this.task = task;
            this.delay = delay;
            this.count = count;
            this.chunkKey = chunkKey;
        }

        public boolean tick(final ServerLevel serverLevel, final long schedulerTick) {
            if (task instanceof final TickTaskWithCallback tickTaskWithCallback) {
                final SchedulerCallback callback = new SchedulerCallback();
                tickTaskWithCallback.run(schedulerTick, serverLevel, callback);

                if (callback.isCancelled()) {
                    this.count = 0;
                }
            } else {
                task.run(schedulerTick, serverLevel);
            }

            return --this.count > 0;
        }
    }
}
