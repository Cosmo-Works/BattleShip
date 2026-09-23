// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.explosion.density;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("DataFlowIssue")
@NullMarked
public final class ExplosionObstructionCache {
    public static final float UNKNOWN_SEEN_PERCENT = -1.0f;

    private final Int2ObjectOpenHashMap<KnownObstruction> knownObstructions = new Int2ObjectOpenHashMap<>();
    private final Object2FloatOpenHashMap<PaperObstructionCacheKey> paperObstructionCache = new Object2FloatOpenHashMap<>();
    private long lastBlockRevision;

    public @Nullable KnownObstruction getKnownObstruction(final int explosionEntityKey, final long blockRevision) {
        if (this.lastBlockRevision != blockRevision) {
            this.knownObstructions.clear();
        }

        return this.knownObstructions.get(explosionEntityKey);
    }

    public void addKnownObstruction(
        final int explosionEntityKey,
        final Vec3 center,
        final AABB entityBox,
        final float seenPercent,
        final long blockRevision
    ) {
        this.knownObstructions.put(explosionEntityKey, new KnownObstruction(center, entityBox, seenPercent));
        this.lastBlockRevision = blockRevision;
    }

    public float getPaperObstruction(final PaperObstructionCacheKey cacheKey) {
        return this.paperObstructionCache.getOrDefault(cacheKey, UNKNOWN_SEEN_PERCENT);
    }

    public void addPaperObstruction(final PaperObstructionCacheKey cacheKey, final float seenPercent) {
        this.paperObstructionCache.put(cacheKey, seenPercent);
    }

    public void tick(final long gameTime) {
        this.knownObstructions.clear();
        this.paperObstructionCache.clear();

        if (gameTime % 200 == 0) {
            this.knownObstructions.trim();
            this.paperObstructionCache.trim();
        }
    }
}
