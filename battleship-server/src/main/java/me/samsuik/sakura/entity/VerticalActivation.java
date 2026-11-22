package me.samsuik.sakura.entity;

import dev.cosmoworks.battleship.BattleshipWorldConfig;
import io.papermc.paper.entity.activation.ActivationType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class VerticalActivation {
    public static int[] forTypes(final Level world) {
        final BattleshipWorldConfig.Entities.VerticalActivationRange ranges = world.battleshipConfig().entities.verticalActivationRange;
        final int worldHeight = world.getHeight();
        final int[] inflate = new int[ActivationType.values().length];
        inflate[ActivationType.ANIMAL.ordinal()] = clamp(ranges.animals, worldHeight);
        inflate[ActivationType.MONSTER.ordinal()] = clamp(ranges.monsters, worldHeight);
        inflate[ActivationType.RAIDER.ordinal()] = clamp(ranges.raiders, worldHeight);
        inflate[ActivationType.MISC.ordinal()] = clamp(ranges.misc, worldHeight);
        inflate[ActivationType.WATER.ordinal()] = clamp(ranges.water, worldHeight);
        inflate[ActivationType.VILLAGER.ordinal()] = clamp(ranges.villagers, worldHeight);
        inflate[ActivationType.FLYING_MONSTER.ordinal()] = clamp(ranges.flyingMonsters, worldHeight);
        return inflate;
    }

    public static int widest(final int[] inflate) {
        int widest = 0;
        for (final int range : inflate) {
            widest = Math.max(widest, range);
        }
        return widest;
    }

    private static int clamp(final int configured, final int worldHeight) {
        if (configured <= 0) {
            return worldHeight;
        }
        return Math.min(configured, worldHeight);
    }

    private VerticalActivation() {
    }
}
