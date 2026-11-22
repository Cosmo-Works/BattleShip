package dev.cosmoworks.battleship.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cosmoworks.battleship.BattleshipServerConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.Random;
import me.samsuik.sakura.player.visibility.FilterOrModifyPackets;
import me.samsuik.sakura.player.visibility.PlayerVisibilitySettings;
import me.samsuik.sakura.player.visibility.VisibilityState;
import me.samsuik.sakura.player.visibility.VisibilityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@AllFeatures
class SpawnerVisibilitySharedPacketTest {
    private final boolean before = BattleshipServerConfig.network().shareChunkFrames;

    @AfterEach
    void restore() {
        BattleshipServerConfig.network().shareChunkFrames = this.before;
    }

    private static ClientboundLevelChunkWithLightPacket chunkWithSpawner() {
        final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryHelper.registryAccess());
        buf.writeInt(2);
        buf.writeInt(3);
        buf.writeVarInt(0);
        final byte[] data = new byte[3000];
        new Random(1).nextBytes(data);
        buf.writeVarInt(data.length);
        buf.writeBytes(data);
        buf.writeVarInt(2);
        writeBlockEntity(buf, 0x12, 64, BlockEntityTypes.MOB_SPAWNER);
        writeBlockEntity(buf, 0x34, 65, BlockEntityTypes.CHEST);
        for (int i = 0; i < 4; ++i) {
            buf.writeVarInt(0);
        }
        buf.writeVarInt(0);
        buf.writeVarInt(0);
        final ClientboundLevelChunkWithLightPacket packet = ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(buf);
        buf.release();
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:mob_spawner");
        packet.chunkData().getExtraPackets().add(new ClientboundBlockEntityDataPacket(new BlockPos(33, 70, 49), BlockEntityTypes.MOB_SPAWNER, tag));
        return packet;
    }

    private static void writeBlockEntity(final RegistryFriendlyByteBuf buf, final int packedXZ, final int y, final BlockEntityType<?> type) {
        buf.writeByte(packedXZ);
        buf.writeShort(y);
        ByteBufCodecs.registry(Registries.BLOCK_ENTITY_TYPE).encode(buf, type);
        ByteBufCodecs.OPTIONAL_COMPOUND_TAG.encode(buf, Optional.empty());
    }

    private static boolean hasSpawner(final ClientboundLevelChunkWithLightPacket packet) {
        return packet.chunkData().battleship$hasBlockEntities(type -> type == BlockEntityTypes.MOB_SPAWNER);
    }

    private static PlayerVisibilitySettings hidingSpawners() {
        final PlayerVisibilitySettings settings = new PlayerVisibilitySettings();
        settings.set(VisibilityTypes.SPAWNERS, VisibilityState.OFF);
        assertTrue(settings.isToggled(VisibilityTypes.SPAWNERS));
        return settings;
    }

    private static final class Player {
        final PlayerVisibilitySettings settings;
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        final EmbeddedChannel channel;

        Player(final PlayerVisibilitySettings settings) {
            this.settings = settings;
            this.channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(final Channel channel) {
                    Connection.configureSerialization(channel.pipeline(), PacketFlow.SERVERBOUND, false, null);
                    Player.this.connection.configurePacketHandler(channel.pipeline());
                    channel.pipeline().addAfter(HandlerNames.PREPENDER, HandlerNames.COMPRESS, new CompressionEncoder(
                        com.velocitypowered.natives.util.Natives.compress.get().create(4), 256
                    ));
                }
            });
            this.connection.setupOutboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(RegistryHelper.registryAccess())));
            this.channel.runPendingTasks();
        }

        byte[] send(final Packet<?> packet) {
            final Packet<?> filtered = this.settings.playerModified() ? FilterOrModifyPackets.filterOrModifyPacket(packet, this.settings) : packet;
            if (filtered != null) {
                this.connection.send(filtered);
            }
            this.channel.runPendingTasks();
            this.channel.flushOutbound();
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteBuf buf;
            while ((buf = this.channel.readOutbound()) != null) {
                out.writeBytes(ByteBufUtil.getBytes(buf));
                buf.release();
            }
            return out.toByteArray();
        }
    }

    @Test
    void hidingPlayerGetsACopyAndThePacketKeepsItsSpawner() {
        final ClientboundLevelChunkWithLightPacket packet = chunkWithSpawner();
        final Packet<?> filtered = FilterOrModifyPackets.filterOrModifyPacket(packet, hidingSpawners());
        assertNotSame(packet, filtered);
        assertTrue(hasSpawner(packet), "the shared packet still has its spawner");
        assertTrue(packet.chunkData().getExtraPackets().size() == 1, "and its extra spawner packet");
        final ClientboundLevelChunkWithLightPacket copy = (ClientboundLevelChunkWithLightPacket) filtered;
        assertFalse(hasSpawner(copy));
        assertTrue(copy.chunkData().battleship$hasBlockEntities(type -> type == BlockEntityTypes.CHEST), "other block entities stay");
        assertTrue(copy.chunkData().getExtraPackets().isEmpty());
        assertSame(packet.chunkData().battleship$buffer(), copy.chunkData().battleship$buffer(), "sections are shared, not copied");

        assertSame(packet, FilterOrModifyPackets.filterOrModifyPacket(packet, new PlayerVisibilitySettings()));
        assertNull(FilterOrModifyPackets.filterOrModifyPacket(packet.chunkData().getExtraPackets().getFirst(), hidingSpawners()));
    }

    @Test
    void sameCachedChunkToTwoPlayersInEitherOrder() {
        BattleshipServerConfig.network().shareChunkFrames = false;
        final ClientboundLevelChunkWithLightPacket reference = chunkWithSpawner();
        final byte[] withSpawner = new Player(new PlayerVisibilitySettings()).send(reference);
        final byte[] withoutSpawner = new Player(new PlayerVisibilitySettings()).send(
            FilterOrModifyPackets.filterOrModifyPacket(reference, hidingSpawners())
        );
        assertFalse(java.util.Arrays.equals(withSpawner, withoutSpawner));

        for (final boolean sharedFrames : new boolean[] {false, true}) {
            BattleshipServerConfig.network().shareChunkFrames = sharedFrames;

            final ClientboundLevelChunkWithLightPacket cached = chunkWithSpawner();
            assertArrayEquals(withoutSpawner, new Player(hidingSpawners()).send(cached), "hider, first");
            assertArrayEquals(withSpawner, new Player(new PlayerVisibilitySettings()).send(cached), "normal player after a hider");
            assertArrayEquals(withoutSpawner, new Player(hidingSpawners()).send(cached), "hider after the frame was kept");
            assertArrayEquals(withSpawner, new Player(new PlayerVisibilitySettings()).send(cached), "normal player, shared frame");
            assertTrue(hasSpawner(cached));

            final ClientboundLevelChunkWithLightPacket other = chunkWithSpawner();
            assertArrayEquals(withSpawner, new Player(new PlayerVisibilitySettings()).send(other), "normal player, first");
            assertArrayEquals(withoutSpawner, new Player(hidingSpawners()).send(other), "hider never gets the shared frame");
            assertTrue(hasSpawner(other));
        }
    }
}
