// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.explosion;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
public final class ProtectionPluginProbeEvent extends EntityExplodeEvent {
    public ProtectionPluginProbeEvent(final Entity entity) {
        super(entity, entity.getLocation(), probeList(entity), 0.0f, ExplosionResult.DESTROY);
    }

    @Override
    public boolean callEvent() {
        return super.callEvent() && !this.blockList().isEmpty();
    }

    private static List<Block> probeList(final Entity entity) {
        final Location location = entity.getLocation();
        final int maxHeight = location.getWorld().getMaxHeight();

        // TNT protection plugins look below blocks for any whitelisted blocks.
        // If the plugin finds one of those blocks then it clears the block list.
        // We are using an out of bounds block to try probe for these plugins.
        final List<Block> list = new ObjectArrayList<>();
        list.add(location.set(0.0, maxHeight, 0.0).getBlock());
        return list;
    }
}
