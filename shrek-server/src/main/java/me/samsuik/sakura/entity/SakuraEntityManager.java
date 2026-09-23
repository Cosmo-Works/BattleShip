package me.samsuik.sakura.entity;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
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
    private final ReferenceOpenHashSet<LinkedEntityControllers> activelyTicking = new ReferenceOpenHashSet<>();
    private final Map<EntitySpawnPosition, PersistentEntityData> entityData = new Object2ObjectOpenHashMap<>();

    public EntityData getEntityData(final Entity entity) {
        final EntitySpawnPosition spawnPosition = EntitySpawnPosition.from(entity);
        final PersistentEntityData persistentEntityData = this.entityData.computeIfAbsent(
            spawnPosition,
            sp -> new PersistentEntityData()
        );

        MergeLevel mergeLevel = entity.level().localConfig().at(entity.blockPosition()).mergeLevel;

        return new EntityData(spawnPosition, mergeLevel, persistentEntityData);
    }

    public void saveEntityData(final SakuraEntityController controller, final PersistentEntityData persistentEntityData) {
        final EntitySpawnPosition entitySpawnPosition = controller.getEntityData().spawnPosition();
        final PersistentEntityData savedPersistentEntityData = this.entityData.get(entitySpawnPosition);
        if (savedPersistentEntityData != null) {
            savedPersistentEntityData.load(persistentEntityData);
        } else {
            this.entityData.put(entitySpawnPosition, persistentEntityData);
        }
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

        this.lastEntityToTick = sakuraEntity;

        SakuraEntity linkWithEntity = null;
        if (lastSakuraEntity != null
            && mergeLevel == MergeLevel.STRICT
            && sakuraEntity.sameProperties(lastSakuraEntity.asEntity(), 1)) {
            linkWithEntity = lastSakuraEntity;
        }

        if (mergeLevel == MergeLevel.LENIENT || mergeLevel == MergeLevel.SPAWN) {
            linkWithEntity = this.tickedEntityPositions.put(entity.blockPosition(), sakuraEntity);
        }

        return linkWithEntity != null && linkWithEntity.isInSimilarState(currentState) ? linkWithEntity : null;
    }

    public void tick(final long gameTime) {
        this.activelyTicking.clear();
        this.lastEntityToTick = null;
        this.tickedEntityPositions.clear();

        if (gameTime % 20 == 0) {
            this.entityData.values().removeIf(data -> data.expire(20));
        }
    }

    public void decoupleAllTickingEntities() {
        for (final LinkedEntityControllers controllers : List.copyOf(this.activelyTicking)) {
            controllers.getHead().decoupleTicking();
        }
    }

    public void updateTickingStatus(final LinkedEntityControllers controllers, final boolean ticking) {
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
        final Iterable<Entity> entities,
        final int chunkToRegionShift,
        final Long2ReferenceOpenHashMap<RegionizedWorldData> regionToData
    ) {
        this.unmergeGroupsThatWouldSplit(entities, chunkToRegionShift);
        this.clearScratch();
        this.activelyTicking.clear();
        this.movePersistentData(chunkToRegionShift, regionToData);
    }

    private void unmergeGroupsThatWouldSplit(final Iterable<Entity> entities, final int chunkToRegionShift) {
        final ReferenceOpenHashSet<LinkedEntityControllers> seen = new ReferenceOpenHashSet<>();
        for (final Entity entity : entities) {
            if (!(entity instanceof final SakuraEntity sakuraEntity)) {
                continue;
            }

            final LinkedEntityControllers links = sakuraEntity.getSakuraEntityController().getLinks();
            if (links.count() <= 1 || !seen.add(links)) {
                continue;
            }

            if (links.membersWouldSeparate(controller -> regionKey(controller.entity, chunkToRegionShift))) {
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
                into.shrekEntityManager.entityData.put(entry.getKey(), entry.getValue());
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
