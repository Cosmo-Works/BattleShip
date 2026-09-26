package gg.shrekmc.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.shrekmc.ShrekServerConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.ReferenceCountUtil;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.GameProtocols;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@AllFeatures
class SendPathBatchTest {
    private final boolean flushOnce = ShrekServerConfig.network().flushOncePerBatch;
    private final boolean bypass = ShrekServerConfig.network().packeteventsBypass;
    private final boolean batch = ShrekServerConfig.network().batchWrites;

    @AfterEach
    void restore() {
        ShrekServerConfig.network().flushOncePerBatch = this.flushOnce;
        ShrekServerConfig.network().packeteventsBypass = this.bypass;
        ShrekServerConfig.network().batchWrites = this.batch;
    }

    private static ProtocolInfo<?> play() {
        return GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(RegistryHelper.registryAccess()));
    }

    private static int flushesForBatch(final int count) throws Exception {
        final DefaultEventLoopGroup group = new DefaultEventLoopGroup(1);
        try {
            final LocalAddress address = new LocalAddress("shrek-flush-batch-" + UUID.randomUUID());
            final Connection connection = new Connection(PacketFlow.SERVERBOUND);
            final AtomicReference<Channel> serverChannel = new AtomicReference<>();
            final CountDownLatch active = new CountDownLatch(1);
            final AtomicInteger flushes = new AtomicInteger();
            final AtomicInteger received = new AtomicInteger();
            new ServerBootstrap().group(group).channel(LocalServerChannel.class).childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(final Channel channel) {
                    Connection.configureInMemoryPipeline(channel.pipeline(), PacketFlow.SERVERBOUND);
                    connection.configurePacketHandler(channel.pipeline());
                    channel.pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
                        @Override
                        public void flush(final ChannelHandlerContext ctx) {
                            flushes.incrementAndGet();
                            ctx.flush();
                        }
                    });
                    channel.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(final ChannelHandlerContext ctx) throws Exception {
                            serverChannel.set(ctx.channel());
                            super.channelActive(ctx);
                            active.countDown();
                        }
                    });
                }
            }).bind(address).sync();
            new Bootstrap().group(group).channel(LocalChannel.class).handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                    received.incrementAndGet();
                    ReferenceCountUtil.release(msg);
                }
            }).connect(address).sync();
            assertTrue(active.await(5, TimeUnit.SECONDS));
            connection.setupOutboundProtocol(play());
            final CountDownLatch settled = new CountDownLatch(1);
            serverChannel.get().eventLoop().execute(settled::countDown);
            assertTrue(settled.await(5, TimeUnit.SECONDS));
            flushes.set(0);

            final CountDownLatch release = new CountDownLatch(1);
            serverChannel.get().eventLoop().execute(() -> {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (final InterruptedException ignored) {
                }
            });
            for (int i = 0; i < count; ++i) {
                connection.send(new ClientboundKeepAlivePacket(i), null, true);
            }
            release.countDown();
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (received.get() < count && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertEquals(count, received.get(), "all packets arrive");
            return flushes.get();
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    void batchFlushesOnceWhenEnabled() throws Exception {
        ShrekServerConfig.network().batchWrites = true;
        ShrekServerConfig.network().flushOncePerBatch = false;
        assertEquals(10, flushesForBatch(10), "one flush per packet");
        ShrekServerConfig.network().flushOncePerBatch = true;
        assertEquals(1, flushesForBatch(10), "one flush for the batch");
    }

    private record Sent(byte[] bytes, int pluginSaw) {
    }

    private static Sent send(final List<Packet<?>> packets) {
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        final io.github.retrooper.packetevents.injector.handlers.PacketEventsEncoder plugin = new io.github.retrooper.packetevents.injector.handlers.PacketEventsEncoder();
        final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel channel) {
                Connection.configureSerialization(channel.pipeline(), PacketFlow.SERVERBOUND, false, null);
                connection.configurePacketHandler(channel.pipeline());
                channel.pipeline().addAfter(HandlerNames.PREPENDER, HandlerNames.COMPRESS, new CompressionEncoder(
                    com.velocitypowered.natives.util.Natives.compress.get().create(4), 64
                ));

                channel.pipeline().addAfter(HandlerNames.COMPRESS, "plugin-encoder", plugin);
            }
        });
        connection.setupOutboundProtocol(play());
        channel.runPendingTasks();
        for (final Packet<?> packet : packets) {
            connection.send(packet);
        }
        channel.runPendingTasks();
        channel.flushOutbound();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuf buf;
        while ((buf = channel.readOutbound()) != null) {
            out.writeBytes(ByteBufUtil.getBytes(buf));
            buf.release();
        }
        channel.finishAndReleaseAll();
        return new Sent(out.toByteArray(), plugin.byteBufsSeen);
    }

    @Test
    void bypassedPacketsSkipPluginHandlersWithIdenticalBytes() {
        final List<Packet<?>> packets = new ArrayList<>();
        for (int i = 0; i < 5; ++i) {
            packets.add(new ClientboundSetTimePacket(1000L * i, java.util.Map.of()));
            packets.add(new ClientboundKeepAlivePacket(i));
        }
        ShrekServerConfig.network().packeteventsBypass = false;
        final Sent plain = send(packets);
        assertEquals(10, plain.pluginSaw());

        ShrekServerConfig.network().packeteventsBypass = true;
        final Sent bypassed = send(packets);
        assertEquals(5, bypassed.pluginSaw(), "only the keep-alives go through the plugin handler");
        assertArrayEquals(plain.bytes(), bypassed.bytes());
    }

    @Test
    void defaultBypassListNamesClientboundPlayPackets() {
        final java.util.Set<net.minecraft.resources.Identifier> play = new java.util.HashSet<>();
        GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type, id) -> play.add(type.id()));
        final var types = PacketEventsBypass.types(PacketEventsBypass.DEFAULT_TYPES);
        assertEquals(PacketEventsBypass.DEFAULT_TYPES.size(), types.size());
        for (final var id : types) {
            assertTrue(play.contains(id), () -> id + " is not a clientbound play packet");
        }

        for (final String kept : List.of("minecraft:add_entity", "minecraft:set_passengers", "minecraft:remove_entities",
            "minecraft:player_info_update", "minecraft:login", "minecraft:respawn", "minecraft:start_configuration", "minecraft:bundle_delimiter")) {
            assertTrue(!types.contains(net.minecraft.resources.Identifier.parse(kept)), kept);
        }
    }
}
