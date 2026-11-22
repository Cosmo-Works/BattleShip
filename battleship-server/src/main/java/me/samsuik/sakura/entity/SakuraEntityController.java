package me.samsuik.sakura.entity;

import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import me.samsuik.sakura.entity.data.EntityData;
import me.samsuik.sakura.entity.data.PersistentEntityData;
import me.samsuik.sakura.entity.merge.MergeLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class SakuraEntityController {
    public final SakuraEntity sakuraEntity;
    public final Entity entity;
    private int sleepTimer = 0;
    private LinkedControllers links = new LinkedControllers(this);
    private @Nullable EntityData entityData;
    private EntityState entityState = EntityState.unknown();
    private long blockStateRevision;
    private boolean triedLinkingOnSpawn;

    public SakuraEntityController(final SakuraEntity sakuraEntity) {
        this.sakuraEntity = sakuraEntity;
        this.entity = sakuraEntity.asEntity();
    }

    public long encodedSpawnPosition() {
        final EntityData entityData = this.entityData;
        if (entityData == null) {
            return Long.MIN_VALUE;
        }
        return entityData.spawnPosition().encodedPosition();
    }

    public LinkedControllers getLinks() {
        return this.links;
    }

    public void updateLinks(final LinkedControllers newLinks) {
        this.links = newLinks;
    }

    public EntityState getPreTickState() {
        return this.entityState;
    }

    public void restoreEntityState() {
        this.entityState.apply(this.entity);
    }

    public void storeEntityState() {
        this.entityState = EntityState.of(this.entity);
    }

    public boolean haveBlocksChangedSinceTicking(final Level level) {
        return this.blockStateRevision != level.getBlockStateRevisionCount();
    }

    public void tickEntity() {
        // The manager belongs to the region ticking this entity
        final SakuraEntityManager manager = this.manager();
        manager.updateTickingStatus(this.links, true);

        if (!this.isSleeping()) {
            this.tickAwakeEntity(manager);
        }

        this.sleepTimer++;
    }

    private void tickAwakeEntity(final SakuraEntityManager manager) {
        final Entity entity = this.entity;
        final Level level = entity.level();

        if (this.entityData == null) {
            this.entityData = manager.getEntityData(entity);
        }

        this.blockStateRevision = level.getBlockStateRevisionCount();
        this.storeEntityState();

        if (manager.tryLinkEntities(this, entity, this.sakuraEntity, this.entityState, this.entityData)) {
            return;
        }

        entity.tick();

        // The entity can leave this region while ticking
        if (!TickThread.isTickThreadFor(entity)) {
            return;
        }
        if (entity.handlePortal()) {
            return;
        }
        if (entity.isRemoved() || this.haveBlocksChangedSinceTicking(level)) {
            manager.decoupleTickingEntities(entity);
        }
    }

    public void removeEntity() {
        final EntityData entityData = this.entityData;
        if (this.entity.level().getCurrentWorldData() == null) {
            // Removed outside of a ticking region, there is no entity manager to update
            this.decouple(this.entity.sakura$getTickOrder());
            return;
        }

        final SakuraEntityManager manager = this.manager();
        if (entityData != null && entityData.mergeLevel() == MergeLevel.SPAWN) {
            final PersistentEntityData persistentEntityData = new PersistentEntityData();
            this.links.save(persistentEntityData);
            manager.saveEntityData(entityData, persistentEntityData);
        }

        manager.decouple(this);
    }

    public boolean canLinkOnSpawn(final SakuraEntity otherSakuraEntity) {
        final Entity entity = this.entity;
        if (entity.tickCount < 2 || entity.tickCount != otherSakuraEntity.asEntity().tickCount) {
            return false;
        }

        final EntityData entityData = this.entityData;
        if (entityData == null
            || !entityData.hasSavedBefore()
            || !this.canTryToLinkOnSpawn()
            || entityData.randomChanceToSpawn()) {
            return false;
        }

        final boolean isFallingBlock = entity.getType() == EntityTypes.FALLING_BLOCK;
        return entityData.hasLinkedInThePast(otherSakuraEntity, isFallingBlock ? 100 : 0);
    }

    public boolean canLinkWith(
        final SakuraEntity sakuraEntity,
        final SakuraEntity otherSakuraEntity,
        final SakuraEntityController otherController
    ) {
        final Entity otherEntity = otherSakuraEntity.asEntity();
        final Entity entity = this.entity;

        if (entity.getType() != otherEntity.getType()
            || !entity.isAlive()
            || !otherEntity.isAlive()
            || otherEntity.sakura$getTickOrder() >= entity.sakura$getTickOrder()) {
            return false;
        }

        return sakuraEntity.canLink(otherController.links.getHead().entity, 1);
    }

    public boolean canTryToLinkOnSpawn() {
        final boolean triedLinkingOnSpawn = this.triedLinkingOnSpawn;
        this.triedLinkingOnSpawn = true;
        return !triedLinkingOnSpawn;
    }

    public void linkWith(final SakuraEntityController otherController) {
        otherController.getLinks().link(this, this.getLinks());
        this.setSleeping(true);
    }

    /**
     * Separates the linked entities that tick after {@code tickOrder} from the ones that have already ticked.
     *
     * @return the head of the entities that were separated and woken up
     */
    public @Nullable SakuraEntityController decouple(final int tickOrder) {
        final LinkedControllers.Controllers decoupled = this.links.decouple(tickOrder);
        if (decoupled == null) {
            return null;
        }

        final SakuraEntityController oldestHead = decoupled.oldest().getHead();
        final SakuraEntityController decoupledHead = decoupled.newest().getHead();

        decoupledHead.entityState = oldestHead.getPreTickState();
        decoupledHead.restoreEntityState();

        // Update the entity position then wake up the entity
        decoupledHead.entity.sakura$notifyMoved();
        decoupledHead.setSleeping(false);
        return decoupledHead;
    }

    /**
     * Wakes up every linked entity, used before the linked entities can end up in different regions.
     * This only touches the linked entities and never the entity manager of a region.
     */
    public void unmergeAll() {
        SakuraEntityController head = this.links.getHead();
        while (head != null) {
            head = head.decouple(head.entity.sakura$getTickOrder());
        }
    }

    public boolean isFullyAwake() {
        return this.sleepTimer > 0;
    }

    public boolean isSleeping() {
        return this.sleepTimer < 0;
    }

    public int sleepTicks() {
        return this.isSleeping() ? this.sleepTimer - Integer.MIN_VALUE : 0;
    }

    public void setSleeping(final boolean sleeping) {
        if (this.isSleeping() == sleeping) {
            return;
        }

        final Entity entity = this.entity;
        if (sleeping) {
            this.sleepTimer = Integer.MIN_VALUE;
        } else {
            final int ticksToCatchupOn = this.sleepTimer - Integer.MIN_VALUE;
            this.sakuraEntity.catchupOnTicks(ticksToCatchupOn);
            this.sleepTimer = 0;
        }

        // It's not safe to wake up an entity that has already been removed
        if (entity.isRemoved() || entity.moonrise$getSectionY() == Integer.MIN_VALUE) {
            return;
        }

        final EntityLookup lookup = entity.level().moonrise$getEntityLookup();
        final ChunkEntitySlices slices = lookup.getOrCreateChunk(entity.moonrise$getSectionX(), entity.moonrise$getSectionZ());

        // Remove sleeping entities from the chunk map and add them back when woken up
        if (sleeping) {
            slices.removeEntityFromSections(entity);
        } else {
            slices.addEntityToSections(entity);
        }
    }

    private SakuraEntityManager manager() {
        return this.entity.level().entityManager();
    }
}
