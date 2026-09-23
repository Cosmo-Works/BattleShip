package me.samsuik.sakura.entity.data;

import java.util.Random;
import me.samsuik.sakura.entity.SakuraEntity;
import me.samsuik.sakura.entity.merge.MergeLevel;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record EntityData(EntitySpawnPosition spawnPosition, MergeLevel mergeLevel, PersistentEntityData persistentEntityData) {
    private static final Random RANDOM = new Random();

    public boolean randomChanceToSpawn() {
        return RANDOM.nextDouble() > this.persistentEntityData.chanceToSpawnEntity();
    }

    public boolean hasLinkedInThePast(final SakuraEntity otherEntity, final int ticksPast) {
        return this.persistentEntityData.haveTicksPast(ticksPast)
            && this.hasLinkedBefore(otherEntity);
    }

    public boolean hasLinkedBefore(final SakuraEntity sakuraEntity) {
        return this.hasLinkedBefore(sakuraEntity.getSakuraEntityController().encodedSpawnPosition());
    }

    public boolean hasLinkedBefore(final long position) {
        return this.persistentEntityData.linkedPositions.contains(position);
    }

    public boolean hasSavedBefore() {
        return !this.persistentEntityData.linkedPositions.isEmpty();
    }
}
