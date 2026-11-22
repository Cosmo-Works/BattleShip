package me.samsuik.sakura.scheduler;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ScheduleSakuraTasks {
    public static void schedule(final LevelTickScheduler scheduler) {
        scheduler.endless((tick, level) -> {
            final long gameTime = level.getGameTime();
            level.entityManager().tick(gameTime);
            level.obstructionCache().tick(gameTime);
            level.dispenseRelocationHandler().clear();
            level.fallingBlockColumnLimits().clear();
        }, 0);
    }

    private ScheduleSakuraTasks() {
    }
}
