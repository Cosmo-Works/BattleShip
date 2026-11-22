package me.samsuik.sakura.world;

import org.jspecify.annotations.NullMarked;

@SuppressWarnings("FieldMayBeFinal")
@NullMarked
public class KeepLoadedArea {
    public int x = 0;
    public int z = 0;
    public int size = 0;

    public KeepLoadedArea() {
    }

    public KeepLoadedArea(final int x, final int z, final int size) {
        this.x = x;
        this.z = z;
        this.size = size;
    }
}
