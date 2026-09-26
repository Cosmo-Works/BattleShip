package me.samsuik.sakura.player.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

/** The occlusion ray walk visits exactly the voxels the segment passes through, in order. */
@AllFeatures
class TrackerOcclusionWalkTest {

    @Test
    void visitsAConnectedPathToTheEndVoxel() {
        final Random random = new Random(3);
        for (int round = 0; round < 2000; ++round) {
            final Vec3 from = new Vec3(random.nextDouble() * 200 - 100, random.nextDouble() * 100, random.nextDouble() * 200 - 100);
            final Vec3 to = from.add(random.nextDouble() * 80 - 40, random.nextDouble() * 40 - 20, random.nextDouble() * 80 - 40);
            final List<int[]> visited = new ArrayList<>();
            TrackerOcclusion.walk(from, to, 10_000, (x, y, z) -> {
                visited.add(new int[] {x, y, z});
                return TrackerOcclusion.CONTINUE;
            });
            int px = (int) Math.floor(from.x), py = (int) Math.floor(from.y), pz = (int) Math.floor(from.z);
            for (final int[] voxel : visited) {
                final int steps = Math.abs(voxel[0] - px) + Math.abs(voxel[1] - py) + Math.abs(voxel[2] - pz);
                assertEquals(1, steps, "voxels must be face neighbours");
                // the segment really passes through the voxel
                final AABB box = new AABB(voxel[0], voxel[1], voxel[2], voxel[0] + 1, voxel[1] + 1, voxel[2] + 1).inflate(1.0E-7);
                assertTrue(box.contains(from) || box.clip(from, to).isPresent(), "segment misses a visited voxel");
                px = voxel[0];
                py = voxel[1];
                pz = voxel[2];
            }
            if (!visited.isEmpty()) {
                assertEquals((int) Math.floor(to.x), px);
                assertEquals((int) Math.floor(to.y), py);
                assertEquals((int) Math.floor(to.z), pz);
            }
        }
    }

    @Test
    void stopsWhereTheVisitorSays() {
        final int result = TrackerOcclusion.walk(new Vec3(0.5, 0.5, 0.5), new Vec3(10.5, 0.5, 0.5), 100, (x, y, z) -> x == 5 ? TrackerOcclusion.STOP_OCCLUDED : TrackerOcclusion.CONTINUE);
        assertEquals(TrackerOcclusion.STOP_OCCLUDED, result);
    }
}
