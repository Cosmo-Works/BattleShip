// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.explosion.specialised;

import ca.spottedleaf.moonrise.common.list.IteratorSafeOrderedReferenceSet;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import me.samsuik.sakura.entity.EntityState;
import me.samsuik.sakura.entity.SakuraEntity;
import me.samsuik.sakura.entity.SakuraEntityController;
import me.samsuik.sakura.explosion.density.ExplosionEntityKey;
import me.samsuik.sakura.explosion.density.ExplosionObstructionCache;
import me.samsuik.sakura.explosion.density.KnownObstruction;
import me.samsuik.sakura.explosion.density.PaperObstructionCacheKey;
import me.samsuik.sakura.explosion.density.SeenPercentRays;
import me.samsuik.sakura.mechanics.MechanicVersion;
import me.samsuik.sakura.mechanics.MinecraftMechanicsTarget;
import me.samsuik.sakura.utils.AABBUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public abstract class SpecialisedExplosion<E extends Entity & SakuraEntity> extends ServerExplosion {
    private static final double SAFE_DISPATCH_DISTANCE_SQR = 16.0 * 16.0;
    private static final int ALL_DIRECTIONS = 0b111;
    private static final int FOUND_ALL_BLOCKS = ALL_DIRECTIONS + 12;
    private static final int REDUCED_SEEN_PERCENT_RAYS = 9 * 3;

    private final E cause;
    private final Vec3 originalPosition;
    private int swinging = 0;
    private boolean moved = false;
    private Vec3 safeDispatchPosition;
    private final List<Vec3> bufferedExplosions = new ObjectArrayList<>();
    private AABB bounds;
    private final Set<BlockPos> gameEvents = new ObjectOpenHashSet<>();
    private final boolean interactWithBlocks;

    public SpecialisedExplosion(
        final ServerLevel level,
        final E cause,
        final @Nullable DamageSource damageSource,
        final @Nullable ExplosionDamageCalculator damageCalculator,
        final Vec3 center,
        final float radius,
        final boolean fire,
        final Explosion.BlockInteraction blockInteraction
    ) {
        super(level, cause, damageSource, damageCalculator, center, radius, fire, blockInteraction);
        this.cause = cause;
        this.originalPosition = center;
        this.safeDispatchPosition = center;
        this.bounds = new AABB(center, center);
        this.interactWithBlocks = this.interactsWithBlocks();
    }

    public static ServerExplosion appropriateExplosion(
        final ServerLevel level,
        final @Nullable Entity source,
        final @Nullable DamageSource damageSource,
        final @Nullable ExplosionDamageCalculator damageCalculator,
        final Vec3 center,
        final float radius,
        final boolean fire,
        final Explosion.BlockInteraction blockInteraction
    ) {
        if (level.battleshipConfig().cannons.explosion.batchExplosions) {
            if (source instanceof final PrimedTnt tnt) {
                return new TntExplosion(level, tnt, damageSource, damageCalculator, center, radius, fire, blockInteraction);
            }

            if (source instanceof final AbstractWindCharge windCharge) {
                return new WindChargeExplosion(level, windCharge, damageSource, damageCalculator, center, radius, fire, blockInteraction);
            }
        }

        return new ServerExplosion(level, source, damageSource, damageCalculator, center, radius, fire, blockInteraction);
    }

    @Override
    public final int explode() {
        this.initialiseBlockCache();
        final double doubleRadius = this.radius() * 2.0;

        E explodingEntity = this.cause;
        E nextEntity = this.nextEntity(explodingEntity, true);
        boolean firstExplosion = true;
        while (true) {
            final SakuraEntityController controller = explodingEntity.getSakuraEntityController();
            final List<BlockPos> blocksToExplode = this.collectBlocksAndImpactEntities(
                explodingEntity,
                doubleRadius,
                this.interactWithBlocks && this.swinging < FOUND_ALL_BLOCKS,
                firstExplosion || nextEntity == null
            );
            firstExplosion = false;
            nextEntity = this.keepIfStackedDuplicate(explodingEntity, nextEntity);

            final boolean destroyedBlocks = this.finalizeExplosion(blocksToExplode);
            if (nextEntity == null || !this.continueExploding(explodingEntity)) {
                break;
            }

            final EntityState entityState = controller.getEntityState();
            explodingEntity = nextEntity;
            nextEntity = this.nextEntity(explodingEntity, false);

            entityState.apply(explodingEntity);
            this.impactEntity(explodingEntity, this.center, 1, doubleRadius);
            this.nextExplosion(blocksToExplode, destroyedBlocks);
            this.tickExplodableEntity(explodingEntity, doubleRadius, destroyedBlocks);
        }

        return 0;
    }

    private @Nullable E keepIfStackedDuplicate(final E exploding, final @Nullable E next) {
        if (next == null || !next.isAlive()) {
            return null;
        }

        if (!next.position().equals(exploding.position())) {
            return null;
        }

        if (!next.getDeltaMovement().equals(exploding.getDeltaMovement())) {
            return null;
        }

        return next;
    }

    protected abstract Vec3 explosionPosition(final E explodingEntity);

    protected abstract void preventEntityExploding(final E explodableEntity);

    protected abstract boolean continueExploding(final E explodingEntity);

    private List<BlockPos> collectBlocksAndImpactEntities(
        final E explodingEntity,
        final double doubleRadius,
        final boolean interactWithBlocks,
        boolean dispatch
    ) {
        final Vec3 center = this.center;
        if (interactWithBlocks && this.gameEvents.add(BlockPos.containing(center))) {
            this.level().gameEvent(explodingEntity, GameEvent.EXPLODE, center);
        }

        if (center.distanceToSqr(this.safeDispatchPosition) > SAFE_DISPATCH_DISTANCE_SQR) {
            dispatch = true;
            this.safeDispatchPosition = center;
            this.blockCache.clear();
            Arrays.fill(this.directMappedBlockCache, null);
        }

        final List<BlockPos> blocksToExplode = interactWithBlocks
            ? this.calculateExplodedPositions()
            : Collections.emptyList();

        this.bufferedExplosions.add(center);
        this.bounds = AABBUtil.expand(this.bounds, center);

        final SakuraEntityController controller = explodingEntity.getSakuraEntityController();
        if (dispatch || !blocksToExplode.isEmpty() || controller.haveBlocksChangedSinceTicking(this.level())) {
            this.locateAndImpactEntitiesInBounds(doubleRadius);
            this.bounds = new AABB(center, center);
            this.bufferedExplosions.clear();
            this.gameEvents.clear();
        }

        return blocksToExplode;
    }

    private boolean finalizeExplosion(final List<BlockPos> blocksToExplode) {
        this.wasCanceled = false;
        final List<BlockPos> blocksDestroyed = new ObjectArrayList<>(blocksToExplode);
        this.interactWithBlocks(blocksDestroyed);

        if (!this.wasCanceled) {
            this.queueNotification(this.center, blocksDestroyed.size());
        }

        return !blocksDestroyed.isEmpty();
    }

    protected void nextExplosion(final List<BlockPos> blocksToExplode, final boolean destroyedBlocks) {
        if (this.level().battleshipConfig().cannons.explosion.reuseBlockCacheAcrossExplosions && !blocksToExplode.isEmpty() && !destroyedBlocks) {
            this.markBlocksInCacheAsExplodable(blocksToExplode);
        } else {
            super.blockCache.clear();
        }

        Arrays.fill(this.directMappedBlockCache, null);

        if (this.swinging >= ALL_DIRECTIONS) {
            if (!destroyedBlocks && this.level().battleshipConfig().cannons.explosion.avoidRedundantBlockSearches) {
                this.swinging++;
            } else {
                this.swinging = ALL_DIRECTIONS;
            }
        }
    }

    private void tickExplodableEntity(final E explodableEntity, final double doubleRadius, final boolean destroyedBlocks) {
        final SakuraEntityController controller = explodableEntity.getSakuraEntityController();
        final Vec3 oldMovement = controller.getEntityState().movement();
        final boolean hasMoved;

        if (this.moved) {
            hasMoved = true;
        } else if (this.center.equals(this.cause.position())) {
            hasMoved = false;
        } else {
            final double newMovementSqr = explodableEntity.getDeltaMovement().lengthSqr();
            final double oldMovementSqr = oldMovement.lengthSqr();
            final double maxExplosionRadiusSqr = Math.pow(doubleRadius + 1.0, 2.0);
            hasMoved = oldMovementSqr <= maxExplosionRadiusSqr || newMovementSqr <= oldMovementSqr;
        }

        if (destroyedBlocks || hasMoved) {
            this.preventEntityExploding(explodableEntity);
            controller.tickEntity();
            this.center = this.explosionPosition(explodableEntity);
            this.moved |= !this.center.equals(this.originalPosition);
        }

        this.source = explodableEntity;
        explodableEntity.discard();

        if (this.moved) {
            this.updateSwingingState(controller.getEntityState().movement(), oldMovement);
        } else {
            this.swinging = ALL_DIRECTIONS;
        }
    }

    @SuppressWarnings("unchecked")
    private @Nullable E nextEntity(final E explodingEntity, final boolean source) {
        final IteratorSafeOrderedReferenceSet<Entity> entities = this.level().getCurrentWorldData().tickingEntities();
        entities.createRawIterator();
        try {
            final Object[] raw = entities.getListRaw();
            final int size = entities.getListSize();
            int index = -1;
            for (int i = 0; i < size; ++i) {
                if (raw[i] == explodingEntity) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                return null;
            }

            final int nextIndex = entities.advanceRawIterator(index);
            if (nextIndex == Integer.MAX_VALUE) {
                return null;
            }

            final Entity nextEntity = (Entity) raw[nextIndex];
            explodingEntity.getSakuraEntityController().decouple();
            if (nextEntity instanceof final SakuraEntity nextSakuraEntity
                && nextSakuraEntity.samePropertiesAndState(explodingEntity, source ? 1 : 0)) {
                return (E) nextEntity;
            }

            return null;
        } finally {
            entities.finishRawIterator();
        }
    }

    private void updateSwingingState(final Vec3 newMovement, final Vec3 oldMovement) {
        for (final Direction.Axis axis : Direction.Axis.VALUES) {
            final double current = newMovement.get(axis);
            final double previous = oldMovement.get(axis);
            if (current == previous || current * previous <= 0.0) {
                this.swinging |= 1 << axis.ordinal();
            }
        }
    }

    private void impactEntity(final Entity entity, final Vec3 explosionPos, final int potential, final double doubleRadius) {
        if (entity == this.source || entity instanceof AbstractWindCharge) {
            return;
        }

        if (entity.cannonEntity && !(entity instanceof SulfurCube)) {
            this.impactCannonEntity(entity, explosionPos, potential, doubleRadius);
        } else {
            final AABB impactArea = new AABB(explosionPos, explosionPos).inflate(doubleRadius + 1.0);
            for (int count = 0; count < potential; ++count) {
                this.sakura$impactEntity(entity, explosionPos, impactArea, doubleRadius);
            }
        }
    }

    protected final void impactCannonEntity(final Entity entity, final Vec3 explosionPos, final int potential, final double radius) {
        double distanceFromBottom = Math.sqrt(entity.distanceToSqr(explosionPos)) / radius;
        if (distanceFromBottom > 1.0) {
            return;
        }

        double x = entity.getX() - explosionPos.x;
        double y = entity.getEyeY() - explosionPos.y;
        double z = entity.getZ() - explosionPos.z;
        double distance = Math.sqrt(x * x + y * y + z * z);

        final MinecraftMechanicsTarget target = this.mechanicsTarget;
        if (distance < 1.0e-5 && (distance == 0.0 || !target.before(MechanicVersion.v1_21_9))) {
            return;
        }

        if (target.before(MechanicVersion.v1_17)) {
            distanceFromBottom = (float) distanceFromBottom;
            distance = (float) distance;
        }

        x /= distance;
        y /= distance;
        z /= distance;
        final double density = this.getSeenPercentCached(explosionPos, entity);
        final double exposure = (1.0D - distanceFromBottom) * density;
        if (exposure == 0.0) {
            return;
        }

        this.applyEntityVelocity(entity, x * exposure, y * exposure, z * exposure, potential);
    }

    protected final void applyEntityVelocity(
        final Entity entity,
        final double x,
        final double y,
        final double z,
        final int potential
    ) {
        final Vec3 movement = entity.getDeltaMovement();
        double moveX = movement.x();
        double moveY = movement.y();
        double moveZ = movement.z();

        for (int count = 0; count < potential; ++count) {
            moveX += x;
            moveY += y;
            moveZ += z;
        }

        entity.setDeltaMovement(moveX, moveY, moveZ);
        entity.syncVelocity = true;
        entity.needsSync = true;
    }

    protected final float getSeenPercentCached(final Vec3 center, final Entity entity) {
        final Level level = this.level();
        final ExplosionObstructionCache obstructionCache = level.obstructionCache();
        final AABB entityBox = entity.getBoundingBox();

        final long blockRevision = level.getBlockStateRevisionCount();
        final int explosionEntityKey = ExplosionEntityKey.encode(center, entity);
        final KnownObstruction obstruction = obstructionCache.getKnownObstruction(explosionEntityKey, blockRevision);

        if (obstruction != null && obstruction.has(center, entityBox)) {
            return obstruction.getSeenPercent();
        }

        final PaperObstructionCacheKey cacheKey;
        if (this.brokenPaperBehaviour) {
            cacheKey = new PaperObstructionCacheKey(center, entity);

            final float paperSeenPercent = obstructionCache.getPaperObstruction(cacheKey);
            if (paperSeenPercent != ExplosionObstructionCache.UNKNOWN_SEEN_PERCENT) {
                return paperSeenPercent;
            }
        } else {
            cacheKey = null;
        }

        final KnownObstruction safeKnownObstruction;
        if (obstruction != null && !obstruction.isPartiallyObstructed() && obstruction.isExplosionKnown(center)) {
            safeKnownObstruction = obstruction;
        } else {
            safeKnownObstruction = null;
        }

        final float seenPercent = this.calculateSeenPercent(level, center, entity, safeKnownObstruction);
        if (obstruction == null || obstruction.isPartiallyObstructed()) {
            obstructionCache.addKnownObstruction(explosionEntityKey, center, entityBox, seenPercent, blockRevision);
        } else if (seenPercent == obstruction.getSeenPercent()) {
            obstruction.expandKnownArea(center, entityBox);
        }

        if (cacheKey != null) {
            obstructionCache.addPaperObstruction(cacheKey, seenPercent);
        }

        return seenPercent;
    }

    private float calculateSeenPercent(
        final Level level,
        final Vec3 center,
        final Entity entity,
        final @Nullable KnownObstruction obstruction
    ) {
        if (!level.battleshipConfig().cannons.explosion.reduceExposureRaycasts
            || obstruction != null
            || this.useSlowerRaycasts
            || entity.getBbHeight() != 0.98f
            || entity.getBbWidth() != 0.98f) {
            return this.getMechanicAppropriateSeenPercent(center, entity, obstruction);
        }

        final CollisionUtil.LazyEntityCollisionContext context = new CollisionUtil.LazyEntityCollisionContext(entity);

        final AABB entityBox = entity.getBoundingBox();
        final double baseX = entityBox.minX;
        final double baseY = entityBox.minY;
        final double baseZ = entityBox.minZ;

        final double[] rayCoords = SeenPercentRays.SEEN_PERCENT_RAYS;
        int end = Math.min(REDUCED_SEEN_PERCENT_RAYS, rayCoords.length);

        int totalRays = 0;
        int missedRays = 0;

        for (int rays = 0; rays < end; rays += 3) {
            final double x = rayCoords[rays];
            final double y = rayCoords[rays + 1];
            final double z = rayCoords[rays + 2];
            final Vec3 from = new Vec3(
                baseX + x,
                baseY + y,
                baseZ + z
            );

            final int result = this.clipsAnything(from, center, context, this.directMappedBlockCache, this.mutablePos);
            if ((result & ANY_COLLISION) == 0) {
                missedRays++;
            }

            totalRays++;

            if (missedRays != 0 && totalRays != missedRays || (result & MISSED_COLLISION) != 0) {
                end = rayCoords.length;
            }
        }

        return (float) missedRays / (float) totalRays;
    }

    private void locateAndImpactEntitiesInBounds(final double doubleRadius) {
        final List<Vec3> bufferedExplosions = this.bufferedExplosions;
        final AABB bounds = this.bounds;

        final double maxDeviation = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
        final double effectiveDistanceSqr = Math.pow(doubleRadius + maxDeviation, 2.0);
        final boolean stationary = maxDeviation == 0.0;
        final Vec3 boundsCenter = bounds.getCenter();
        final List<Entity> entities = this.level().getEntities(
            this.source,
            bounds.inflate(doubleRadius + maxDeviation + 1.0),
            entity -> entity.isAlive() && !entity.isSpectator()
        );

        for (final Entity entity : entities) {
            if (stationary) {
                this.impactEntity(entity, bufferedExplosions.getFirst(), bufferedExplosions.size(), doubleRadius);
            } else if (entity.distanceToSqr(boundsCenter) <= effectiveDistanceSqr) {
                for (int explosionIndex = 0; explosionIndex < bufferedExplosions.size(); ++explosionIndex) {
                    this.impactEntity(entity, bufferedExplosions.get(explosionIndex), 1, doubleRadius);
                }
            }
        }
    }
}
