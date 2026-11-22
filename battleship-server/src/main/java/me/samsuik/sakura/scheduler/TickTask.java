package me.samsuik.sakura.scheduler;

import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface TickTask {
    static TickTask wrap(final Runnable task) {
        return (tick, level) -> task.run();
    }

    void run(long tick, ServerLevel level);
}
