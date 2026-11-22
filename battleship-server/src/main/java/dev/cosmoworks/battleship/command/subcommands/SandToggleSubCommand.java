package dev.cosmoworks.battleship.command.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.cosmoworks.battleship.command.SubCommand;
import java.util.Locale;
import me.samsuik.sakura.player.visibility.VisibilityState;
import me.samsuik.sakura.player.visibility.VisibilityType;
import me.samsuik.sakura.player.visibility.VisibilityTypes;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.CommonColors;
import org.apache.commons.lang3.StringUtils;

public class SandToggleSubCommand implements SubCommand {

    @Override
    public String getDescription() {
        return "Toggles falling block (sand) entity visibility";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base, final CommandBuildContext buildContext) {
        return base.executes(context -> {
            final CommandSourceStack source = context.getSource();
            final ServerPlayer player = source.getPlayer();
            if (player == null) {
                source.sendSystemMessage(Component.literal("Only players can use this command").withColor(CommonColors.RED));
                return 0;
            }

            final VisibilityType type = VisibilityTypes.SAND;
            final VisibilityState state = player.visibilitySettings.toggle(type);
            player.getBukkitEntity().sendMessage(me.samsuik.sakura.configuration.GlobalConfiguration.get().messages.fpsSettingChangeComponent("Sand", visibilityLabel(state)));
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
        return "sandtoggle";
    }

    private static String visibilityLabel(final VisibilityState state) {
        return StringUtils.capitalize(state.name().toLowerCase(Locale.ENGLISH));
    }
}
