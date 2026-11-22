package dev.cosmoworks.battleship.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.cosmoworks.battleship.BattleshipServerConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.world.phys.Vec3;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@AllFeatures
class SharedBroadcastFrameTest {
    private final boolean before = BattleshipServerConfig.network().shareBroadcastFrames;
    private final boolean bypassBefore = BattleshipServerConfig.network().packeteventsBypass;

    @AfterEach
    void restore() {
        BattleshipServerConfig.network().shareBroadcastFrames = this.before;
        BattleshipServerConfig.network().packeteventsBypass = this.bypassBefore;
    }

    private static final class Server {
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        final io.github.retrooper.packetevents.injector.handlers.PacketEventsEncoder plugin = new io.github.retrooper.packetevents.injector.handlers.PacketEventsEncoder();
        final EmbeddedChannel channel;

        Server(final int threshold, final Locale locale) {
            this.channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(final Channel channel) {
                    Connection.configureSerialization(channel.pipeline(), PacketFlow.SERVERBOUND, false, null);
                    Server.this.connection.configurePacketHandler(channel.pipeline());
                    channel.pipeline().addAfter(HandlerNames.PREPENDER, HandlerNames.COMPRESS, new CompressionEncoder(
                        com.velocitypowered.natives.util.Natives.compress.get().create(4), threshold
                    ));
                    channel.pipeline().addAfter(HandlerNames.COMPRESS, "plugin-encoder", Server.this.plugin);
                }
            });
            if (locale != null) {
                this.channel.attr(io.papermc.paper.adventure.PaperAdventure.LOCALE_ATTRIBUTE).set(locale);
            }
            this.connection.setupOutboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(RegistryHelper.registryAccess())));
            this.channel.runPendingTasks();
        }

        byte[] drain() {
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

    private static List<byte[]> broadcast(final List<Server> servers, final List<Packet<?>> packets) {
        for (final Packet<?> packet : packets) {
            final SharedFrames.Broadcast broadcast = SharedFrames.begin(packet, servers.size());
            try {
                for (final Server server : servers) {
                    server.connection.send(packet);
                }
            } finally {
                SharedFrames.end(broadcast);
            }
        }
        final List<byte[]> ret = new ArrayList<>();
        for (final Server server : servers) {
            ret.add(server.drain());
        }
        return ret;
    }

    private static List<Packet<?>> packets() {
        return List.of(
            new ClientboundSetTimePacket(123456L, Map.of()),
            new ClientboundSetEntityMotionPacket(42, new Vec3(0.25, -0.5, 1.0)),
            new ClientboundSetHealthPacket(17.5F, 12, 3.0F),
            new ClientboundKeepAlivePacket(7)
        );
    }

    private static List<Server> servers(final int count, final int threshold) {
        final List<Server> servers = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            servers.add(new Server(threshold, null));
        }
        return servers;
    }

    private void checkIdentical(final int threshold) {
        final List<Packet<?>> packets = packets();
        BattleshipServerConfig.network().shareBroadcastFrames = false;
        final byte[] plain = broadcast(servers(1, threshold), packets).getFirst();

        BattleshipServerConfig.network().shareBroadcastFrames = true;
        final List<Server> servers = servers(5, threshold);
        final List<byte[]> shared = broadcast(servers, packets);
        for (final byte[] bytes : shared) {
            assertArrayEquals(plain, bytes);
        }

        assertEquals(4, servers.getFirst().plugin.byteBufsSeen);
        for (int i = 1; i < servers.size(); ++i) {
            assertEquals(1, servers.get(i).plugin.byteBufsSeen, "connection " + i + " used the shared frames");
        }
    }

    @Test
    void sharedFramesAreByteIdenticalBelowTheThreshold() {
        checkIdentical(256);
    }

    @Test
    void sharedFramesAreByteIdenticalWhenCompressed() {
        checkIdentical(1);
    }

    @Test
    void sharedFramesAreByteIdenticalWithPacketEventsBypassOn() {
        BattleshipServerConfig.network().packeteventsBypass = true;
        final List<Packet<?>> packets = packets();
        BattleshipServerConfig.network().shareBroadcastFrames = false;
        final byte[] plain = broadcast(servers(1, 64), packets).getFirst();
        BattleshipServerConfig.network().shareBroadcastFrames = true;
        for (final byte[] bytes : broadcast(servers(4, 64), packets)) {
            assertArrayEquals(plain, bytes);
        }
    }

    @Test
    void otherLanguageAndThresholdEncodeForThemselves() {
        final List<Packet<?>> packets = packets();
        BattleshipServerConfig.network().shareBroadcastFrames = false;
        final byte[] plainGerman = broadcast(List.of(new Server(256, Locale.GERMAN)), packets).getFirst();
        final byte[] plainSmall = broadcast(List.of(new Server(1, null)), packets).getFirst();

        BattleshipServerConfig.network().shareBroadcastFrames = true;
        final Server english = new Server(256, Locale.ENGLISH);
        final Server german = new Server(256, Locale.GERMAN);
        final Server small = new Server(1, null);
        final List<byte[]> bytes = broadcast(List.of(english, german, small), packets);
        assertArrayEquals(plainGerman, bytes.get(1));
        assertArrayEquals(plainSmall, bytes.get(2));
        assertEquals(4, german.plugin.byteBufsSeen, "a different language encodes for itself");
        assertEquals(4, small.plugin.byteBufsSeen, "a different threshold encodes for itself");
    }

    @Test
    void notMarkedOutsideABroadcastOrForOneViewer() {
        BattleshipServerConfig.network().shareBroadcastFrames = true;
        final Packet<?> packet = new ClientboundSetTimePacket(1L, Map.of());
        assertNull(SharedFrames.current(packet));
        assertNull(SharedFrames.begin(packet, 1));
        final SharedFrames.Broadcast broadcast = SharedFrames.begin(packet, 3);
        try {
            assertNull(SharedFrames.current(new ClientboundSetTimePacket(1L, Map.of())), "only the same packet object");
        } finally {
            SharedFrames.end(broadcast);
        }
        assertNull(SharedFrames.current(packet));
        assertNull(SharedFrames.begin(new ClientboundKeepAlivePacket(1), 3), "types outside the list are never shared");
    }
}
