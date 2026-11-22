// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.player.visibility;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.time.Duration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MinimalVisibility {
    private final Cache<Long, Entity> minimalEntities = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofSeconds(5))
        .maximumSize(Short.MAX_VALUE)
        .build();
    // Battleship - region threading: explosions can be filtered from multiple region threads
    // concurrently, guard the non-thread-safe state with a lock.
    private final Object explosionsLock = new Object();
    private final ObjectOpenHashSet<BlockPos> explosions = new ObjectOpenHashSet<>();
    private long lastExplosion;

    public boolean filterEntity(final Entity entity) {
        final BlockPos blockPos = entity.blockPosition();
        final long posAndType = blockPos.asLong() ^ entity.getType().hashCode();
        final Entity visibleEntity = this.minimalEntities.getIfPresent(posAndType);

        if (visibleEntity != null && visibleEntity.isAlive() && blockPos.equals(visibleEntity.blockPosition())) {
            return entity != visibleEntity;
        } else {
            this.minimalEntities.put(posAndType, entity);
            return false;
        }
    }

    public boolean filterExplosion(final Vec3 explosionPos, final long gameTime) {
        final BlockPos explosionBlockPos = BlockPos.containing(explosionPos);
        synchronized (this.explosionsLock) {
            if (this.lastExplosion != gameTime) {
                this.explosions.clear();
                this.lastExplosion = gameTime;
            }

            return this.explosions.add(explosionBlockPos);
        }
    }
}
