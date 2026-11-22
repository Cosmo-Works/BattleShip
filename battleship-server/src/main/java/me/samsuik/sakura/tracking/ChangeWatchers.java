package me.samsuik.sakura.tracking;

import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ChangeWatchers<T extends Watchable<T>> {
    private final ReferenceArraySet<ChangeListener<T>> listeners = new ReferenceArraySet<>();

    public void add(final ChangeListener<T> listener) {
        this.listeners.add(listener);
    }

    public void remove(final ChangeListener<T> listener) {
        this.listeners.remove(listener);
    }

    public void changed(final T watchableRef) {
        for (final ChangeListener<T> listener : this.listeners) {
            listener.changed(watchableRef);
        }
    }

    public void removed(final T watchableRef) {
        for (final ChangeListener<T> listener : this.listeners) {
            listener.removed(watchableRef);
        }
    }
}
