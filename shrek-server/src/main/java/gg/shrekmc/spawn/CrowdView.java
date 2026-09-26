package gg.shrekmc.spawn;

import ca.spottedleaf.moonrise.common.util.TickThread;
import gg.shrekmc.ShrekServerConfig;
import gg.shrekmc.tracker.ShrekTracker;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class CrowdView {
    private final CrowdSelection selection = new CrowdSelection();

    private final Int2ObjectOpenHashMap<ServerPlayer> candidates = new Int2ObjectOpenHashMap<>();

    private final ReferenceOpenHashSet<ServerPlayer> tracked = new ReferenceOpenHashSet<>();
    private long tick = Long.MIN_VALUE;
    private long lastRebuild = Long.MIN_VALUE;

    private int cap;
    private @Nullable Level level;

    private volatile int peakTracked;
    private volatile int priorityCount;

    void onPaired(final ServerPlayer other) {
        this.tracked.add(other);
        final int size = this.tracked.size();
        if (size > this.peakTracked) {
            this.peakTracked = size;
            ShrekSpawnCrowd.recordPeak(size);
        }
    }

    void onUnpaired(final ServerPlayer other) {
        this.tracked.remove(other);
    }

    boolean canSee(final ServerPlayer viewer, final ServerPlayer other, final long tick, final boolean currentlySeen) {
        if (tick != this.tick) {
            this.beginTick(viewer, tick);
        }
        final int cap = this.cap;
        if (cap <= 0) {
            return true;
        }
        final int id = other.getId();
        final boolean priority = ShrekSpawnCrowd.alwaysVisible(viewer, other);
        this.candidates.put(id, other);
        this.selection.offer(id, priority ? 0.0 : other.distanceToSqr(viewer), tick);

        if (!this.selection.isSelected(id)) {
            if (priority) {
                final int dropped = this.selection.forceSelect(id, cap);
                if (dropped != Integer.MIN_VALUE) {
                    this.untrack(viewer, dropped);
                }
            } else if (currentlySeen || !this.selection.admit(id, cap)) {
                return false;
            }
        }

        return currentlySeen || this.tracked.size() < cap || this.makeRoom(viewer);
    }

    void prepare(final ServerPlayer viewer, final long tick) {
        if (tick != this.tick) {
            this.beginTick(viewer, tick);
        }
    }

    boolean hiddenByCap(final ServerPlayer viewer, final ServerPlayer other, final long tick) {
        if (tick != this.tick) {
            this.beginTick(viewer, tick);
        }
        final int cap = this.cap;
        if (cap <= 0) {
            return false;
        }
        return !this.selection.isSelected(other.getId())
            && this.selection.selectedCount() >= cap
            && !ShrekSpawnCrowd.alwaysVisible(viewer, other);
    }

    void prepareDeferred(final ServerPlayer viewer, final long tick) {
        if (tick != this.tick) {
            this.deferDrops = true;
            try {
                this.beginTick(viewer, tick);
            } finally {
                this.deferDrops = false;
            }
        }
    }

    void applyDeferred(final ServerPlayer viewer) {
        final List<ServerPlayer> drop = this.deferredDrops;
        if (drop == null) {
            return;
        }
        this.deferredDrops = null;
        for (final ServerPlayer other : drop) {
            if (this.tracked.contains(other)) {
                this.untrack(viewer, other);
            }
        }
    }

    private boolean deferDrops;
    private @Nullable List<ServerPlayer> deferredDrops;

    private void beginTick(final ServerPlayer viewer, final long tick) {
        this.tick = tick;

        final int cap = ShrekSpawnCrowd.INSTANCE.capFor(viewer);
        final Level level = viewer.level();
        final boolean becameCapped = cap > 0 && (this.cap <= 0 || level != this.level);
        if (cap <= 0 || level != this.level) {
            this.selection.clear();
            this.candidates.clear();
            this.lastRebuild = Long.MIN_VALUE;
        }
        this.level = level;
        this.cap = Math.max(cap, 0);
        if (cap <= 0) {
            return;
        }
        if (tick < this.lastRebuild) {
            this.lastRebuild = Long.MIN_VALUE;
        }
        if (becameCapped) {
            for (final ServerPlayer other : this.tracked) {
                if (TickThread.isTickThreadFor(other)) {
                    this.candidates.put(other.getId(), other);
                    this.selection.offer(other.getId(), other.distanceToSqr(viewer), tick);
                }
            }
        }
        final ShrekServerConfig.SpawnCrowd config = ShrekServerConfig.spawnCrowd();
        if (becameCapped || this.lastRebuild == Long.MIN_VALUE || tick - this.lastRebuild >= Math.max(1, config.reselectInterval)) {
            this.rebuild(viewer, cap, config.swapDistance, tick);
        }
    }

    private void rebuild(final ServerPlayer viewer, final int cap, final double swapDistance, final long tick) {
        final long oldest = tick - ShrekTracker.fullUpdateFrequency() - 1;

        for (final int id : this.selection.recentCandidates(oldest)) {
            final ServerPlayer other = this.candidates.get(id);
            if (other == null || other.isRemoved() || !TickThread.isTickThreadFor(other) || other.level() != viewer.level()) {
                this.selection.forget(id);
                continue;
            }
            final boolean priority = ShrekSpawnCrowd.alwaysVisible(viewer, other);
            this.selection.updateDistance(id, priority ? 0.0 : other.distanceToSqr(viewer));
        }
        this.selection.rebuild(cap, swapDistance, oldest);
        this.candidates.keySet().removeIf(id -> !this.selection.isCandidate(id));
        this.lastRebuild = tick;

        int priorities = 0;
        List<ServerPlayer> drop = null;
        for (final ServerPlayer other : this.tracked) {
            if (!this.selection.isSelected(other.getId())) {
                if (drop == null) {
                    drop = new ArrayList<>();
                }
                drop.add(other);
            } else if (ShrekSpawnCrowd.alwaysVisible(viewer, other)) {
                ++priorities;
            }
        }
        this.priorityCount = priorities;
        if (drop != null && this.deferDrops) {
            this.deferredDrops = drop;
            return;
        }
        if (drop != null) {
            for (final ServerPlayer other : drop) {
                this.untrack(viewer, other);
            }
        }
    }

    private boolean makeRoom(final ServerPlayer viewer) {
        for (final ServerPlayer other : this.tracked) {
            if (!this.selection.isSelected(other.getId())) {
                this.untrack(viewer, other);
                return this.tracked.size() < this.cap;
            }
        }
        return false;
    }

    private void untrack(final ServerPlayer viewer, final int id) {
        final ServerPlayer other = this.candidates.get(id);
        if (other != null && this.tracked.contains(other)) {
            this.untrack(viewer, other);
        }
    }

    private void untrack(final ServerPlayer viewer, final ServerPlayer other) {
        final ChunkMap.TrackedEntity trackedEntity = other.moonrise$getTrackedEntity();
        if (trackedEntity != null && TickThread.isTickThreadFor(other)) {
            trackedEntity.removePlayer(viewer);
        }

        this.tracked.remove(other);
    }

    int trackedCount() {
        return this.tracked.size();
    }

    int peakTracked() {
        return this.peakTracked;
    }

    int priorityCount() {
        return this.priorityCount;
    }

    int cap() {
        return this.cap;
    }

    int selectedCount() {
        return this.selection.selectedCount();
    }
}
