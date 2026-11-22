package dev.cosmoworks.battleship.tracker;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import dev.cosmoworks.battleship.BattleshipServerConfig;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Arrays;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class BattleshipTracker {
    public static final String BYPASS_PERMISSION = "battleship.maximumtrackerbypass";
    private static final int BYPASS_RECHECK_TICKS = 100;

    private BattleshipTracker() {
    }

    public static int fullUpdateFrequency() {
        return Math.max(1, BattleshipServerConfig.tracker().fullUpdateFrequency);
    }

    public static boolean fullUpdate(final int trackerTicks) {
        final int frequency = fullUpdateFrequency();
        return frequency <= 1 || Math.floorMod(trackerTicks, frequency) == 0;
    }

    public static int maximumTrackersPerEntity() {
        return BattleshipServerConfig.tracker().maximumTrackersPerEntity;
    }

    public static ReferenceOpenHashSet<ServerPlayer> nearestTrackers(
        final Entity entity,
        final ReferenceList<ServerPlayer> candidates,
        final int max,
        final @Nullable ReferenceOpenHashSet<ServerPlayer> into
    ) {
        final ReferenceOpenHashSet<ServerPlayer> allowed = into == null ? new ReferenceOpenHashSet<>(max) : into;
        allowed.clear();
        final ServerPlayer[] raw = candidates.getRawDataUnchecked();
        final int size = candidates.size();
        final Scratch scratch = SCRATCH.get();
        final long[] keys = scratch.keys(size);
        int count = 0;
        for (int i = 0; i < size; ++i) {
            final ServerPlayer player = raw[i];
            if (player == null || player == entity) {
                continue;
            }
            final double dx = player.getX() - entity.getX();
            final double dz = player.getZ() - entity.getZ();
            final float distance = hasBypass(player) ? 0.0F : (float) (dx * dx + dz * dz);

            keys[count++] = ((long) Float.floatToIntBits(distance) << 32) | i;
        }
        if (count > max) {
            Arrays.sort(keys, 0, count);
            count = max;
        }
        for (int i = 0; i < count; ++i) {
            allowed.add(raw[(int) keys[i]]);
        }
        return allowed;
    }

    private static boolean hasBypass(final ServerPlayer player) {
        if (player.tickCount - player.battleshipTrackerBypassCheckedAt >= BYPASS_RECHECK_TICKS || player.battleshipTrackerBypassCheckedAt > player.tickCount) {
            player.battleshipTrackerBypassCheckedAt = player.tickCount;
            player.battleshipTrackerBypass = player.getBukkitEntity().hasPermission(BYPASS_PERMISSION);
        }
        return player.battleshipTrackerBypass;
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static final class Scratch {
        private long[] keys = new long[64];

        long[] keys(final int size) {
            if (this.keys.length < size) {
                this.keys = new long[Math.max(size, this.keys.length * 2)];
            }
            return this.keys;
        }
    }
}
