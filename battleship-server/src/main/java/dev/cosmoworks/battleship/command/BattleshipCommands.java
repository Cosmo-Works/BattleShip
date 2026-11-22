package dev.cosmoworks.battleship.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.cosmoworks.battleship.command.subcommands.FpsSubCommand;
import dev.cosmoworks.battleship.command.subcommands.FreezeSubCommand;
import dev.cosmoworks.battleship.command.subcommands.LocalConfigSubCommand;
import dev.cosmoworks.battleship.command.subcommands.MechanicSubCommand;
import dev.cosmoworks.battleship.command.subcommands.ReloadSubCommand;
import dev.cosmoworks.battleship.command.subcommands.SandToggleSubCommand;
import dev.cosmoworks.battleship.command.subcommands.TntToggleSubCommand;
import dev.cosmoworks.battleship.command.subcommands.TpsSubCommand;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.CommonColors;
import org.jspecify.annotations.NullMarked;

import static net.minecraft.commands.Commands.literal;

@NullMarked
public class BattleshipCommands {
    public static final BattleshipCommands INSTANCE;

    static {
        INSTANCE = new BattleshipCommands();
        INSTANCE.register(
            new ReloadSubCommand(),
            new MechanicSubCommand(),
            new TntToggleSubCommand(),
            new SandToggleSubCommand(),
            new FpsSubCommand(),
            new FreezeSubCommand(),
            new TpsSubCommand(),
            new LocalConfigSubCommand(),
            new dev.cosmoworks.battleship.command.subcommands.CrowdSubCommand(),
            new dev.cosmoworks.battleship.command.subcommands.RegionsSubCommand()
        );
    }

    private final List<SubCommand> subCommands = new LinkedList<>();

    public void register(final SubCommand command) {
        this.subCommands.add(command);
    }

    public void register(final SubCommand... commands) {
        for (final SubCommand command : commands) {
            this.register(command);
        }
    }

    public static Predicate<CommandSourceStack> permission(final String node) {
        return source -> source.hasPermission(Permissions.COMMANDS_ADMIN, "battleship.command." + node);
    }

    public void build(final CommandDispatcher<CommandSourceStack> dispatcher, final CommandBuildContext context) {
        final LiteralArgumentBuilder<CommandSourceStack> base = literal("sakura")
            .requires(source -> {
                boolean canUse = permission("help").test(source);

                for (final SubCommand subCommand : this.subCommands) {
                    if (canUse) break;

                    if (permission(subCommand.getName()).test(source)) {
                        canUse = true;
                    }
                }

                return canUse;
            })
            .executes(commandContext -> {
                commandContext.getSource().sendSystemMessage(Component.literal("Battleship commands:").withColor(CommonColors.GREEN));
                for (final SubCommand subCommand : this.subCommands) {
                    final String description = subCommand.getDescription();
                    commandContext.getSource().sendSystemMessage(Component.literal(
                        "  /sakura " + subCommand.getName() + (description == null ? "" : " - " + description)
                    ).withColor(CommonColors.GRAY));
                }
                return this.subCommands.size();
            });

        for (final SubCommand sub : this.subCommands) {
            final String name = sub.getName();

            base.then(sub.construct(literal(name)
                .requires(permission(name)), context));

            if (sub.isAllowedSelfCommand()) {
                dispatcher.register(sub.construct(literal(name)
                    .requires(permission(name)), context));

                dispatcher.register(sub.construct(literal("sakura:" + name)
                    .requires(permission(name)), context));
            }
        }

        dispatcher.register(base);
    }
}
