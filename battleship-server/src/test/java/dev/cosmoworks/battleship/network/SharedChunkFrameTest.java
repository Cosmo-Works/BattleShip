package dev.cosmoworks.battleship.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cosmoworks.battleship.BattleshipServerConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@AllFeatures
class SharedChunkFrameTest {
    private final boolean before = BattleshipServerConfig.network().shareChunkFrames;

    @AfterEach
    void restore() {
        BattleshipServerConfig.network().shareChunkFrames = this.before;
    }

    private static ClientboundLevelChunkWithLightPacket packet(final int seed, final int size) {
        final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryHelper.registryAccess());
        buf.writeInt(seed);
        buf.writeInt(-seed);
        buf.writeVarInt(0);
        final byte[] data = new byte[size];
        final Random random = new Random(seed);
        for (int i = 0; i < data.length; ++i) {
            data[i] = (byte) random.nextInt(6);
        }
        buf.writeVarInt(data.length);
        buf.writeBytes(data);
        buf.writeVarInt(0);
        for (int i = 0; i < 4; ++i) {
            buf.writeVarInt(0);
        }
        buf.writeVarInt(0);
        buf.writeVarInt(0);
        final ClientboundLevelChunkWithLightPacket packet = ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(buf);
        buf.release();
        return packet;
    }

    private static final class Server {
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        final EmbeddedChannel channel;

        Server(final int threshold) {
            this.channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(final Channel channel) {
                    Connection.configureSerialization(channel.pipeline(), PacketFlow.SERVERBOUND, false, null);
                    Server.this.connection.configurePacketHandler(channel.pipeline());
                    channel.pipeline().addAfter(HandlerNames.PREPENDER, HandlerNames.COMPRESS, new CompressionEncoder(
                        com.velocitypowered.natives.util.Natives.compress.get().create(4), threshold
                    ));
                }
            });
            final ProtocolInfo<?> play = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(RegistryHelper.registryAccess()));
            this.connection.setupOutboundProtocol(play);
            this.channel.runPendingTasks();
        }

        byte[] send(final ClientboundLevelChunkWithLightPacket packet) {
            this.connection.send(packet);
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
    void sharedFrameIsByteIdenticalToPerConnectionEncoding() {
        final ClientboundLevelChunkWithLightPacket packet = packet(7, 40_000);

        BattleshipServerConfig.network().shareChunkFrames = false;
        final byte[] plain = new Server(256).send(packet);
        assertTrue(plain.length > 0);
        assertNull(packet.chunkData().battleship$frame, "switch off: nothing kept");

        BattleshipServerConfig.network().shareChunkFrames = true;
        final byte[] first = new Server(256).send(packet);
        assertNotNull(packet.chunkData().battleship$frame, "the first connection keeps the compressed frame");
        final byte[] second = new Server(256).send(packet);
        final byte[] third = new Server(256).send(packet);

        assertArrayEquals(plain, first);
        assertArrayEquals(plain, second);
        assertArrayEquals(plain, third);
    }

    @Test
    void laterConnectionsReallyUseTheKeptBytes() {
        BattleshipServerConfig.network().shareChunkFrames = true;
        final ClientboundLevelChunkWithLightPacket packet = packet(3, 5_000);
        new Server(256).send(packet);
        final SharedFrames.Frame kept = packet.chunkData().battleship$frame;
        assertNotNull(kept);

        final byte[] marker = {0, 1, 2, 3, 4, 5, 6, 7};
        packet.chunkData().battleship$frame = new SharedFrames.Frame(packet, kept.threshold(), kept.nativeCompressor(), marker);
        final byte[] sent = new Server(256).send(packet);
        assertArrayEquals(new byte[] {8, 0, 1, 2, 3, 4, 5, 6, 7}, sent, "length prefix + the kept bytes");
    }

    @Test
    void otherThresholdEncodesAgain() {
        BattleshipServerConfig.network().shareChunkFrames = false;
        final ClientboundLevelChunkWithLightPacket packet = packet(11, 20_000);
        final byte[] plain512 = new Server(512).send(packet);

        BattleshipServerConfig.network().shareChunkFrames = true;
        new Server(256).send(packet);
        assertArrayEquals(plain512, new Server(512).send(packet));
        assertArrayEquals(plain512, new Server(512).send(packet));
    }

    @Test
    void smallPacketsBelowTheThresholdMatchToo() {
        BattleshipServerConfig.network().shareChunkFrames = false;
        final ClientboundLevelChunkWithLightPacket packet = packet(5, 20);
        final byte[] plain = new Server(256).send(packet);
        BattleshipServerConfig.network().shareChunkFrames = true;
        assertArrayEquals(plain, new Server(256).send(packet));
        assertArrayEquals(plain, new Server(256).send(packet));
    }
}
