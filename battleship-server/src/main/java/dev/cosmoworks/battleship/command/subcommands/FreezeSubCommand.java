package dev.cosmoworks.battleship.command.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.cosmoworks.battleship.command.SubCommand;
import io.canvasmc.canvas.region.RegionThreadingTickManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.CommonColors;

public class FreezeSubCommand implements SubCommand {

    @Override
    public String getDescription() {
        return "Freezes or unfreezes ticking in your current region";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(
        final LiteralArgumentBuilder<CommandSourceStack> base,
        final CommandBuildContext buildContext
    ) {
        return base.executes(context -> {
            final CommandSourceStack source = context.getSource();
            final ServerPlayer player = source.getPlayer();
            if (player == null) {
                source.sendSystemMessage(Component.literal("Only players can use this command").withColor(CommonColors.RED));
                return 0;
            }

            final RegionThreadingTickManager.RegionHandle ticks = player.level().getCurrentWorldData().regionData.getTickManager();
            if (ticks.doesRunGameElements()) {
                ticks.pause();
                source.sendSystemMessage(Component.literal("Region ticks frozen").withColor(CommonColors.YELLOW));
            } else {
                ticks.play();
                source.sendSystemMessage(Component.literal("Region ticks running").withColor(CommonColors.GREEN));
            }
            return Command.SINGLE_SUCCESS;
        });
    }

    @Override
    public boolean isAllowedSelfCommand() {
        return false;
    }

    @Override
    public boolean hasExtraArgs() {
        return false;
    }

    @Override
    public String getName() {
        return "freeze";
    }
}
