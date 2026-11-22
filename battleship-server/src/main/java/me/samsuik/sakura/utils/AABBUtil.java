// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.utils;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class AABBUtil {
    public static boolean containsInclusive(final AABB box, final AABB otherBox) {
        return box.minX <= otherBox.minX && box.maxX >= otherBox.maxX
            && box.minY <= otherBox.minY && box.maxY >= otherBox.maxY
            && box.minZ <= otherBox.minZ && box.maxZ >= otherBox.maxZ;
    }

    public static boolean containsInclusive(final AABB box, final Vec3 position) {
        return box.minX <= position.x && box.maxX >= position.x
            && box.minY <= position.y && box.maxY >= position.y
            && box.minZ <= position.z && box.maxZ >= position.z;
    }
    
    public static AABB expand(final AABB box, final Vec3 position) {
        final double minX = Math.min(box.minX, position.x);
        final double minY = Math.min(box.minY, position.y);
        final double minZ = Math.min(box.minZ, position.z);
        final double maxX = Math.max(box.maxX, position.x);
        final double maxY = Math.max(box.maxY, position.y);
        final double maxZ = Math.max(box.maxZ, position.z);
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
