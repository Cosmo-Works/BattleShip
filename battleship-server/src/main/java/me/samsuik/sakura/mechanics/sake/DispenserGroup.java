// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.mechanics.sake;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DispenserGroup {
    private final Long2ObjectMap<BlockPos> columns = new Long2ObjectOpenHashMap<>();

    public BlockPos getSpawnPosition(final BlockPos pos) {
        final long column = ChunkPos.pack(pos.getX(), pos.getZ());
        return this.columns.computeIfAbsent(column, unused -> pos);
    }
}
