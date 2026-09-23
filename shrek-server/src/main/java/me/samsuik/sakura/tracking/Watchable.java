package me.samsuik.sakura.tracking;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface Watchable<T extends Watchable<T>> {
    void watch(ChangeListener<T> listener);

    void unwatch(ChangeListener<T> listener);
}
