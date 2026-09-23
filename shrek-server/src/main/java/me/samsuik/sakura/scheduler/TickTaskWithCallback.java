package me.samsuik.sakura.scheduler;

import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface TickTaskWithCallback extends TickTask {
    @Override
    @Deprecated
    default void run(final long tick, final ServerLevel level) {
        throw new UnsupportedOperationException();
    }

    void run(long tick, ServerLevel serverLevel, SchedulerCallback callback);
}
