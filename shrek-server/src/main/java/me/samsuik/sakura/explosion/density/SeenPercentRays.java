// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.explosion.density;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class SeenPercentRays {
    private static final AABB BLOCK_ENTITY_BOX = new AABB(Vec3.ZERO, new Vec3(0.98f, 0.98f, 0.98f));
    public static final double[] SEEN_PERCENT_RAYS;

    static {
        SEEN_PERCENT_RAYS = rays(BLOCK_ENTITY_BOX).toDoubleArray();
    }

    private static DoubleArrayList rays(final AABB boundingBox) {
        // Copied from Paper/Moonrise ServerExplosion
        final double diffX = boundingBox.getXsize();
        final double diffY = boundingBox.getYsize();
        final double diffZ = boundingBox.getZsize();

        final double incX = 1.0 / (diffX * 2.0 + 1.0);
        final double incY = 1.0 / (diffY * 2.0 + 1.0);
        final double incZ = 1.0 / (diffZ * 2.0 + 1.0);

        final double offX = (1.0 - Math.floor(1.0 / incX) * incX) * 0.5;
        final double offZ = (1.0 - Math.floor(1.0 / incZ) * incZ) * 0.5;

        final int horizontalCount = (int) Math.floor(1.0 / incX);
        final int verticalCount   = (int) Math.floor(1.0 / incY);

        final DoubleArrayList rays = new DoubleArrayList();
        for (int x = 0; x <= horizontalCount; x++) {
            for (int y = 0; y <= verticalCount; y++) {
                for (int z = 0; z <= horizontalCount; z++) {
                    final double fromX = Math.fma(incX * x, diffX, offX);
                    final double fromY = (incY * y) * diffY;
                    final double fromZ = Math.fma(incZ * z, diffZ, offZ);

                    if (isCornerOrCenter(x, y, z, horizontalCount, verticalCount)) {
                        rays.add(0, fromX);
                        rays.add(1, fromY);
                        rays.add(2, fromZ);
                    } else {
                        rays.add(fromX);
                        rays.add(fromY);
                        rays.add(fromZ);
                    }
                }
            }
        }

        return rays;
    }

    private static boolean isCornerOrCenter(
        final int x,
        final int y,
        final int z,
        final int horizontalCount,
        final int verticalCount
    ) {
        if ((x == 0 || x == horizontalCount)
            && (y == 0 || y == verticalCount)
            && (z == 0 || z == horizontalCount)) {
            return true;
        }

        final int horizontalCenter = horizontalCount / 2;
        final int verticalCenter = verticalCount / 2;
        return x == horizontalCenter && y == verticalCenter && z == horizontalCenter;
    }
}
