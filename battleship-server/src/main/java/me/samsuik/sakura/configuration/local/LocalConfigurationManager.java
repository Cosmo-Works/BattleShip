package me.samsuik.sakura.configuration.local;

import ca.spottedleaf.moonrise.common.util.TickThread;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.samsuik.sakura.configuration.local.ConfigurationContainer.SealedConfigurationContainer;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bukkit.util.BoundingBox;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class LocalConfigurationManager implements LocalConfigurationAccessor {
    private final Object lock = new Object();
    private final Map<ConfigurationBounds, SealedConfigurationContainer> containers = new HashMap<>();
    private final ConfigurationSpatialMap configurationSpatialMap = new ConfigurationSpatialMap();
    private final Level level;
    private long lastGameTime;
    private final Long2ObjectOpenHashMap<CachedLocalConfiguration> cachedConfiguration = new Long2ObjectOpenHashMap<>();
    private final CachedLocalConfiguration[] recentlyAccessed = new CachedLocalConfiguration[8];

    public LocalConfigurationManager(final Level level) {
        this.level = level;
        Arrays.fill(this.recentlyAccessed, this.defaultCachedConfiguration());
    }

    @Override
    public void set(final BoundingBox bb, final SealedConfigurationContainer container) {
        final ConfigurationBounds bounds = ConfigurationBounds.fromBukkitBoundingBox(bb);
        synchronized (this.lock) {
            this.containers.put(bounds, container);
            this.configurationSpatialMap.add(bounds);
            this.clearCacheUnlocked();
        }
    }

    @Override
    public @Nullable SealedConfigurationContainer remove(final BoundingBox bb) {
        final ConfigurationBounds bounds = ConfigurationBounds.fromBukkitBoundingBox(bb);
        synchronized (this.lock) {
            final SealedConfigurationContainer oldContainer = this.containers.remove(bounds);
            if (oldContainer != null) {
                this.configurationSpatialMap.remove(bounds);
            }
            this.clearCacheUnlocked();
            return oldContainer;
        }
    }

    @Override
    public @Nullable SealedConfigurationContainer get(final BoundingBox bb) {
        synchronized (this.lock) {
            return this.containers.get(ConfigurationBounds.fromBukkitBoundingBox(bb));
        }
    }

    @Override
    public @Nullable ConfigurationContainer getContainer(final int x, final int y, final int z) {
        synchronized (this.lock) {
            return this.getContainerUnlocked(x, y, z);
        }
    }

    private @Nullable ConfigurationContainer getContainerUnlocked(final int x, final int y, final int z) {
        final ConfigurationContainer newContainer = new ConfigurationContainer();
        final List<ConfigurationBounds> bounds = this.configurationSpatialMap.get(x, y, z);
        bounds.sort(Comparator.comparingLong(ConfigurationBounds::volume));

        for (final ConfigurationBounds configurationBounds : bounds) {
            newContainer.fillAbsentValues(this.containers.get(configurationBounds));
        }

        return newContainer.contents().isEmpty() ? null : newContainer;
    }

    public ConfigurationContainer getContainerWithDefaults(final int x, final int y, final int z) {
        synchronized (this.lock) {
            final ConfigurationContainer container = this.getContainerUnlocked(x, y, z);
            final SealedConfigurationContainer defaultContainer = ConfigurationDefaults.levelDefaults(this.level);
            if (container == null) {
                return defaultContainer;
            }

            container.fillAbsentValues(defaultContainer);
            return container;
        }
    }

    @Override
    public List<BoundingBox> getAreas(final int x, final int y, final int z) {
        synchronized (this.lock) {
            return this.configurationSpatialMap.get(x, y, z).stream()
                .map(ConfigurationBounds::asBukkitBoundingBox)
                .toList();
        }
    }

    @Override
    public List<BoundingBox> getAllAreas() {
        synchronized (this.lock) {
            return this.containers.keySet().stream()
                .map(ConfigurationBounds::asBukkitBoundingBox)
                .toList();
        }
    }

    @Override
    public void removeAll() {
        synchronized (this.lock) {
            this.containers.clear();
            this.configurationSpatialMap.clear();
            this.clearCacheUnlocked();
        }
    }

    public void clearCache() {
        synchronized (this.lock) {
            this.clearCacheUnlocked();
        }
    }

    private void clearCacheUnlocked() {
        this.cachedConfiguration.clear();
        Arrays.fill(this.recentlyAccessed, this.defaultCachedConfiguration());
    }

    private CachedLocalConfiguration defaultCachedConfiguration() {
        return new CachedLocalConfiguration(Integer.MIN_VALUE, ConfigurationDefaults.levelDefaults(this.level));
    }

    public CachedLocalConfiguration at(final Vec3 vec3) {
        return this.getCachedContainer(Mth.floor(vec3.x()), Mth.floor(vec3.y()), Mth.floor(vec3.z()));
    }

    public CachedLocalConfiguration at(final Vec3i pos) {
        return this.getCachedContainer(pos.getX(), pos.getY(), pos.getZ());
    }

    private CachedLocalConfiguration getCachedContainer(final int x, final int y, final int z) {
        if (!TickThread.isTickThreadFor(this.level, x >> 4, z >> 4)) {
            return this.defaultCachedConfiguration();
        }

        synchronized (this.lock) {
            final long sectionKey = SectionPos.asLong(x, y, z);
            final int recentCacheIndex = ((x & 1) << 2) | ((y & 1) << 1) | (z & 1);
            final CachedLocalConfiguration recentCache = this.recentlyAccessed[recentCacheIndex];
            if (recentCache.sectionKey == sectionKey) {
                return recentCache;
            }

            final long gameTime = this.level.getGameTime();
            if (gameTime - this.lastGameTime >= 60 * 20) {
                this.cachedConfiguration.clear();
                this.lastGameTime = gameTime;
            }

            CachedLocalConfiguration cache = this.cachedConfiguration.get(sectionKey);
            if (cache == null) {
                cache = new CachedLocalConfiguration(sectionKey, this.getContainerWithDefaultsUnlocked(x, y, z));
                this.cachedConfiguration.put(sectionKey, cache);
            }

            this.recentlyAccessed[recentCacheIndex] = cache;
            return cache;
        }
    }

    private ConfigurationContainer getContainerWithDefaultsUnlocked(final int x, final int y, final int z) {
        final ConfigurationContainer container = this.getContainerUnlocked(x, y, z);
        final SealedConfigurationContainer defaultContainer = ConfigurationDefaults.levelDefaults(this.level);
        if (container == null) {
            return defaultContainer;
        }
        container.fillAbsentValues(defaultContainer);
        return container;
    }
}
