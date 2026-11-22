// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.utils;

import ca.spottedleaf.moonrise.common.util.TickThread;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Entities shared between the moving blocks of one piston, looked up once per tick.
 * Only used while the ticking region owns every chunk the shared bounds touch.
 */
@NullMarked
public final class SharedEntities {
    private final ObjectArrayList<Entity> entities = new ObjectArrayList<>();
    private final AABB sharedBounds;
    private long lastTick = Long.MIN_VALUE;
    private boolean ownedByThisRegion;

    public SharedEntities(final AABB bounds) {
        this.sharedBounds = bounds;
    }

    public List<Entity> getEntities(final Level level, final AABB bounds, final @Nullable Predicate<? super Entity> selector) {
        final long gameTime = level.getGameTime();
        if (gameTime != this.lastTick) {
            this.updateSharedEntities(level);
            this.lastTick = gameTime;
        }

        if (!this.ownedByThisRegion) {
            return level.getEntities((Entity) null, bounds, selector);
        }

        final List<Entity> ret = new ObjectArrayList<>();
        for (final Entity entity : this.entities) {
            if (entity.getBoundingBox().intersects(bounds) && (selector == null || selector.test(entity))) {
                ret.add(entity);
            }
        }

        return ret;
    }

    private void updateSharedEntities(final Level level) {
        this.entities.clear();
        // The shared bounds can cover chunks that none of the moving blocks touch
        this.ownedByThisRegion = TickThread.isTickThreadFor(level, this.sharedBounds);
        if (this.ownedByThisRegion) {
            this.entities.addAll(level.getEntities((Entity) null, this.sharedBounds, null));
        }
    }
}
