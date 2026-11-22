package dev.cosmoworks.battleship.command.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.cosmoworks.battleship.command.SubCommand;
import me.samsuik.sakura.configuration.local.ConfigurationContainer;
import me.samsuik.sakura.configuration.local.LocalConfigurationAccessor;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.CommonColors;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class LocalConfigSubCommand implements SubCommand {
    private static final int DEFAULT_REGION_SIZE = 16;

    @Override
    public String getDescription() {
        return "Inspects and edits spatial local configuration areas";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(
        final LiteralArgumentBuilder<CommandSourceStack> base,
        final CommandBuildContext buildContext
    ) {
        return base.executes(context -> this.inspect(context.getSource()))
            .then(literal("delete").executes(context -> this.delete(context.getSource())))
            .then(literal("clear").executes(context -> this.clear(context.getSource())))
            .then(literal("create")
                .executes(context -> this.create(context.getSource(), DEFAULT_REGION_SIZE))
                .then(argument("size", IntegerArgumentType.integer(1))
                    .executes(context -> this.create(context.getSource(), IntegerArgumentType.getInteger(context, "size")))))
            .then(literal("grid")
                .then(argument("count", IntegerArgumentType.integer(1))
                    .then(argument("size", IntegerArgumentType.integer(1))
                        .executes(context -> this.grid(
                            context.getSource(),
                            IntegerArgumentType.getInteger(context, "count"),
                            IntegerArgumentType.getInteger(context, "size")
                        )))));
    }

    @Override
    public boolean isAllowedSelfCommand() {
        return false;
    }

    @Override
    public boolean hasExtraArgs() {
        return true;
    }

    @Override
    public String getName() {
        return "localconfig";
    }

    private int inspect(final CommandSourceStack source) {
        final Player player = bukkitPlayer(source);
        if (player == null) {
            return 0;
        }
        this.describeAreas(player);
        return Command.SINGLE_SUCCESS;
    }

    private int delete(final CommandSourceStack source) {
        final Player player = bukkitPlayer(source);
        if (player == null) {
            return 0;
        }
        final BoundingBox area = currentArea(player);
        if (area == null) {
            player.sendRichMessage("<red>You are not inside a local-config area");
            return 0;
        }
        player.getWorld().localConfig().remove(area);
        player.sendRichMessage("<red>Removed area");
        return Command.SINGLE_SUCCESS;
    }

    private int clear(final CommandSourceStack source) {
        final Player player = bukkitPlayer(source);
        if (player == null) {
            return 0;
        }
        player.getWorld().localConfig().removeAll();
        player.sendRichMessage("<red>Removed all areas");
        return Command.SINGLE_SUCCESS;
    }

    private int create(final CommandSourceStack source, final int size) {
        final Player player = bukkitPlayer(source);
        if (player == null) {
            return 0;
        }
        final Location location = player.getLocation();
        final BoundingBox area = BoundingBox.of(location, size, size, size);
        player.getWorld().localConfig().set(area, ConfigurationContainer.sealedContainer());
        player.sendRichMessage("<green>Created a new area with size " + size);
        return Command.SINGLE_SUCCESS;
    }

    private int grid(final CommandSourceStack source, final int count, final int size) {
        final Player player = bukkitPlayer(source);
        if (player == null) {
            return 0;
        }
        final LocalConfigurationAccessor localConfigurationAccessor = player.getWorld().localConfig();
        final int halfCount = count / 2;
        final int adjustedSize = size + 16;

        for (int x = -halfCount; x <= halfCount; x++) {
            for (int z = -halfCount; z <= halfCount; z++) {
                final int minX = (adjustedSize * x) - 16;
                final int minZ = (adjustedSize * z) - 16;
                final BoundingBox area = new BoundingBox(
                    minX,
                    Integer.MIN_VALUE,
                    minZ,
                    minX + size,
                    Integer.MAX_VALUE,
                    minZ + size
                );
                localConfigurationAccessor.set(area, ConfigurationContainer.sealedContainer());
            }
        }

        player.sendRichMessage("<green>Created a %sx%s grid with size %s".formatted(halfCount, halfCount, size));
        return Command.SINGLE_SUCCESS;
    }

    private void describeAreas(final Player player) {
        final Location location = player.getLocation();
        final LocalConfigurationAccessor localConfigurationAccessor = location.getWorld().localConfig();
        final List<BoundingBox> areas = new ArrayList<>(localConfigurationAccessor.getAreas(
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        ));
        if (areas.isEmpty()) {
            player.sendRichMessage("<yellow>You are not inside a local-config area");
            return;
        }

        player.sendRichMessage("<yellow>You are currently inside a area with %s local-config(s).".formatted(areas.size()));
        for (int count = 0; count < Math.min(4, areas.size()); ++count) {
            if (count != 0) {
                player.sendRichMessage("<yellow> |");
            }

            final BoundingBox area = areas.get(count);
            final ConfigurationContainer container = localConfigurationAccessor.get(area);
            player.sendRichMessage("<yellow> | X: %.0f -> %.0f".formatted(area.getMinX(), area.getMaxX()));
            player.sendRichMessage("<yellow> | Y: %.0f -> %.0f".formatted(area.getMinY(), area.getMaxY()));
            player.sendRichMessage("<yellow> | Z: %.0f -> %.0f".formatted(area.getMinZ(), area.getMaxZ()));
            player.sendRichMessage("<yellow> | ID: " + System.identityHashCode(container));
        }

        if (areas.size() > 4) {
            player.sendRichMessage("<yellow> | ... and %s more".formatted(areas.size() - 4));
        }

        player.sendRichMessage(localConfigurationAccessor.getAllAreas().size() + " total areas");
    }

    private static BoundingBox currentArea(final Player player) {
        final Location location = player.getLocation();
        final List<BoundingBox> areas = player.getWorld().localConfig().getAreas(
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
        return areas.isEmpty() ? null : areas.getFirst();
    }

    private static Player bukkitPlayer(final CommandSourceStack source) {
        final ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("Only players can use this command").withColor(CommonColors.RED));
            return null;
        }
        return player.getBukkitEntity();
    }
}
