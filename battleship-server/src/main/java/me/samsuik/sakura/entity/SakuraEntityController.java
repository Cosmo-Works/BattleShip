package me.samsuik.sakura.entity;

import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import java.util.Objects;
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
    private LinkedEntityControllers links = new LinkedEntityControllers(this);
    private @Nullable EntityData entityData;
    private EntityState entityState = EntityState.unknown();
    private long blockStateRevision;
    private boolean triedLinkingOnSpawn;

    public SakuraEntityController(final SakuraEntity sakuraEntity) {
        this.sakuraEntity = sakuraEntity;
        this.entity = sakuraEntity.asEntity();
    }

    public void tickEntity() {
        this.updateTickingState(true);
        if (!this.isSleeping()) {
            if (this.entityData == null) {
                this.entityData = this.manager().getEntityData(this.entity);
            }

            final EntityState currentState = EntityState.of(this.entity);
            this.entityState = currentState;
            this.tickAwakeEntity(currentState);
        }

        this.sleepTimer++;
    }

    private void tickAwakeEntity(final EntityState currentState) {
        if (this.attemptToLinkEntities(currentState)) {
            return;
        }

        final Level level = this.entity.level();
        this.blockStateRevision = level.getBlockStateRevisionCount();
        this.entity.tick();

        if (!TickThread.isTickThreadFor(this.entity)) {
            return;
        }
        if (this.entity.handlePortal()) {
            return;
        }
        if (this.entity.isRemoved() || this.blockStateRevision != level.getBlockStateRevisionCount()) {
            this.manager().decoupleAllTickingEntities();
        }
    }

    private boolean attemptToLinkEntities(final EntityState currentState) {
        final MergeLevel mergeLevel = this.getEntityData().mergeLevel();
        final SakuraEntity otherSakuraEntity = this.manager().getEntityToLinkWith(
            this.entity,
            this.sakuraEntity,
            this,
            currentState,
            mergeLevel
        );
        if (otherSakuraEntity == null) {
            return false;
        }

        final SakuraEntityController otherController = otherSakuraEntity.getSakuraEntityController();
        if (!this.canLinkWith(otherSakuraEntity, otherController)) {
            return false;
        }

        otherController.links.link(this, this.links);
        this.setSleeping(true);
        return true;
    }

    private boolean canLinkWith(final SakuraEntity otherSakuraEntity, final SakuraEntityController otherController) {
        final SakuraEntityController tailController = otherController.links.getTail();
        final Entity tailEntity = tailController.sakuraEntity.asEntity();
        final Entity otherEntity = otherSakuraEntity.asEntity();

        return this.entity.getType() == otherEntity.getType()
            && this.entity.isAlive()
            && otherEntity.isAlive()
            && otherEntity.getId() < this.entity.getId()
            && this.entity.moonrise$getSectionY() != Integer.MIN_VALUE
            && this.isFullyAwake()
            && this.sakuraEntity.canLink(tailEntity, tailController.sleepTicks());
    }

    public boolean canLinkOnSpawn(final SakuraEntity otherSakuraEntity) {
        if (this.entity.tickCount < 2 || this.entity.tickCount != otherSakuraEntity.asEntity().tickCount) {
            return false;
        }

        final EntityData data = this.getEntityData();
        if (!data.hasSavedBefore() || !this.attemptLinkingOnSpawn() || data.randomChanceToSpawn()) {
            return false;
        }

        final boolean isFallingBlock = this.entity.getType() == EntityTypes.FALLING_BLOCK;
        return data.hasLinkedInThePast(otherSakuraEntity, isFallingBlock ? 100 : 0);
    }

    private boolean attemptLinkingOnSpawn() {
        final boolean triedLinkingOnSpawn = this.triedLinkingOnSpawn;
        this.triedLinkingOnSpawn = true;
        return !triedLinkingOnSpawn;
    }

    public void setSleeping(final boolean sleeping) {
        if (this.isSleeping() == sleeping) {
            return;
        }

        if (sleeping) {
            this.sleepTimer = Integer.MIN_VALUE;
        } else {
            this.sakuraEntity.catchupOnTicks(this.sleepTimer - Integer.MIN_VALUE);
            this.sleepTimer = 0;
        }

        this.updateCollisionPresence(sleeping);
    }

    private void updateCollisionPresence(final boolean sleeping) {
        final Entity entity = this.entity;
        if (entity.moonrise$getSectionY() == Integer.MIN_VALUE) {
            return;
        }

        final EntityLookup lookup = entity.level().moonrise$getEntityLookup();
        final ChunkEntitySlices slices = lookup.getOrCreateChunk(entity.moonrise$getSectionX(), entity.moonrise$getSectionZ());
        if (sleeping) {
            slices.removeEntityFromSections(entity);
        } else {
            slices.addEntityToSections(entity);
        }
    }

    public int sleepTicks() {
        return this.isSleeping() ? this.sleepTimer - Integer.MIN_VALUE : 0;
    }

    public boolean isFullyAwake() {
        return this.sleepTimer > 0;
    }

    public boolean isSleeping() {
        return this.sleepTimer < 0;
    }

    public void unmergeAll() {
        this.unmergeFromHead(this.links.getHead().entityState);
    }

    private void unmergeFromHead(final EntityState headState) {
        if (this.links.getHead() != this) {
            this.links.getHead().unmergeFromHead(headState);
            return;
        }

        if (this.links.count() <= 1) {
            return;
        }

        final SakuraEntityController remainingHead = this.links.decouple(this);
        if (remainingHead == null) {
            return;
        }

        remainingHead.wakeFrom(headState);
        remainingHead.unmergeFromHead(headState);
    }

    public void decoupleTicking() {
        final SakuraEntityController ticking = this.links.lastToTick;
        if (ticking != null) {
            this.decouple(ticking);
            this.links.lastToTick = null;
        }
    }

    public void decouple() {
        this.decouple(this);
    }

    public void decouple(final SakuraEntityController otherController) {
        final SakuraEntityController remainingHead = this.links.decouple(otherController);
        if (remainingHead != null) {
            remainingHead.wakeFrom(this.entityState);
        }
    }

    private void wakeFrom(final EntityState headState) {
        this.entityState = headState;
        this.restoreEntityState();
        this.entity.sakura$notifyMoved();
        this.setSleeping(false);
        this.updateTickingState(false);
    }

    public void removeEntity() {
        if (this.entityData != null && this.entityData.mergeLevel() == MergeLevel.SPAWN) {
            final PersistentEntityData persistentEntityData = new PersistentEntityData();
            this.links.save(persistentEntityData);
            this.manager().saveEntityData(this, persistentEntityData);
        }
    }

    public long encodedSpawnPosition() {
        return this.getEntityData().spawnPosition().encodedPosition();
    }

    public LinkedEntityControllers getLinks() {
        return this.links;
    }

    public void updateLinks(final LinkedEntityControllers newLinks) {
        this.links = newLinks;
    }

    public EntityData getEntityData() {
        return Objects.requireNonNull(this.entityData);
    }

    public EntityState getEntityState() {
        return this.entityState;
    }

    public void restoreEntityState() {
        this.entityState.apply(this.entity);
    }

    public boolean haveBlocksChangedSinceTicking(final Level level) {
        return this.blockStateRevision != level.getBlockStateRevisionCount();
    }

    private void updateTickingState(final boolean newTick) {
        final LinkedEntityControllers links = this.links;
        if (newTick) {
            links.lastToTick = this;
        }

        final SakuraEntityController head = links.getHead();
        final SakuraEntityController tail = links.getTail();
        if (head != tail && head == this) {
            this.manager().updateTickingStatus(links, true);
        }
        if (tail == this) {
            this.manager().updateTickingStatus(links, false);
        }
    }

    private SakuraEntityManager manager() {
        return this.entity.level().entityManager();
    }
}
