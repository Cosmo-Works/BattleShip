package gg.shrekmc.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.shrekmc.ShrekServerConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.ReferenceCountUtil;
import io.github.retrooper.packetevents.injector.handlers.PacketEventsEncoder;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Inflater;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@AllFeatures
class JoinWriteOrderTest {
    private final ShrekServerConfig.Network network = ShrekServerConfig.network();
    private final boolean[] saved = {
        this.network.fastSendPath, this.network.batchWrites, this.network.flushOncePerBatch, this.network.packeteventsBypass,
        this.network.shareBroadcastFrames, this.network.shareChunkFrames, this.network.useLazyExecuteWhenNotFlushing
    };

    @AfterEach
    void restore() {
        this.network.fastSendPath = this.saved[0];
        this.network.batchWrites = this.saved[1];
        this.network.flushOncePerBatch = this.saved[2];
        this.network.packeteventsBypass = this.saved[3];
        this.network.shareBroadcastFrames = this.saved[4];
        this.network.shareChunkFrames = this.saved[5];
        this.network.useLazyExecuteWhenNotFlushing = this.saved[6];
    }

    private static ProtocolInfo<?> play() {
        return GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(RegistryHelper.registryAccess()));
    }

    private static ClientboundLoginPacket login() {
        final var dimension = RegistryHelper.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        return new ClientboundLoginPacket(
            1, false, Set.of(Level.OVERWORLD), 500, 10, 10, false, true, false,
            new CommonPlayerSpawnInfo(dimension, Level.OVERWORLD, 0L, GameType.SURVIVAL, Optional.empty(), false, false, Optional.empty(), 0, 63),
            false, false
        );
    }

    private static ClientboundEntityEventPacket entityEvent(final int entityId, final byte event) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entityId);
        buf.writeByte(event);
        final ClientboundEntityEventPacket packet = ClientboundEntityEventPacket.STREAM_CODEC.decode(buf);
        buf.release();
        return packet;
    }

    private static ClientboundLevelChunkWithLightPacket chunk(final int seed) {
        final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryHelper.registryAccess());
        buf.writeInt(seed);
        buf.writeInt(-seed);
        buf.writeVarInt(0);
        final byte[] data = new byte[2000];
        new Random(seed).nextBytes(data);
        buf.writeVarInt(data.length);
        buf.writeBytes(data);
        buf.writeVarInt(0);
        for (int i = 0; i < 6; ++i) {
            buf.writeVarInt(0);
        }
        final ClientboundLevelChunkWithLightPacket packet = ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(buf);
        buf.release();
        return packet;
    }

    private record Send(Packet<?> packet, boolean flush, boolean broadcast) {
    }

    private static List<Send> joinPackets() {
        final ClientboundLevelChunkWithLightPacket sharedChunk = chunk(3);
        final List<Send> sends = new ArrayList<>();
        sends.add(new Send(login(), false, false));
        sends.add(new Send(new ClientboundKeepAlivePacket(1), false, false));
        sends.add(new Send(entityEvent(1, (byte) 28), false, false));
        sends.add(new Send(new ClientboundSetHealthPacket(20F, 20, 5F), false, false));
        sends.add(new Send(new ClientboundSetTimePacket(1000L, Map.of()), true, false));
        sends.add(new Send(sharedChunk, false, false));
        sends.add(new Send(sharedChunk, false, false));
        sends.add(new Send(new ClientboundSetTimePacket(1001L, Map.of()), false, true));
        sends.add(new Send(new ClientboundKeepAlivePacket(2), true, false));
        return sends;
    }

    private static List<Send> afterJoinPackets() {
        final List<Send> sends = new ArrayList<>();
        sends.add(new Send(new ClientboundSetTimePacket(2000L, Map.of()), false, true));
        sends.add(new Send(entityEvent(7, (byte) 2), false, true));
        sends.add(new Send(chunk(5), false, false));
        sends.add(new Send(new ClientboundKeepAlivePacket(3), true, false));
        return sends;
    }

    private static final class OtherViewer {
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        final io.netty.channel.embedded.EmbeddedChannel channel;

        OtherViewer() {
            this.channel = new io.netty.channel.embedded.EmbeddedChannel(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(final Channel channel) {
                    Connection.configureSerialization(channel.pipeline(), PacketFlow.SERVERBOUND, false, null);
                    OtherViewer.this.connection.configurePacketHandler(channel.pipeline());
                    channel.pipeline().addAfter(HandlerNames.PREPENDER, HandlerNames.COMPRESS, new CompressionEncoder(
                        com.velocitypowered.natives.util.Natives.compress.get().create(4), 256
                    ));
                }
            });
            this.connection.setupOutboundProtocol(play());
            this.channel.runPendingTasks();
        }

        void receive(final Packet<?> packet) {
            this.connection.send(packet);
            this.channel.runPendingTasks();
            this.channel.flushOutbound();
            this.channel.releaseOutbound();
        }
    }

    private static void send(final Connection connection, final OtherViewer other, final Send send) {
        final SharedFrames.Broadcast broadcast = send.broadcast() ? SharedFrames.begin(send.packet(), 2) : null;
        try {
            if (send.broadcast() || send.packet() instanceof ClientboundLevelChunkWithLightPacket) {
                other.receive(send.packet());
            }
            connection.send(send.packet(), null, send.flush());
        } finally {
            SharedFrames.end(broadcast);
        }
    }

    private static List<Identifier> expected(final List<Send> sends) {
        final List<Identifier> ids = new ArrayList<>();
        for (final Send send : sends) {
            ids.add(send.packet().type().id());
        }
        return ids;
    }

    private static boolean consolidateFlushes;

    private static List<Identifier> join(final boolean freeze) throws Exception {
        final DefaultEventLoopGroup group = new DefaultEventLoopGroup(1);
        try {
            final LocalAddress address = new LocalAddress("shrek-join-order-" + UUID.randomUUID());
            final Connection connection = new Connection(PacketFlow.SERVERBOUND);
            final PacketEventsEncoder packetEvents = new PacketEventsEncoder();
            final AtomicReference<Channel> serverChannel = new AtomicReference<>();
            final CountDownLatch active = new CountDownLatch(1);
            final ByteArrayOutputStream received = new ByteArrayOutputStream();

            new ServerBootstrap().group(group).channel(LocalServerChannel.class).childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(final Channel channel) {
                    Connection.configureSerialization(channel.pipeline(), PacketFlow.SERVERBOUND, false, null);
                    connection.configurePacketHandler(channel.pipeline());
                    channel.pipeline().addAfter(HandlerNames.PREPENDER, HandlerNames.COMPRESS, new CompressionEncoder(
                        com.velocitypowered.natives.util.Natives.compress.get().create(4), 256
                    ));
                    channel.pipeline().addAfter(HandlerNames.COMPRESS, "pe-encoder", packetEvents);
                    channel.pipeline().addFirst(FlushConsolidation.newHandler(consolidateFlushes));
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
                    final ByteBuf buf = (ByteBuf) msg;
                    synchronized (received) {
                        final byte[] bytes = new byte[buf.readableBytes()];
                        buf.readBytes(bytes);
                        received.writeBytes(bytes);
                    }
                    ReferenceCountUtil.release(msg);
                }
            }).connect(address).sync();
            assertTrue(active.await(5, TimeUnit.SECONDS));
            final Channel channel = serverChannel.get();

            connection.setupOutboundProtocol(ConfigurationProtocols.CLIENTBOUND);
            connection.send(net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket.INSTANCE);
            settle(channel);
            settle(channel);
            synchronized (received) {
                received.reset();
            }

            final CountDownLatch release = new CountDownLatch(1);
            channel.eventLoop().execute(() -> {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (final InterruptedException ignored) {
                }
            });

            connection.setupOutboundProtocol(play());
            if (freeze) {
                channel.eventLoop().execute(() -> packetEvents.setHold(true));
            }

            final List<Send> join = joinPackets();
            final OtherViewer other = new OtherViewer();
            final Thread region = new Thread(() -> {
                for (final Send send : join) {
                    send(connection, other, send);
                }
            }, "test region thread");
            region.start();
            region.join();

            if (freeze) {
                channel.eventLoop().execute(() -> packetEvents.setHold(false));
            }
            final List<Send> after = afterJoinPackets();
            for (final Send send : after) {
                send(connection, other, send);
            }
            release.countDown();

            final List<Identifier> expected = new ArrayList<>(expected(join));
            expected.addAll(expected(after));
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            List<Identifier> got = List.of();
            while (System.nanoTime() < deadline) {
                settle(channel);
                synchronized (received) {
                    got = decode(received.toByteArray());
                }
                if (got.size() >= expected.size()) {
                    break;
                }
                Thread.sleep(5);
            }
            channel.eventLoop().submit(channel::flush).sync();
            return got;
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
    }

    private static void settle(final Channel channel) throws Exception {
        channel.eventLoop().submit(() -> {}).sync();
    }

    private static final Map<Integer, Identifier> PLAY_IDS = new HashMap<>();

    static {
        GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type, id) -> PLAY_IDS.put(id, type.id()));
    }

    private static List<Identifier> decode(final byte[] bytes) throws Exception {
        final ByteBuf in = Unpooled.wrappedBuffer(bytes);
        final List<Identifier> ids = new ArrayList<>();
        while (in.isReadable()) {
            final int mark = in.readerIndex();
            final int length;
            try {
                length = VarInt.read(in);
            } catch (final RuntimeException incomplete) {
                break;
            }
            if (in.readableBytes() < length) {
                in.readerIndex(mark);
                break;
            }
            final ByteBuf frame = in.readSlice(length);
            final int dataLength = VarInt.read(frame);
            final ByteBuf data;
            if (dataLength == 0) {
                data = frame;
            } else {
                final byte[] compressed = new byte[frame.readableBytes()];
                frame.readBytes(compressed);
                final Inflater inflater = new Inflater();
                inflater.setInput(compressed);
                final byte[] out = new byte[dataLength];
                int done = 0;
                while (done < dataLength && !inflater.finished()) {
                    done += inflater.inflate(out, done, dataLength - done);
                }
                inflater.end();
                data = Unpooled.wrappedBuffer(out);
            }
            ids.add(PLAY_IDS.get(VarInt.read(data)));
        }
        return ids;
    }

    @Test
    void joinWritesInSendOrderUnderEverySwitchCombination() throws Exception {
        final List<Identifier> expected = new ArrayList<>(expected(joinPackets()));
        expected.addAll(expected(afterJoinPackets()));
        final Identifier loginId = login().type().id();
        for (int mask = 0; mask < 1 << 8; ++mask) {
            consolidateFlushes = (mask & 128) != 0;
            this.network.fastSendPath = (mask & 1) != 0;
            this.network.batchWrites = (mask & 2) != 0;
            this.network.flushOncePerBatch = (mask & 4) != 0;
            this.network.packeteventsBypass = (mask & 8) != 0;
            this.network.shareBroadcastFrames = (mask & 16) != 0;
            this.network.shareChunkFrames = (mask & 32) != 0;
            this.network.useLazyExecuteWhenNotFlushing = (mask & 64) != 0;
            for (final boolean freeze : new boolean[] {true, false}) {
                final List<Identifier> got = join(freeze);
                final String combination = "switches " + Integer.toBinaryString(mask) + " freeze " + freeze;
                assertTrue(!got.isEmpty(), combination + ": nothing received");
                assertEquals(loginId, got.getFirst(), combination + ": first PLAY packet");
                assertEquals(expected, got, combination);
            }
        }
    }

    @Test
    void withoutTheCheckTheListedTypesPassTheFrozenEncoder() throws Exception {
        final List<Identifier> expected = new ArrayList<>(expected(joinPackets()));
        expected.addAll(expected(afterJoinPackets()));
        final String[] names = {"fast-send-path", "batch-writes", "flush-once-per-batch", "packetevents-bypass",
            "share-broadcast-frames", "share-chunk-frames", "use-lazy-execute-when-not-flushing"};
        OutboundOrder.unguardedForTest = true;
        final List<String> culprits = new ArrayList<>();
        try {
            for (int bit = 0; bit < names.length; ++bit) {
                this.network.fastSendPath = bit == 0;
                this.network.batchWrites = bit == 1;
                this.network.flushOncePerBatch = bit == 2;
                this.network.packeteventsBypass = bit == 3;
                this.network.shareBroadcastFrames = bit == 4;
                this.network.shareChunkFrames = bit == 5;
                this.network.useLazyExecuteWhenNotFlushing = bit == 6;
                final List<Identifier> got = join(true);
                if (!expected.equals(got)) {
                    culprits.add(names[bit] + " -> " + got);
                }
            }
        } finally {
            OutboundOrder.unguardedForTest = false;
        }
        System.out.println("[order] reordering switches without the check: " + culprits);
        assertTrue(culprits.stream().anyMatch(c -> c.startsWith("packetevents-bypass")), "the old code reorders with packetevents-bypass: " + culprits);
    }

    @Test
    void outboundOrderSeesPacketEventsHold() {
        final io.netty.channel.embedded.EmbeddedChannel channel = new io.netty.channel.embedded.EmbeddedChannel();
        Connection.configureSerialization(channel.pipeline(), PacketFlow.SERVERBOUND, false, null);
        channel.pipeline().addAfter(HandlerNames.PREPENDER, HandlerNames.COMPRESS, new CompressionEncoder(256));
        assertTrue(!OutboundOrder.maySkipPluginHandlers(channel), "no packet encoder yet");
        channel.pipeline().replace(HandlerNames.OUTBOUND_CONFIG, HandlerNames.ENCODER, new net.minecraft.network.PacketEncoder<>(play()));
        assertTrue(OutboundOrder.maySkipPluginHandlers(channel), "nothing between encoder and compression");
        OutboundOrder.reset(channel);
        final PacketEventsEncoder packetEvents = new PacketEventsEncoder();
        channel.pipeline().addAfter(HandlerNames.COMPRESS, "pe-encoder", packetEvents);
        assertTrue(OutboundOrder.maySkipPluginHandlers(channel), "PacketEvents not holding");
        packetEvents.setHold(true);
        assertTrue(!OutboundOrder.maySkipPluginHandlers(channel), "PacketEvents holding");
        packetEvents.setHold(false);
        assertTrue(OutboundOrder.maySkipPluginHandlers(channel));
        OutboundOrder.reset(channel);
        channel.pipeline().addAfter(HandlerNames.COMPRESS, "other-plugin", new io.netty.channel.ChannelOutboundHandlerAdapter());
        assertTrue(!OutboundOrder.maySkipPluginHandlers(channel), "unknown handler in between");
        channel.finishAndReleaseAll();
    }
}
