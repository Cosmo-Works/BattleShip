package gg.shrekmc.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

@AllFeatures
class ProtocolSwitchOrderTest {
    @FunctionalInterface
    private interface Script {
        int run(Connection connection, ChannelFutureListener capture);
    }

    @Test
    void packetsAfterALoginToConfigurationSwitchAreWrittenAfterIt() throws Exception {
        run(connection -> connection.setupOutboundProtocol(LoginProtocols.CLIENTBOUND), (connection, capture) -> {
            connection.send(new ClientboundLoginFinishedPacket(new GameProfile(UUID.randomUUID(), "shrek"), UUID.randomUUID()), capture);
            connection.setupOutboundProtocol(ConfigurationProtocols.CLIENTBOUND);
            for (int i = 0; i < 5; ++i) {
                connection.send(new ClientboundKeepAlivePacket(i), capture);
            }
            return 6;
        });
    }

    @Test
    void batchedPlayPacketsAndAPlayToConfigurationSwitchKeepTheirOrder() throws Exception {
        final net.minecraft.network.ProtocolInfo<?> play = net.minecraft.network.protocol.game.GameProtocols.CLIENTBOUND_TEMPLATE
            .bind(net.minecraft.network.RegistryFriendlyByteBuf.decorator(org.bukkit.support.RegistryHelper.registryAccess()));
        run(connection -> connection.setupOutboundProtocol(play), (connection, capture) -> {
            for (int i = 0; i < 5; ++i) {
                connection.send(new ClientboundKeepAlivePacket(i), capture);
            }
            connection.send(net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket.INSTANCE, capture);
            connection.setupOutboundProtocol(ConfigurationProtocols.CLIENTBOUND);
            for (int i = 0; i < 5; ++i) {
                connection.send(new ClientboundKeepAlivePacket(i), capture);
            }
            connection.setupOutboundProtocol(play);
            for (int i = 0; i < 5; ++i) {
                connection.send(new ClientboundKeepAlivePacket(i), capture);
            }
            return 16;
        });
    }

    private static void run(final java.util.function.Consumer<Connection> setup, final Script script) throws Exception {
        final DefaultEventLoopGroup group = new DefaultEventLoopGroup(1);
        try {
            final LocalAddress address = new LocalAddress("shrek-protocol-switch-" + UUID.randomUUID());
            final Connection connection = new Connection(PacketFlow.SERVERBOUND);
            final AtomicReference<Channel> serverChannel = new AtomicReference<>();
            final CountDownLatch active = new CountDownLatch(1);
            final AtomicInteger framesReceived = new AtomicInteger();

            new ServerBootstrap().group(group).channel(LocalServerChannel.class).childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(final Channel channel) {
                    Connection.configureInMemoryPipeline(channel.pipeline(), PacketFlow.SERVERBOUND);
                    connection.configurePacketHandler(channel.pipeline());
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
                    framesReceived.incrementAndGet();
                    ReferenceCountUtil.release(msg);
                }
            }).connect(address).sync();
            assertTrue(active.await(5, TimeUnit.SECONDS));

            setup.accept(connection);
            final List<Throwable> failures = new CopyOnWriteArrayList<>();
            final ChannelFutureListener capture = future -> {
                if (!future.isSuccess()) {
                    failures.add(future.cause());
                }
            };

            final CountDownLatch release = new CountDownLatch(1);
            serverChannel.get().eventLoop().execute(() -> {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (final InterruptedException ignored) {
                }
            });

            final int expected = script.run(connection, capture);
            release.countDown();

            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (framesReceived.get() < expected && failures.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(failures.isEmpty(), () -> "writes failed: " + failures);
            assertTrue(framesReceived.get() >= expected, "frames: " + framesReceived.get());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
    }
}
