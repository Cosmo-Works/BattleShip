package gg.shrekmc.command.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gg.shrekmc.ShrekServerConfig;
import gg.shrekmc.command.SubCommand;
import gg.shrekmc.debug.RegionPhaseTiming;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

public class RegionsSubCommand implements SubCommand {
    @Override
    public String getDescription() {
        return "Shows where the busiest regions spend their tick time";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(
        final LiteralArgumentBuilder<CommandSourceStack> base,
        final CommandBuildContext buildContext
    ) {
        return base.executes(context -> {
            final CommandSourceStack source = context.getSource();
            if (!ShrekServerConfig.debug().regionPhaseTiming) {
                source.sendSystemMessage(Component.literal("Region phase timing is off (debug.region-phase-timing in shrek-server.yml).")
                    .withColor(CommonColors.GRAY));
            }
            boolean first = true;
            for (final String line : RegionPhaseTiming.lastReport()) {
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
        return "regions";
    }
}
