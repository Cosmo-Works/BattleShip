package dev.cosmoworks.battleship.spawn;

import com.google.common.base.Preconditions;
import dev.cosmoworks.battleship.BattleshipServerConfig;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class BattleshipSpawnCrowd implements SpawnCrowd {
    public static final BattleshipSpawnCrowd INSTANCE = new BattleshipSpawnCrowd();

    private static final byte SPRINTING = Entity.BATTLESHIP_SPRINTING_FLAG_MASK;
    private static final int NO_OVERRIDE = Integer.MIN_VALUE;

    private record WorldOverride(@Nullable Location center, @Nullable Double radius) {
    }

    private record LevelRegion(
        BattleshipServerConfig.SpawnCrowd config,
        int version,
        boolean active,
        @Nullable Location center,
        double radius
    ) {
    }

    private final Map<ResourceKey<Level>, WorldOverride> overrides = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, LevelRegion> regions = new ConcurrentHashMap<>();
    private final AtomicInteger version = new AtomicInteger();
    private volatile int maxVisibleOverride = NO_OVERRIDE;

    private BattleshipSpawnCrowd() {
    }

    // region resolution

    private LevelRegion region(final ServerLevel level) {
        final BattleshipServerConfig.SpawnCrowd config = BattleshipServerConfig.spawnCrowd();
        final int version = this.version.get();
        LevelRegion region = this.regions.get(level.dimension());
        if (region == null || region.config() != config || region.version() != version) {
            region = this.resolve(level, config, version);
            this.regions.put(level.dimension(), region);
        }
        return region;
    }

    private LevelRegion resolve(final ServerLevel level, final BattleshipServerConfig.SpawnCrowd config, final int version) {
        final WorldOverride override = this.overrides.get(level.dimension());
        final Location center = override == null ? null : override.center();
        final boolean active = config.enabled && (center != null || listed(level, config.worlds));
        final double radius = override != null && override.radius() != null ? override.radius() : Math.max(0, config.radius);
        return new LevelRegion(config, version, active, center, radius);
    }

    private static boolean listed(final ServerLevel level, final List<String> worlds) {
        final String dimension = level.dimension().identifier().toString();
        final String name = level.getWorld().getName();
        for (final String world : worlds) {
            final String entry = world.trim();
            if (entry.equalsIgnoreCase(name) || entry.toLowerCase(Locale.ROOT).equals(dimension)) {
                return true;
            }
        }
        return false;
    }

    private static double centerX(final ServerLevel level, final LevelRegion region) {
        final Location center = region.center();
        return center != null ? center.getX() : level.serverLevelData.getRespawnData().pos().getX() + 0.5;
    }

    private static double centerZ(final ServerLevel level, final LevelRegion region) {
        final Location center = region.center();
        return center != null ? center.getZ() : level.serverLevelData.getRespawnData().pos().getZ() + 0.5;
    }

    private boolean inside(final ServerLevel level, final double x, final double z) {
        final LevelRegion region = this.region(level);
        if (!region.active()) {
            return false;
        }
        final Location center = region.center();
        final double centerX;
        final double centerZ;
        if (center != null) {
            centerX = center.getX();
            centerZ = center.getZ();
        } else {
            final BlockPos spawn = level.serverLevelData.getRespawnData().pos();
            centerX = spawn.getX() + 0.5;
            centerZ = spawn.getZ() + 0.5;
        }
        final double dx = x - centerX;
        final double dz = z - centerZ;
        final double radius = region.radius();
        return dx * dx + dz * dz <= radius * radius;
    }

    private boolean inside(final Entity entity) {
        return entity.level() instanceof ServerLevel level && this.inside(level, entity.getX(), entity.getZ());
    }

    private int configuredCap() {
        final int override = this.maxVisibleOverride;
        return override != NO_OVERRIDE ? override : BattleshipServerConfig.spawnCrowd().maxVisiblePlayers;
    }

    int capFor(final ServerPlayer viewer) {
        if (!BattleshipServerConfig.spawnCrowd().enabled) {
            return 0;
        }
        final int cap = this.configuredCap();
        return cap > 0 && (BattleshipServerConfig.spawnCrowd().capEverywhere || this.inside(viewer)) ? cap : 0;
    }

    public static boolean canTrack(final ServerPlayer viewer, final ServerPlayer other, final boolean currentlySeen) {
        final CrowdView view = viewer.battleshipCrowdView;
        final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region = TickRegionScheduler.getCurrentRegion();
        if (region == null) {
            return true;
        }
        return view.canSee(viewer, other, region.getData().getCurrentTick(), currentlySeen);
    }

    public static void prepareDeferred(final ServerPlayer viewer) {
        final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region = TickRegionScheduler.getCurrentRegion();
        if (region != null) {
            viewer.battleshipCrowdView.prepareDeferred(viewer, region.getData().getCurrentTick());
        }
    }

    public static void applyDeferred(final ServerPlayer viewer) {
        viewer.battleshipCrowdView.applyDeferred(viewer);
    }

    public static void prepare(final ServerPlayer viewer) {
        final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region = TickRegionScheduler.getCurrentRegion();
        if (region != null) {
            viewer.battleshipCrowdView.prepare(viewer, region.getData().getCurrentTick());
        }
    }

    public static boolean skipUnseen(final ServerPlayer viewer, final Entity tracked) {
        if (!(tracked instanceof ServerPlayer other)) {
            return false;
        }
        final CrowdView view = viewer.battleshipCrowdView;
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(viewer)) {
            return false;
        }
        final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region = TickRegionScheduler.getCurrentRegion();
        return region != null && view.hiddenByCap(viewer, other, region.getData().getCurrentTick());
    }

    static boolean alwaysVisible(final ServerPlayer viewer, final ServerPlayer other) {
        return other.isPassenger() || other.isVehicle() || viewer.getCamera() == other;
    }

    public static void onPairing(final ServerPlayer viewer, final Entity tracked, final boolean paired) {
        if (!(tracked instanceof ServerPlayer other) || !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(viewer)) {
            return;
        }
        final CrowdView view = viewer.battleshipCrowdView;
        if (paired) {
            view.onPaired(other);
        } else {
            view.onUnpaired(other);
        }
    }

    private static final java.util.concurrent.atomic.AtomicInteger PEAK_TRACKED = new java.util.concurrent.atomic.AtomicInteger();

    static void recordPeak(final int tracked) {
        PEAK_TRACKED.accumulateAndGet(tracked, Math::max);
    }

    public record ViewStats(int cap, int tracked, int selected, int peak, int priority) {
    }

    public static ViewStats stats(final ServerPlayer viewer) {
        final CrowdView view = viewer.battleshipCrowdView;
        return new ViewStats(view.cap(), view.trackedCount(), view.selectedCount(), view.peakTracked(), view.priorityCount());
    }

    public static int peakTrackedAnyViewer() {
        return PEAK_TRACKED.get();
    }

    public static void resetPeaks() {
        PEAK_TRACKED.set(0);
    }

    private boolean hidesSprint(final ServerPlayer player) {
        final BattleshipServerConfig.SpawnCrowd config = BattleshipServerConfig.spawnCrowd();
        return config.enabled && config.hideSprintParticles && this.inside(player);
    }

    public static void updateSprintMask(final ServerPlayer player, final ServerEntity.Synchronizer synchronizer) {
        final boolean hide = INSTANCE.hidesSprint(player);
        if (hide == player.battleshipSprintHidden) {
            return;
        }
        player.battleshipSprintHidden = hide;
        final byte flags = player.getEntityData().get(Entity.BATTLESHIP_SHARED_FLAGS);
        if ((flags & SPRINTING) != 0) {
            final byte sent = hide ? (byte) (flags & ~SPRINTING) : flags;
            synchronizer.sendToTrackingPlayers(new ClientboundSetEntityDataPacket(
                player.getId(), List.of(SynchedEntityData.DataValue.create(Entity.BATTLESHIP_SHARED_FLAGS, sent))
            ));
        }
    }

    public static List<SynchedEntityData.DataValue<?>> dataForOthers(final Entity entity, final List<SynchedEntityData.DataValue<?>> values) {
        if (!(entity instanceof ServerPlayer player) || !player.battleshipSprintHidden) {
            return values;
        }
        final int flagsId = Entity.BATTLESHIP_SHARED_FLAGS.id();
        for (int i = 0, size = values.size(); i < size; ++i) {
            final SynchedEntityData.DataValue<?> value = values.get(i);
            if (value.id() == flagsId && value.value() instanceof Byte flags && (flags & SPRINTING) != 0) {
                final List<SynchedEntityData.DataValue<?>> masked = new ArrayList<>(values);
                masked.set(i, SynchedEntityData.DataValue.create(Entity.BATTLESHIP_SHARED_FLAGS, (byte) (flags & ~SPRINTING)));
                return masked;
            }
        }
        return values;
    }

    public static boolean hidesMovementSounds(final Entity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        final BattleshipServerConfig.SpawnCrowd config = BattleshipServerConfig.spawnCrowd();
        return config.enabled && config.hideStepSounds && INSTANCE.inside(player);
    }

    private static ServerLevel level(final World world) {
        Preconditions.checkArgument(world instanceof CraftWorld, "world");
        return ((CraftWorld) world).getHandle();
    }

    private void override(final ServerLevel level, final UnaryOperator<WorldOverride> change) {
        this.overrides.compute(level.dimension(), (key, current) -> {
            final WorldOverride updated = change.apply(current == null ? new WorldOverride(null, null) : current);
            return updated.center() == null && updated.radius() == null ? null : updated;
        });
        this.version.incrementAndGet();
    }

    @Override
    public boolean isActive(final World world) {
        return this.region(level(world)).active();
    }

    @Override
    public @Nullable Location getCenter(final World world) {
        final ServerLevel level = level(world);
        final LevelRegion region = this.region(level);
        if (!region.active()) {
            return null;
        }
        final Location center = region.center();
        if (center != null) {
            return center.clone();
        }
        final BlockPos spawn = level.serverLevelData.getRespawnData().pos();
        return new Location(world, centerX(level, region), spawn.getY(), centerZ(level, region));
    }

    @Override
    public void setCenter(final Location center) {
        Preconditions.checkArgument(center.getWorld() != null, "center has no world");
        final Location copy = center.clone();
        this.override(level(center.getWorld()), current -> new WorldOverride(copy, current.radius()));
    }

    @Override
    public void resetCenter(final World world) {
        this.override(level(world), current -> new WorldOverride(null, current.radius()));
    }

    @Override
    public double getRadius(final World world) {
        return this.region(level(world)).radius();
    }

    @Override
    public void setRadius(final World world, final double radius) {
        Preconditions.checkArgument(Double.isFinite(radius) && radius >= 0, "radius must be finite and not negative");
        this.override(level(world), current -> new WorldOverride(current.center(), radius));
    }

    @Override
    public void resetRadius(final World world) {
        this.override(level(world), current -> new WorldOverride(current.center(), null));
    }

    @Override
    public int getMaxVisiblePlayers() {
        return this.configuredCap();
    }

    @Override
    public void setMaxVisiblePlayers(final int maxVisiblePlayers) {
        this.maxVisibleOverride = maxVisiblePlayers == NO_OVERRIDE ? NO_OVERRIDE + 1 : maxVisiblePlayers;
    }

    @Override
    public void resetMaxVisiblePlayers() {
        this.maxVisibleOverride = NO_OVERRIDE;
    }

    @Override
    public boolean isInside(final Location location) {
        final World world = location.getWorld();
        return world instanceof CraftWorld craftWorld && this.inside(craftWorld.getHandle(), location.getX(), location.getZ());
    }

    @Override
    public boolean isInside(final Player player) {
        return player instanceof CraftPlayer craftPlayer && this.inside(craftPlayer.getHandle());
    }
}
