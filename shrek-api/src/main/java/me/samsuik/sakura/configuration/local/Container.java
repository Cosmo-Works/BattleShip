package me.samsuik.sakura.configuration.local;

import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface Container<K, V> {
    Map<K, V> contents();

    Container<K, V> open();

    Container<K, V> seal();
}
