package me.samsuik.sakura.tracking.watchers;

import me.samsuik.sakura.tracking.ChangeListener;
import me.samsuik.sakura.tracking.Watchable;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class AnyChangeWatcher<T extends Watchable<T>> implements ChangeListener<T> {
    private final T watchable;
    private boolean changed = true;

    public AnyChangeWatcher(final T watchable) {
        this.watchable = watchable;
        this.watchable.watch(this);
    }

    @Override
    public void changed(final T watchable) {
        this.changed = true;
    }

    @Override
    public void removed(final T watchable) {
        this.watchable.unwatch(this);
        this.changed = true;
    }

    public boolean hasChanged() {
        final boolean hasChanges = this.changed;
        this.changed = false;
        return hasChanges;
    }
}
