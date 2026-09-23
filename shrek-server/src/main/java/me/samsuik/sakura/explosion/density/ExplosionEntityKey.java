// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.explosion.density;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ExplosionEntityKey {
    public static int encode(final Vec3 center, final Entity entity) {
        return encode(center, entity.blockPosition(), entity.getType());
    }

    public static int encode(final Vec3 center, final BlockPos entityBlockPos, final EntityType<?> type) {
        int key        = Mth.floor(center.x());
        key = 31 * key + Mth.floor(center.y());
        key = 31 * key + Mth.floor(center.z());
        key = 31 * key + entityBlockPos.getX();
        key = 31 * key + entityBlockPos.getY();
        key = 31 * key + entityBlockPos.getZ();
        key = 31 * key + type.hashCode();
        return key;
    }
}
