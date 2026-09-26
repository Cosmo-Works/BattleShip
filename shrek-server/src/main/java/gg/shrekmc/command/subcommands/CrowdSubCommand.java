package gg.shrekmc.command.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gg.shrekmc.chunk.ChunkPacketCache;
import gg.shrekmc.command.SubCommand;
import gg.shrekmc.spawn.ShrekSpawnCrowd;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.CommonColors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class CrowdSubCommand implements SubCommand {
    @Override
    public String getDescription() {
        return "Shows spawn crowd cap and chunk packet cache counters";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(
        final LiteralArgumentBuilder<CommandSourceStack> base,
        final CommandBuildContext buildContext
    ) {
        return base
            .executes(context -> this.show(context.getSource(), context.getSource().getPlayer()))
            .then(literal("reset").executes(context -> {
                ShrekSpawnCrowd.resetPeaks();
                context.getSource().sendSystemMessage(Component.literal("Reset the tracked player peak").withColor(CommonColors.GREEN));
                return Command.SINGLE_SUCCESS;
            }))
            .then(argument("player", EntityArgument.player())
                .executes(context -> this.show(context.getSource(), EntityArgument.getPlayer(context, "player"))));
    }

    private int show(final CommandSourceStack source, final ServerPlayer viewer) {
        if (viewer != null) {
            final ShrekSpawnCrowd.ViewStats stats = ShrekSpawnCrowd.stats(viewer);
            source.sendSystemMessage(Component.literal(
                "%s: cap %d, tracking %d players, selected %d, riding/spectated %d, peak %d".formatted(
                    viewer.getPlainTextName(),
                    stats.cap(), stats.tracked(), stats.selected(), stats.priority(), stats.peak()
                )
            ).withColor(CommonColors.WHITE));
        }
        source.sendSystemMessage(Component.literal(
            "Most players tracked by any viewer since start or reset: " + ShrekSpawnCrowd.peakTrackedAnyViewer()
        ).withColor(CommonColors.GRAY));
        final long hits = ChunkPacketCache.STORE.hits.sum();
        final long misses = ChunkPacketCache.STORE.misses.sum();
        final long total = hits + misses;
        source.sendSystemMessage(Component.literal(
            "Chunk packet cache: %d hits, %d misses (%d%% hits), %d kept".formatted(
                hits, misses, total == 0 ? 0 : hits * 100 / total, ChunkPacketCache.STORE.size()
            )
        ).withColor(CommonColors.GRAY));
        return Command.SINGLE_SUCCESS;
    }

    @Override
    public boolean isAllowedSelfCommand() {
        return false;
    }

    @Override
    public String getName() {
        return "crowd";
    }
}
