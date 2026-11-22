package me.samsuik.sakura.tps;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TickInformationCollector {
    private static final int TEN_MINUTES = 10 * 60;
    private final ObjectArrayList<ServerTickInformation> collectedInformation = new ObjectArrayList<>();
    private final LongArrayList tickSamples = new LongArrayList();
    private long identifier = 0;

    public TickInformationCollector() {
        this.collectedInformation.add(ServerTickInformation.UNKNOWN);
    }

    public ServerTickInformation latestTickInformation() {
        return this.collectedInformation.getLast();
    }

    public void regionData(final int chunks, final int entities, final float targetTickRate) {
        final double averageTick = this.tickSamples.longStream()
            .average()
            .orElse(0.0);
        final long longestTick = this.tickSamples.longStream()
            .max()
            .orElse(0);
        final double tps = averageTick <= 0.0
            ? targetTickRate
            : Math.min(targetTickRate, 1000.0 / averageTick);
        final ServerTickInformation tickInformation = new ServerTickInformation(
            this.identifier++, tps, averageTick, longestTick, targetTickRate, chunks, entities
        );

        this.collectedInformation.add(tickInformation);
        this.tickSamples.clear();

        if (this.collectedInformation.size() > TEN_MINUTES) {
            this.collectedInformation.subList(0, 60).clear();
        }
    }

    public void tickDuration(final long timeTaken) {
        this.tickSamples.add(timeTaken);
    }

    public boolean hasFullSecond() {
        return this.tickSamples.size() >= 20;
    }

    public ObjectImmutableList<ServerTickInformation> collect(final long from, final long to) {
        final List<ServerTickInformation> collected = new ObjectArrayList<>();
        for (final ServerTickInformation tickInformation : this.collectedInformation.reversed()) {
            if (tickInformation.identifier() >= from && tickInformation.identifier() < to) {
                collected.add(tickInformation);
            }
        }

        final long ahead = to - this.identifier;
        final long missing = to - from - collected.size();
        for (int remaining = 0; remaining < missing; ++remaining) {
            final int index = (remaining < ahead) ? 0 : collected.size();
            collected.add(index, ServerTickInformation.UNKNOWN);
        }

        return new ObjectImmutableList<>(collected);
    }
}
