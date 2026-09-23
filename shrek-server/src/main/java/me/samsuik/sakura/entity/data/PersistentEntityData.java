package me.samsuik.sakura.entity.data;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PersistentEntityData {
    public final LongOpenHashSet linkedPositions = new LongOpenHashSet();
    private long totalTicksPast;
    private int ticksSinceLastAccess;

    public double chanceToSpawnEntity() {
        final double minutePercentile = this.totalTicksPast / 1200.0;
        return 0.95 + Math.min(minutePercentile / 20.0, 0.05);
    }

    public boolean haveTicksPast(final int ticksPast) {
        return this.totalTicksPast >= ticksPast;
    }

    public void addLinkedPositions(final LongOpenHashSet linkedPositions) {
        this.linkedPositions.addAll(linkedPositions);
    }

    public void load(final PersistentEntityData persistentEntityData) {
        if (this.totalTicksPast >= 100) {
            this.linkedPositions.retainAll(persistentEntityData.linkedPositions);
        } else {
            this.linkedPositions.addAll(persistentEntityData.linkedPositions);
        }

        this.ticksSinceLastAccess = 0;
    }

    public boolean expire(final int ticksPast) {
        this.totalTicksPast += ticksPast;
        this.ticksSinceLastAccess += ticksPast;
        return this.ticksSinceLastAccess >= 200;
    }
}
