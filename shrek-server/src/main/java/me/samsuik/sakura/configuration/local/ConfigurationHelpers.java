package me.samsuik.sakura.configuration.local;

import io.papermc.paper.configuration.WorldConfiguration;
import java.util.HashMap;
import java.util.Map;
import me.samsuik.sakura.entity.TntSpread;
import me.samsuik.sakura.entity.merge.MergeLevel;
import me.samsuik.sakura.explosion.durable.DurableMaterial;
import me.samsuik.sakura.explosion.durable.DurableMaterialsContainer;
import me.samsuik.sakura.mechanics.MinecraftMechanicsTarget;
import me.samsuik.sakura.redstone.RedstoneConfiguration;
import me.samsuik.sakura.redstone.RedstoneImplementation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.bukkit.craftbukkit.block.CraftBlockType;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ConfigurationHelpers {
    public static DurableMaterialsContainer.SealedDurableMaterialsContainer durableMaterialContainer(
        final Map<Block, DurableMaterial> materials
    ) {
        final me.samsuik.sakura.explosion.durable.DurableMaterialsContainer container =
            new me.samsuik.sakura.explosion.durable.DurableMaterialsContainer();
        materials.forEach((block, material) -> container.set(CraftBlockType.minecraftToBukkitNew(block), material));
        return container.seal();
    }

    public static Map<Block, DurableMaterial> durableMaterials(
        final me.samsuik.sakura.explosion.durable.DurableMaterialsContainer container
    ) {
        final Map<Block, DurableMaterial> durableMaterials = new HashMap<>();
        container.contents().forEach((type, material) -> durableMaterials.put(CraftBlockType.bukkitToMinecraftNew(type), material));
        return durableMaterials;
    }

    public static WorldConfiguration.Misc.RedstoneImplementation toPaperImpl(final RedstoneConfiguration redstoneConfig) {
        return WorldConfiguration.Misc.RedstoneImplementation.values()[redstoneConfig.implementation().ordinal()];
    }

    public static RedstoneConfiguration redstoneConfigurationDefault(final Level level) {
        final WorldConfiguration.Misc.RedstoneImplementation parsed = level.shrekConfig().redstone.parsedImplementation.valueSafe();
        final WorldConfiguration.Misc.RedstoneImplementation paperImpl = parsed != null
            ? parsed
            : level.paperConfig().misc.redstoneImplementation;
        return new RedstoneConfiguration(fromPaperImpl(paperImpl), level.shrekConfig().redstone.cache);
    }

    private static RedstoneImplementation fromPaperImpl(final WorldConfiguration.Misc.RedstoneImplementation paper) {
        return RedstoneImplementation.values()[paper.ordinal()];
    }

    public static TntSpread tntSpread(final Level level) {
        final TntSpread spread = level.shrekConfig().cannons.tnt.parsedSpread.valueSafe();
        return spread != null ? spread : TntSpread.ALL;
    }

    public static MergeLevel mergeLevel(final Level level) {
        final MergeLevel mergeLevel = level.shrekConfig().cannons.parsedMergeLevel.valueSafe();
        return mergeLevel != null ? mergeLevel : MergeLevel.LENIENT;
    }

    public static MinecraftMechanicsTarget mechanicsTarget(final Level level) {
        return level.mechanicsTarget();
    }

    public static int heightLimit(final Level level) {
        final Integer parsed = level.shrekConfig().mechanics.parsedHeightLimit.valueSafe();
        return parsed != null ? parsed : Integer.MIN_VALUE;
    }

    public static Map<Block, DurableMaterial> durableMaterials(final Level level) {
        final Map<Block, DurableMaterial> materials = level.shrekConfig().cannons.explosion.parsedDurableMaterials.valueSafe();
        return materials != null ? materials : Map.of();
    }

    private ConfigurationHelpers() {
    }
}
