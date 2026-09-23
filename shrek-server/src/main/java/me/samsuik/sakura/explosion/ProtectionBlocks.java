package me.samsuik.sakura.explosion;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@NullMarked
public final class ProtectionBlocks {
    public static boolean cancelBlockDamage(final Level level, final Vec3 center) {
        final Set<Block> protectionBlocks = level.shrekConfig().cannons.explosion.protectionBlocks();
        if (protectionBlocks.isEmpty()) {
            return false;
        }
        return columnHasProtectionBlock(level, center, protectionBlocks);
    }

    public static Set<Block> parse(final List<String> tokens, final Logger logger, final String worldName) {
        final Set<Block> blocks = new ReferenceOpenHashSet<>();
        for (final String token : tokens) {
            final Block block = findBlock(token);
            if (block == null) {
                logger.warn("Unknown protection-block \"{}\" for level {}", token, worldName);
                continue;
            }
            blocks.add(block);
        }
        return blocks;
    }

    private static boolean columnHasProtectionBlock(
        final Level level,
        final Vec3 center,
        final Set<Block> protectionBlocks
    ) {
        final int x = Mth.floor(center.x);
        final int z = Mth.floor(center.z);
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = Mth.floor(center.y); y >= level.getMinY(); y--) {
            final BlockState state = level.getBlockStateIfLoaded(cursor.set(x, y, z));
            if (state == null) {
                return false;
            }
            if (protectionBlocks.contains(state.getBlock())) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Block findBlock(final String token) {
        final Identifier identifier = Identifier.tryParse(token.toLowerCase(Locale.ENGLISH));
        if (identifier == null || !BuiltInRegistries.BLOCK.containsKey(identifier)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getValue(identifier);
    }

    private ProtectionBlocks() {
    }
}
