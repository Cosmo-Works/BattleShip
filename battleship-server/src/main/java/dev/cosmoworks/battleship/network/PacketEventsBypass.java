package dev.cosmoworks.battleship.network;

import dev.cosmoworks.battleship.BattleshipServerConfig;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;

public final class PacketEventsBypass {
    public static final class Encoded extends io.netty.buffer.DefaultByteBufHolder {
        public Encoded(final ByteBuf buf) {
            super(buf);
        }
    }

    private static volatile List<String> parsedFrom;
    private static volatile Set<Identifier> types = Set.of();

    private PacketEventsBypass() {
    }

    public static boolean bypass(final ChannelHandlerContext ctx, final Packet<?> packet) {
        final BattleshipServerConfig.Network config = BattleshipServerConfig.network();
        if (!config.packeteventsBypass) {
            return false;
        }
        if (packet.type().flow() != PacketFlow.CLIENTBOUND || !types(config.packeteventsBypassTypes).contains(packet.type().id())) {
            return false;
        }
        return ctx.pipeline().get(HandlerNames.COMPRESS) instanceof CompressionEncoder
            && OutboundOrder.maySkipPluginHandlers(ctx.channel());
    }

    static Set<Identifier> types(final List<String> configured) {
        if (configured != parsedFrom) {
            final Set<Identifier> parsed = new HashSet<>();
            for (final String id : configured) {
                final Identifier identifier = Identifier.tryParse(id);
                if (identifier != null) {
                    parsed.add(identifier);
                }
            }
            types = Set.copyOf(parsed);
            parsedFrom = configured;
        }
        return types;
    }

    public static final List<String> DEFAULT_TYPES = List.of(
        "minecraft:move_entity_pos", "minecraft:move_entity_pos_rot", "minecraft:move_entity_rot", "minecraft:rotate_head",
        "minecraft:entity_position_sync", "minecraft:teleport_entity", "minecraft:set_entity_motion", "minecraft:set_entity_data",
        "minecraft:set_equipment", "minecraft:update_attributes", "minecraft:entity_event", "minecraft:animate",
        "minecraft:swing_animation", "minecraft:hurt_animation", "minecraft:damage_event", "minecraft:take_item_entity",
        "minecraft:block_update", "minecraft:section_blocks_update", "minecraft:block_event", "minecraft:block_destruction",
        "minecraft:block_entity_data", "minecraft:level_chunk_with_light", "minecraft:light_update", "minecraft:chunks_biomes",
        "minecraft:forget_level_chunk", "minecraft:set_chunk_cache_center", "minecraft:chunk_batch_start",
        "minecraft:chunk_batch_finished", "minecraft:sound", "minecraft:sound_entity", "minecraft:level_particles",
        "minecraft:level_event", "minecraft:set_time", "minecraft:container_set_slot", "minecraft:container_set_content",
        "minecraft:container_set_data", "minecraft:set_player_inventory", "minecraft:set_cursor_item",
        "minecraft:update_mob_effect", "minecraft:remove_mob_effect", "minecraft:set_health", "minecraft:set_experience"
    );
}
