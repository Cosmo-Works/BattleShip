package gg.shrekmc.command.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gg.shrekmc.ShrekServerConfig;
import gg.shrekmc.command.SubCommand;
import gg.shrekmc.octopus.PluginGate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

public class GateSubCommand implements SubCommand {
    @Override
    public String getDescription() {
        return "Shows the plugin gate report (octopus head cost)";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(
        final LiteralArgumentBuilder<CommandSourceStack> base,
        final CommandBuildContext buildContext
    ) {
        return base.executes(context -> {
            final CommandSourceStack source = context.getSource();
            if (!ShrekServerConfig.octopus().gate.observe) {
                source.sendSystemMessage(Component.literal("The plugin gate is off (octopus.gate.observe in shrek-server.yml).")
                    .withColor(CommonColors.GRAY));
            }
            boolean first = true;
            for (final String line : PluginGate.lastReport()) {
                source.sendSystemMessage(Component.literal(line).withColor(first ? CommonColors.WHITE : CommonColors.GRAY));
                first = false;
            }
            return Command.SINGLE_SUCCESS;
        });
    }

    @Override
    public boolean isAllowedSelfCommand() {
        return false;
    }

    @Override
    public String getName() {
        return "gate";
    }
}
