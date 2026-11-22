package me.samsuik.sakura.player.knockback;

import org.jspecify.annotations.NullMarked;

@NullMarked
final class KnockbackSyncPhysics {
    private static final double TERMINAL_VELOCITY = 3.92;
    private static final double DRAG = 0.98;
    private static final int MAX_TICKS = 30;

    static double fallVelocity(double velocity, final double gravity, int ticks) {
        while (ticks > 0) {
            velocity -= gravity;
            velocity *= DRAG;
            ticks--;
        }
        return velocity;
    }

    static double distanceTravelled(double velocity, final int ticks, final double gravity) {
        double travelled = 0.0;
        for (int tick = 0; tick < ticks; tick++) {
            travelled += velocity;
            velocity = (velocity - gravity) * DRAG;
            velocity = Math.min(velocity, TERMINAL_VELOCITY);
        }
        return travelled;
    }

    static int fallTicks(final double initialVelocity, double distance, final double gravity) {
        double velocity = Math.abs(initialVelocity);
        int ticks = 0;
        while (distance > 0.0) {
            if (ticks > MAX_TICKS) {
                return -1;
            }
            velocity += gravity;
            velocity = Math.min(velocity, TERMINAL_VELOCITY);
            velocity *= DRAG;
            distance -= velocity;
            ticks++;
        }
        return ticks;
    }

    static int ticksUntilApex(double velocity, final double gravity) {
        int ticks = 0;
        while (velocity > 0.0) {
            if (ticks > MAX_TICKS) {
                return -1;
            }
            velocity -= gravity;
            velocity = Math.min(velocity, TERMINAL_VELOCITY);
            velocity *= DRAG;
            ticks++;
        }
        return ticks;
    }

    private KnockbackSyncPhysics() {
    }
}
