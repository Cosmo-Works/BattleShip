// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.redstone;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.Duration;
import me.samsuik.sakura.redstone.network.RedstoneNetwork;
import me.samsuik.sakura.redstone.network.RedstoneSourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("DataFlowIssue")
@NullMarked
public final class RedstoneWireCache {
    private final Cache<RedstoneSourceKey, RedstoneNetwork> redstoneNetworks = CacheBuilder.newBuilder()
        .removalListener(notification -> ((RedstoneNetwork) notification.getValue()).unregister())
        .expireAfterAccess(Duration.ofSeconds(30))
        .maximumSize(8192)
        .build();
    private @Nullable TrackedRedstoneChanges redstoneChanges;
    private @Nullable RedstoneNetwork applyingRedstoneNetwork;

    public boolean isApplyingNetwork() {
        return this.applyingRedstoneNetwork != null;
    }

    public boolean isUpdatingRedstone(final BlockPos pos) {
        return this.isApplyingNetwork() && this.applyingRedstoneNetwork.isWirePos(pos);
    }

    public void wirePower(final BlockPos redstoneWirePos, final int oldPower, final int newPower) {
        if (this.redstoneChanges != null) {
            this.redstoneChanges.addWireUpdate(redstoneWirePos, oldPower, newPower);
        }
    }

    public void singleNeighborUpdate(final BlockPos updatePos) {
        if (this.redstoneChanges != null) {
            this.redstoneChanges.addNeighborUpdate(updatePos, true);
        }
    }

    public void neighborUpdates(final BlockPos updatePos) {
        if (this.redstoneChanges != null) {
            this.redstoneChanges.addNeighborUpdate(updatePos, false);
        }
    }

    public boolean cache(final Level level, final RedstoneSourceKey sourceKey) {
        final RedstoneNetwork redstoneNetwork = this.redstoneNetworks.getIfPresent(sourceKey);
        try {
            this.applyingRedstoneNetwork = redstoneNetwork;
            if (redstoneNetwork != null && redstoneNetwork.apply(level)) {
                return true;
            }
        } finally {
            this.applyingRedstoneNetwork = null;
        }

        if (this.redstoneChanges == null) {
            this.redstoneChanges = new TrackedRedstoneChanges(level, sourceKey);
        }
        return false;
    }

    public void cacheNetwork() {
        final TrackedRedstoneChanges redstoneChanges = this.redstoneChanges;
        if (redstoneChanges == null) {
            return;
        }

        this.redstoneChanges = null;
        final RedstoneNetwork network = redstoneChanges.createNetwork();
        if (network != null) {
            this.redstoneNetworks.put(redstoneChanges.getSourceKey(), network);
        }
    }
}
