package me.samsuik.sakura.configuration.local;

import io.papermc.paper.configuration.WorldConfiguration;
import java.util.HashMap;
import java.util.Map;
import me.samsuik.sakura.explosion.durable.DurableMaterial;
import me.samsuik.sakura.explosion.durable.DurableMaterialsContainer;
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
        final WorldConfiguration.Misc.RedstoneImplementation parsed = level.battleshipConfig().redstone.parsedImplementation.valueSafe();
        final WorldConfiguration.Misc.RedstoneImplementation paperImpl = parsed != null
            ? parsed
            : level.paperConfig().misc.redstoneImplementation;
        return new RedstoneConfiguration(fromPaperImpl(paperImpl), level.sakuraConfig().technical.redstone.redstoneCache);
    }

    private static RedstoneImplementation fromPaperImpl(final WorldConfiguration.Misc.RedstoneImplementation paper) {
        return RedstoneImplementation.values()[paper.ordinal()];
    }

    private ConfigurationHelpers() {
    }
}
