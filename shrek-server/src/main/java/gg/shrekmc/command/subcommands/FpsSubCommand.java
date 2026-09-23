package gg.shrekmc.command.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gg.shrekmc.command.SubCommand;
import me.samsuik.sakura.player.visibility.VisibilityGui;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.CommonColors;

public class FpsSubCommand implements SubCommand {
    private final VisibilityGui visibilityGui = new VisibilityGui();

    @Override
    public String getDescription() {
        return "Opens client visibility settings";
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

            this.visibilityGui.showTo(player.getBukkitEntity());
            return Command.SINGLE_SUCCESS;
        });
    }

    @Override
    public boolean isAllowedSelfCommand() {
        return true;
    }

    @Override
    public boolean hasExtraArgs() {
        return false;
    }

    @Override
    public String getName() {
        return "fps";
    }
}
