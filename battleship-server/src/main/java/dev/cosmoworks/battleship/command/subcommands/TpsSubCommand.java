package dev.cosmoworks.battleship.command.subcommands;

import com.google.common.base.Strings;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.cosmoworks.battleship.command.SubCommand;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import me.samsuik.sakura.tps.ServerTickInformation;
import me.samsuik.sakura.tps.graph.BuiltComponentCanvas;
import me.samsuik.sakura.tps.graph.DetailedTPSGraph;
import me.samsuik.sakura.tps.graph.GraphComponents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.CommonColors;

import static net.minecraft.commands.Commands.argument;

public class TpsSubCommand implements SubCommand {
    private static final int GRAPH_WIDTH = 71;
    private static final int GRAPH_HEIGHT = 10;
    private static final Style GRAY_WITH_STRIKETHROUGH = Style.style(NamedTextColor.GRAY, TextDecoration.STRIKETHROUGH);

    @Override
    public String getDescription() {
        return "Shows this region's ticks per second";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(
        final LiteralArgumentBuilder<CommandSourceStack> base,
        final CommandBuildContext buildContext
    ) {
        return base.executes(context -> this.show(context.getSource(), -1.0, Long.MIN_VALUE))
            .then(argument("scale", DoubleArgumentType.doubleArg())
                .executes(context -> this.show(
                    context.getSource(),
                    DoubleArgumentType.getDouble(context, "scale"),
                    Long.MIN_VALUE
                ))
                .then(argument("identifier", LongArgumentType.longArg())
                    .executes(context -> this.show(
                        context.getSource(),
                        DoubleArgumentType.getDouble(context, "scale"),
                        LongArgumentType.getLong(context, "identifier")
                    ))));
    }

    @Override
    public boolean isAllowedSelfCommand() {
        return true;
    }

    @Override
    public boolean hasExtraArgs() {
        return true;
    }

    @Override
    public String getName() {
        return "tps";
    }

    private int show(final CommandSourceStack source, final double requestedScale, final long requestedIdentifier) {
        final RegionizedWorldData worldData = TickRegionScheduler.getCurrentRegionizedWorldData();
        if (worldData == null) {
            source.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "This command must be run from a ticking region"
            ).withColor(CommonColors.RED));
            return 0;
        }

        final ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Only players can use this command"
            ).withColor(CommonColors.RED));
            return 0;
        }

        final ServerTickInformation tickInformation = worldData.latestTickInformation();
        final long identifier = requestedIdentifier == Long.MIN_VALUE ? tickInformation.identifier() : requestedIdentifier;
        double scale = requestedScale;
        if (scale < 0.0) {
            scale = this.dynamicScale(worldData, identifier);
        }

        final ObjectImmutableList<ServerTickInformation> tickHistory = worldData.tickHistory(identifier - GRAPH_WIDTH, identifier);
        final DetailedTPSGraph graph = new DetailedTPSGraph(GRAPH_WIDTH, GRAPH_HEIGHT, scale, tickHistory);
        final BuiltComponentCanvas canvas = graph.plot();

        // Alternate the side bar character so compact chat does not collapse identical rows
        final int[] counter = new int[1];
        canvas.appendLeft(() -> Component.text(counter[0]++ % 2 == 0 ? ":" : ";", NamedTextColor.BLACK));
        canvas.appendRight(Component.text(":", NamedTextColor.BLACK));
        canvas.header(this.createHeaderComponent(tickInformation, identifier));
        canvas.footer(Component.text("*", NamedTextColor.DARK_GRAY)
            .append(Component.text(Strings.repeat(" ", GRAPH_WIDTH - 1), GRAY_WITH_STRIKETHROUGH))
            .append(Component.text("*")));

        for (final Component component : canvas.components()) {
            player.getBukkitEntity().sendMessage(component);
        }
        return Command.SINGLE_SUCCESS;
    }

    private double dynamicScale(final RegionizedWorldData worldData, final long identifier) {
        final ObjectImmutableList<ServerTickInformation> tickHistory = worldData.tickHistory(identifier - 5, identifier);
        final double averageTps = tickHistory.stream()
            .mapToDouble(ServerTickInformation::tps)
            .average()
            .orElse(0.0);
        return averageTps <= 0.0 ? 1.0 : 20.0 / averageTps;
    }

    private Component createHeaderComponent(final ServerTickInformation tickInformation, final long identifier) {
        final int scrollAmount = GRAPH_WIDTH / 3 * 2;
        final double memoryUsage = memoryUsage();
        final TextComponent.Builder builder = Component.text();
        builder.color(NamedTextColor.DARK_GRAY);
        builder.append(Component.text("< ")
            .clickEvent(ClickEvent.runCommand("/tps -1 " + (identifier + scrollAmount))));
        builder.append(Component.text(Strings.repeat(" ", 19), GRAY_WITH_STRIKETHROUGH));
        builder.append(Component.text(" ( "));
        builder.append(Component.text("Now: ", NamedTextColor.WHITE)
            .append(Component.text("%.1f".formatted(tickInformation.tps()), tickInformation.colour())));
        builder.appendSpace();
        builder.append(Component.text("Mem: ", NamedTextColor.WHITE)
            .append(Component.text("%.1f".formatted(memoryUsage * 100), GraphComponents.colour(1 - (float) memoryUsage))));
        builder.append(Component.text("% ) "));
        builder.append(Component.text(Strings.repeat(" ", 18), GRAY_WITH_STRIKETHROUGH));
        builder.append(Component.text(" >")
            .clickEvent(ClickEvent.runCommand("/tps -1 " + (identifier - scrollAmount))));
        return builder.build();
    }

    private static double memoryUsage() {
        final Runtime runtime = Runtime.getRuntime();
        final double free = runtime.freeMemory();
        final double max = runtime.maxMemory();
        final double alloc = runtime.totalMemory();
        return (alloc - free) / max;
    }
}
