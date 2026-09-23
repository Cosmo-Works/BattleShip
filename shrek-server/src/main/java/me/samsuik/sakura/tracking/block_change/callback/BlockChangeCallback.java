package me.samsuik.sakura.tracking.block_change.callback;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

import java.util.function.LongConsumer;

@NullMarked
public interface BlockChangeCallback {
    static BlockChangeCallback identifier(final LongConsumer identifierConsumer) {
        return (pos, newBlock, oldBlock, identifier) -> identifierConsumer.accept(identifier);
    }

    void call(BlockPos pos, BlockState newBlock, BlockState oldBlock, long identifier);
}
