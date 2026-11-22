// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.redstone.network;

import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record RedstoneSourceKey(
    WorldConfiguration.Misc.RedstoneImplementation redstoneImplementation,
    BlockPos position,
    @Nullable Orientation orientation,
    int updateDepth,
    int oldPower,
    int newPower,
    boolean deduplicate
) {
}
