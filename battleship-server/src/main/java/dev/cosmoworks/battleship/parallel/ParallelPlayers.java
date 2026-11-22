package dev.cosmoworks.battleship.parallel;

import ca.spottedleaf.moonrise.common.util.TickThread;
import dev.cosmoworks.battleship.BattleshipServerConfig;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ParallelPlayers {
    private ParallelPlayers() {
    }

    public static boolean resumeFlushing(final List<ServerPlayer> players) {
        final BattleshipServerConfig.Parallel config = BattleshipServerConfig.parallel();
        final int count = players.size();
        if (!config.flushConnections || count < config.minPlayersPerBatch * 2) {
            return false;
        }
        final int batches = RegionParallel.batchesFor(count, config.minPlayersPerBatch);
        RegionParallel.run(batches, batch -> {
            final int from = (int) ((long) count * batch / batches);
            final int to = (int) ((long) count * (batch + 1) / batches);
            for (int i = from; i < to; ++i) {
                final ServerPlayer player = players.get(i);
                if (TickThread.isTickThreadFor(player)) {
                    player.connection.resumeFlushing();
                }
            }
        });
        return true;
    }
}
