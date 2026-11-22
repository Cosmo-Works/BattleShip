package me.samsuik.sakura.entity;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.TickThread;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.List;
import java.util.Map;
import me.samsuik.sakura.entity.data.EntityData;
import me.samsuik.sakura.entity.data.EntitySpawnPosition;
import me.samsuik.sakura.entity.data.PersistentEntityData;
import me.samsuik.sakura.entity.merge.MergeLevel;
import me.samsuik.sakura.utils.SimpleHashTable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class SakuraEntityManager {
    private final SimpleHashTable<BlockPos, SakuraEntity> tickedEntityPositions = new SimpleHashTable<>(128);
    private @Nullable SakuraEntity lastEntityToTick;
    private final ReferenceOpenHashSet<LinkedControllers> activelyTicking = new ReferenceOpenHashSet<>();
    private final Map<EntitySpawnPosition, PersistentEntityData> entityData = new Object2ObjectOpenHashMap<>();

    public EntityData getEntityData(final Entity entity) {
        final EntitySpawnPosition spawnPosition = EntitySpawnPosition.from(entity);
        final PersistentEntityData persistentEntityData = this.entityData.computeIfAbsent(
            spawnPosition,
            _ -> new PersistentEntityData()
        );

        MergeLevel mergeLevel = entity.level().localConfig().at(entity.blockPosition()).mergeLevel;

        return new EntityData(spawnPosition, mergeLevel, persistentEntityData);
    }

    public void saveEntityData(final EntityData entityData, final PersistentEntityData persistentEntityData) {
        final EntitySpawnPosition entitySpawnPosition = entityData.spawnPosition();
        final PersistentEntityData savedPersistentEntityData = this.entityData.get(entitySpawnPosition);
        if (savedPersistentEntityData != null) {
            savedPersistentEntityData.load(persistentEntityData);
        } else {
            this.entityData.put(entitySpawnPosition, persistentEntityData);
        }
    }

    public boolean tryLinkEntities(
        final SakuraEntityController controller,
        final Entity entity,
        final SakuraEntity sakuraEntity,
        final EntityState currentState,
        final EntityData entityData
    ) {
        final MergeLevel mergeLevel = entityData.mergeLevel();
        final SakuraEntity otherSakuraEntity = this.getEntityToLinkWith(
            entity, sakuraEntity, controller, currentState, mergeLevel
        );

        if (otherSakuraEntity != null) {
            final SakuraEntityController otherController = otherSakuraEntity.getSakuraEntityController();
            if (controller.canLinkWith(sakuraEntity, otherSakuraEntity, otherController)) {
                controller.linkWith(otherController);
                return true;
            }
        }

        this.lastEntityToTick = sakuraEntity;
        return false;
    }

    public @Nullable SakuraEntity getEntityToLinkWith(
        final Entity entity,
        final SakuraEntity sakuraEntity,
        final SakuraEntityController controller,
        final EntityState currentState,
        final MergeLevel mergeLevel
    ) {
        if (mergeLevel == MergeLevel.NONE) {
            return null;
        }

        final SakuraEntity lastSakuraEntity = this.lastEntityToTick;
        if (lastSakuraEntity != null
            && mergeLevel == MergeLevel.SPAWN
            && controller.canLinkOnSpawn(lastSakuraEntity)
            && sakuraEntity.sameProperties(lastSakuraEntity.asEntity(), 1)
            && lastSakuraEntity.getSakuraEntityController().isFullyAwake()) {
            return lastSakuraEntity;
        }

        SakuraEntity linkWithEntity = null;
        if (lastSakuraEntity != null
            && sakuraEntity.sameProperties(lastSakuraEntity.asEntity(), 1)
            && lastSakuraEntity.isInSimilarState(currentState)) {
            linkWithEntity = lastSakuraEntity;
        }

        if (mergeLevel == MergeLevel.LENIENT || mergeLevel == MergeLevel.SPAWN) {
            final SakuraEntity entityWithSamePos = this.tickedEntityPositions.put(entity.blockPosition(), sakuraEntity);
            if (linkWithEntity == null && entityWithSamePos != null && entityWithSamePos.isInSimilarState(currentState)) {
                linkWithEntity = entityWithSamePos;
            }
        }

        return linkWithEntity;
    }

    public void tick(final long gameTime) {
        this.activelyTicking.clear();
        this.lastEntityToTick = null;
        this.tickedEntityPositions.clear();

        if (gameTime % 20 == 0) {
            this.entityData.values().removeIf(data -> data.expire(20));
        }
    }

    public void decouple(final SakuraEntityController controller) {
        controller.decouple(controller.entity.sakura$getTickOrder());
        this.invalidate();
    }

    public void decoupleTickingEntities(final Entity entity) {
        final int tickOrder = entity.sakura$getTickOrder();
        for (final LinkedControllers linkedControllers : this.activelyTicking) {
            final SakuraEntityController head = linkedControllers.getHead();
            // Entities that left this region while ticking belong to another region now
            if (TickThread.isTickThreadFor(head.entity)) {
                head.decouple(tickOrder);
            }
        }

        this.activelyTicking.clear();
        this.invalidate();
    }

    private void invalidate() {
        if (this.lastEntityToTick != null) {
            this.lastEntityToTick = null;
            this.tickedEntityPositions.clear();
        }
    }

    public void updateTickingStatus(final LinkedControllers controllers, final boolean ticking) {
        if (ticking) {
            this.activelyTicking.add(controllers);
        } else {
            this.activelyTicking.remove(controllers);
        }
    }

    public void mergeFrom(final SakuraEntityManager from) {
        this.entityData.putAll(from.entityData);
        this.activelyTicking.addAll(from.activelyTicking);
        from.clearScratch();
        from.entityData.clear();
        from.activelyTicking.clear();
    }

    public void splitInto(
        final int chunkToRegionShift,
        final Long2ReferenceOpenHashMap<RegionizedWorldData> regionToData
    ) {
        this.clearScratch();
        this.activelyTicking.clear();
        this.movePersistentData(chunkToRegionShift, regionToData);
    }

    /**
     * Wakes up the linked entities that would end up in different regions. This runs before the
     * entities are handed to the new regions, as waking an entity up moves it to the head's position.
     */
    public void unmergeGroupsThatWouldSplit(final Iterable<Entity> entities, final int chunkToRegionShift) {
        final ReferenceOpenHashSet<LinkedControllers> seen = new ReferenceOpenHashSet<>();
        for (final Entity entity : entities) {
            if (!(entity instanceof final SakuraEntity sakuraEntity)) {
                continue;
            }

            final LinkedControllers links = sakuraEntity.getSakuraEntityController().getLinks();
            if (links.count() <= 1 || !seen.add(links)) {
                continue;
            }

            if (links.membersWouldSeparate(controller -> regionKey(controller.entity, chunkToRegionShift))) {
                // Regions split between ticks, the linked entities continue from where the head is now
                links.getHead().storeEntityState();
                sakuraEntity.getSakuraEntityController().unmergeAll();
            }
        }
    }

    private void movePersistentData(
        final int chunkToRegionShift,
        final Long2ReferenceOpenHashMap<RegionizedWorldData> regionToData
    ) {
        for (final Map.Entry<EntitySpawnPosition, PersistentEntityData> entry : List.copyOf(this.entityData.entrySet())) {
            final RegionizedWorldData into = regionToData.get(regionKey(entry.getKey().encodedPosition(), chunkToRegionShift));
            if (into != null) {
                into.battleshipEntityManager.entityData.put(entry.getKey(), entry.getValue());
            }
        }
        this.entityData.clear();
    }

    private void clearScratch() {
        this.lastEntityToTick = null;
        this.tickedEntityPositions.clear();
    }

    private static long regionKey(final Entity entity, final int chunkToRegionShift) {
        final ChunkPos pos = entity.chunkPosition();
        return CoordinateUtils.getChunkKey(pos.x() >> chunkToRegionShift, pos.z() >> chunkToRegionShift);
    }

    private static long regionKey(final long packedBlockPos, final int chunkToRegionShift) {
        final int chunkX = BlockPos.getX(packedBlockPos) >> 4;
        final int chunkZ = BlockPos.getZ(packedBlockPos) >> 4;
        return CoordinateUtils.getChunkKey(chunkX >> chunkToRegionShift, chunkZ >> chunkToRegionShift);
    }
}
