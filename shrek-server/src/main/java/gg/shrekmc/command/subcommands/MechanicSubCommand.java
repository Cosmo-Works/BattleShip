package gg.shrekmc.command.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gg.shrekmc.ShrekServerConfig;
import gg.shrekmc.command.SubCommand;
import me.samsuik.sakura.configuration.local.CachedLocalConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.CommonColors;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class MechanicSubCommand implements SubCommand {
    private static final String MECHANIC_DUMP = """
        Mechanic Version: <yellow><mechanic_version></yellow>
        Height Parity: <yellow><height_parity></yellow>
        Tnt Spread: <yellow><tnt_spread></yellow>
        Tnt Flow: <yellow><tnt_flow></yellow>
        Sand Drops: <yellow><sand_drops></yellow>
        Redstone Implementation: <yellow><redstone_implementation></yellow>
        Paper Optimize Explosions: <yellow><broken_explosion_behaviour></yellow>
        Consistent Radius: <yellow><consistent_radius></yellow>
        Height Limit: <yellow><height_limit></yellow>
        Protection Blocks: <yellow><protection_blocks></yellow>
        Lava Flow Speed: <yellow><lava_flow_speed></yellow>
        Floating Point Fix: <yellow><floating_point_fix></yellow>""";

    @Override
    public String getDescription() {
        return "Displays information related to cannon mechanics";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base, final CommandBuildContext buildContext) {
        return base.executes(context -> this.showMechanics(context.getSource()));
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
        return "mechanic";
    }

    private int showMechanics(final CommandSourceStack source) {
        final ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("Only players can use this command").withColor(CommonColors.RED));
            return 0;
        }

        player.getBukkitEntity().sendMessage(ShrekServerConfig.mechanicInformation(this.mechanicDump(player)));
        return Command.SINGLE_SUCCESS;
    }

    private net.kyori.adventure.text.Component mechanicDump(final ServerPlayer player) {
        final ServerLevel level = player.level();
        final CachedLocalConfiguration config = level.localConfig().at(BlockPos.containing(player.position()));

        final PrimedTnt testTnt = new PrimedTnt(level, player.getX(), player.getY(), player.getZ(), null);
        final FallingBlockEntity testSand = new FallingBlockEntity(
            level,
            player.getX(),
            player.getY(),
            player.getZ(),
            Blocks.SAND.defaultBlockState()
        );

        return MiniMessage.miniMessage().deserialize(
            MECHANIC_DUMP,
            Placeholder.unparsed("mechanic_version", config.mechanicsTarget.toString()),
            Placeholder.unparsed("height_parity", String.valueOf(testSand.heightParity)),
            Placeholder.unparsed("tnt_spread", spreadLabel(testTnt.getDeltaMovement())),
            Placeholder.unparsed("tnt_flow", String.valueOf(testTnt.isPushedByFluid())),
            Placeholder.unparsed("sand_drops", String.valueOf(testSand.dropItem)),
            Placeholder.unparsed("redstone_implementation", config.redstoneBehaviour.implementation().getFriendlyName()),
            Placeholder.unparsed("broken_explosion_behaviour", String.valueOf(config.brokenPaperExplosionBehaviour)),
            Placeholder.unparsed("consistent_radius", String.valueOf(config.consistentExplosionRadius)),
            Placeholder.unparsed("height_limit", heightLimitLabel(config.heightLimit)),
            Placeholder.unparsed("protection_blocks", protectionBlocksLabel(level)),
            Placeholder.unparsed("lava_flow_speed", String.valueOf(config.lavaFlowSpeed)),
            Placeholder.unparsed("floating_point_fix", String.valueOf(config.floatingPointFix))
        );
    }

    private static String spreadLabel(final Vec3 velocity) {
        if (velocity.x() != 0.0 && velocity.y() != 0.0) {
            return "ALL";
        }
        if (velocity.y() != 0.0) {
            return "Y";
        }
        return "NONE";
    }

    private static String heightLimitLabel(final int heightLimit) {
        if (heightLimit == Integer.MIN_VALUE) {
            return "disabled";
        }
        return Integer.toString(heightLimit);
    }

    private static String protectionBlocksLabel(final ServerLevel level) {
        final List<String> blocks = level.shrekConfig().cannons.explosion.protectionBlocks;
        if (blocks.isEmpty()) {
            return "disabled";
        }
        return String.join(", ", blocks);
    }
}
