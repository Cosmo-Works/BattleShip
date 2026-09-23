// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.redstone;

import io.papermc.paper.configuration.WorldConfiguration.Misc.RedstoneImplementation;
import me.samsuik.sakura.redstone.network.RedstoneSourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class RedstoneBehaviour {
    private RedstoneBehaviour() {
    }

    public static RedstoneImplementation implementation(final Level level) {
        return implementation(level, BlockPos.ZERO);
    }

    public static RedstoneImplementation implementation(final Level level, final BlockPos pos) {
        return level.localConfig().at(pos).paperRedstoneImplementation();
    }

    public static boolean cacheEnabled(final Level level) {
        return cacheEnabled(level, BlockPos.ZERO);
    }

    public static boolean cacheEnabled(final Level level, final BlockPos pos) {
        return !usesPanda(level, pos) && level.localConfig().at(pos).redstoneBehaviour.cache();
    }

    public static boolean usesPanda(final Level level) {
        return usesPanda(level, BlockPos.ZERO);
    }

    public static boolean usesPanda(final Level level, final BlockPos pos) {
        return level.localConfig().at(pos).mechanicsTarget.isLegacy();
    }

    public static boolean usesAlternateCurrent(final Level level) {
        return usesAlternateCurrent(level, BlockPos.ZERO);
    }

    public static boolean usesAlternateCurrent(final Level level, final BlockPos pos) {
        return !usesPanda(level, pos) && implementation(level, pos) == RedstoneImplementation.ALTERNATE_CURRENT;
    }

    public static boolean usesEigencraft(final Level level) {
        return usesEigencraft(level, BlockPos.ZERO);
    }

    public static boolean usesEigencraft(final Level level, final BlockPos pos) {
        return !usesPanda(level, pos) && implementation(level, pos) == RedstoneImplementation.EIGENCRAFT;
    }

    public static boolean tryApplyCache(
        final Level level,
        final BlockPos pos,
        final @Nullable Orientation orientation,
        final int oldPower,
        final int newPower
    ) {
        if (!cacheEnabled(level, pos)) {
            return false;
        }
        return level.redstoneWireCache().cache(level, new RedstoneSourceKey(
            implementation(level, pos),
            pos,
            orientation,
            level.neighborUpdateDepth(),
            oldPower,
            newPower
        ));
    }
}
