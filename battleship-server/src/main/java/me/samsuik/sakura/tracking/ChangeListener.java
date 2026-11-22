package me.samsuik.sakura.tracking;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ChangeListener<T extends Watchable<?>> {
    void changed(T watchable);

    default void removed(final T watchable) {}
}
