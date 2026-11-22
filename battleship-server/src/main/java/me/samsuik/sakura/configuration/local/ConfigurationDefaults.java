package me.samsuik.sakura.configuration.local;

import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ConfigurationDefaults {
    public static ConfigurationContainer.SealedConfigurationContainer levelDefaults(final Level level) {
        final ConfigurationContainer container = new ConfigurationContainer();
        container.set(ConfigurationKeys.MECHANICS_TARGET, ConfigurationHelpers.mechanicsTarget(level));
        container.set(ConfigurationKeys.DURABLE_MATERIALS, ConfigurationHelpers.durableMaterialContainer(ConfigurationHelpers.durableMaterials(level)));
        container.set(ConfigurationKeys.REDSTONE_BEHAVIOUR, ConfigurationHelpers.redstoneConfigurationDefault(level));
        container.set(ConfigurationKeys.CONSISTENT_EXPLOSION_RADIUS, level.battleshipConfig().cannons.explosion.consistentRadius);
        container.set(ConfigurationKeys.LAVA_FLOW_SPEED, -1);
        container.set(ConfigurationKeys.MERGE_LEVEL, ConfigurationHelpers.mergeLevel(level));
        container.set(ConfigurationKeys.TNT_SPREAD, ConfigurationHelpers.tntSpread(level));
        container.set(ConfigurationKeys.TNT_FLOWS_IN_WATER, level.battleshipConfig().cannons.tnt.flowsInWater);
        container.set(ConfigurationKeys.HEIGHT_PARITY, level.battleshipConfig().cannons.sand.heightParity);
        container.set(ConfigurationKeys.FLOATING_POINT_FIX, level.battleshipConfig().cannons.sand.floatingPointFix);
        container.set(ConfigurationKeys.BROKEN_PAPER_EXPLOSION_BEHAVIOUR, level.battleshipConfig().cannons.explosion.useBrokenPaperExplosionBehaviour(level));
        container.set(ConfigurationKeys.HEIGHT_LIMIT, ConfigurationHelpers.heightLimit(level));
        return container.seal();
    }

    private ConfigurationDefaults() {
    }
}
