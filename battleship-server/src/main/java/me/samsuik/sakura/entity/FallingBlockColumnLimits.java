// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class FallingBlockColumnLimits {
    private final Long2ObjectOpenHashMap<Column> columns = new Long2ObjectOpenHashMap<>();

    public void limitInstantFalling(final Level level, final BlockPos pos) {
        final int limit = level.sakuraConfig().cannons.restrictions.instantBlockFallingLimit.or(-1);
        if (limit <= 1) {
            return;
        }
        if (this.count(pos) < limit) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getBlockTicks().clearArea(new BoundingBox(pos.above()));
        }
    }

    public void clear() {
        this.columns.clear();
    }

    private int count(final BlockPos pos) {
        return this.columns.computeIfAbsent(ChunkPos.pack(pos.getX(), pos.getZ()), key -> new Column()).count(pos);
    }

    private static final class Column {
        private @Nullable BlockPos lastToFall;
        private int count;

        private int count(final BlockPos pos) {
            if (this.lastToFall == null || pos.getY() == this.lastToFall.getY() + 1) {
                this.count++;
            } else {
                this.count = 0;
            }
            this.lastToFall = pos;
            return this.count;
        }
    }
}
