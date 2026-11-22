// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.mechanics;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class HoneyBehaviour {
    private HoneyBehaviour() {
    }

    public static double fallingSpeedBeforeDrag(final Entity entity, final double deltaY) {
        if (entity.mechanicsTarget().before(MechanicVersion.v1_21_2)) {
            return deltaY;
        }
        return deltaY / 0.98F + 0.08;
    }

    public static double fallingSpeedAfterDrag(final Entity entity, final double deltaY) {
        if (entity.mechanicsTarget().before(MechanicVersion.v1_21_2)) {
            return deltaY;
        }
        return (deltaY - 0.08) * 0.98F;
    }
}
