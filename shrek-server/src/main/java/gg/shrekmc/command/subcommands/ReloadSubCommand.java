package gg.shrekmc.command.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gg.shrekmc.ShrekServerConfig;
import gg.shrekmc.ShrekWorldConfig;
import gg.shrekmc.command.SubCommand;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.CommonColors;

public class ReloadSubCommand implements SubCommand {

    @Override
    public String getDescription() {
        return "Reloads the Shrek configuration";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base, final CommandBuildContext buildContext) {
        return base.executes(context -> {
            final CommandSourceStack source = context.getSource();

            source.sendSystemMessage(
                Component.literal("Some configuration options cannot be changed at runtime or may work incorrectly after reloading.").withColor(CommonColors.RED)
            );
            source.sendSystemMessage(
                Component.literal("This command is unsupported. If you encounter issues, please run /stop").withColor(CommonColors.RED)
            );

            ShrekServerConfig.reload();
            ShrekWorldConfig.reload();
            for (final ServerLevel level : MinecraftServer.getServer().getAllLevels()) {
                level.reloadShrekConfig();
                level.localConfig().clearCache();
            }

            source.sendSystemMessage(Component.literal("Reloaded Shrek configuration").withColor(CommonColors.GREEN));
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
        return "reload";
    }
}
