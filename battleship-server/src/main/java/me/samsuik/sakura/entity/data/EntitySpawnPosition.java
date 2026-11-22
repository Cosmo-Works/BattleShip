package me.samsuik.sakura.entity.data;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record EntitySpawnPosition(EntityType<?> type, long encodedPosition) {
    public static EntitySpawnPosition from(final Entity entity) {
        return new EntitySpawnPosition(entity.getType(), entity.blockPosition().asLong());
    }
}
