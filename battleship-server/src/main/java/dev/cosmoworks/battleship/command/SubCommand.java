package dev.cosmoworks.battleship.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import org.jspecify.annotations.Nullable;

public interface SubCommand {
    @Nullable
    String getDescription();

    LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base, final CommandBuildContext buildContext);

    default boolean isAllowedSelfCommand() {
        return true;
    }

    default boolean hasExtraArgs() {
        return true;
    }

    default Predicate<CommandSourceStack> check(final String node) {
        return BattleshipCommands.permission(getName() + "." + node);
    }

    String getName();
}
