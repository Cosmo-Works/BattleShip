package me.samsuik.sakura.scheduler;

public final class SchedulerCallback {
    private boolean cancelled = false;

    public boolean isCancelled() {
        return this.cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }
}
