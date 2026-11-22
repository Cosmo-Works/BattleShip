package dev.cosmoworks.battleship.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("Battleship")
class FlushConsolidationTest {
    private static final class Socket extends ChannelOutboundHandlerAdapter {
        final List<Integer> written = new ArrayList<>();
        final List<Integer> flushedAt = new ArrayList<>();

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
            final ByteBuf buf = (ByteBuf) msg;
            this.written.add((int) buf.readByte());
            buf.release();
            promise.setSuccess();
        }

        @Override
        public void flush(final ChannelHandlerContext ctx) {
            this.flushedAt.add(this.written.size());
        }
    }

    private static EmbeddedChannel channel(final Socket socket, final boolean consolidate) {
        final EmbeddedChannel channel = new EmbeddedChannel();

        channel.pipeline().addFirst(socket);
        channel.pipeline().addLast(FlushConsolidation.newHandler(consolidate));
        return channel;
    }

    private static void burst(final EmbeddedChannel channel, final int count) {
        channel.eventLoop().execute(() -> {
            for (int i = 0; i < count; ++i) {
                channel.pipeline().writeAndFlush(Unpooled.wrappedBuffer(new byte[] {(byte) i}));
            }
        });
        channel.runPendingTasks();
    }

    @Test
    void paperDefaultFlushesEveryWrite() {
        final Socket socket = new Socket();
        final EmbeddedChannel channel = channel(socket, false);
        burst(channel, 10);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), socket.flushedAt);
        channel.finishAndReleaseAll();
    }

    @Test
    void consolidatedBurstIsOneFlushAndKeepsOrder() {
        final Socket socket = new Socket();
        final EmbeddedChannel channel = channel(socket, true);
        burst(channel, 10);
        assertEquals(List.of(10), socket.flushedAt, "one flush after all ten writes");
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), socket.written);
        channel.finishAndReleaseAll();
    }

    @Test
    void separateBurstsFlushSeparately() {
        final Socket socket = new Socket();
        final EmbeddedChannel channel = channel(socket, true);
        burst(channel, 3);
        burst(channel, 2);
        assertEquals(List.of(3, 5), socket.flushedAt);
        channel.finishAndReleaseAll();
    }

    @Test
    void closeFlushesPendingWrites() {
        final Socket socket = new Socket();
        final EmbeddedChannel channel = channel(socket, true);
        channel.pipeline().writeAndFlush(Unpooled.wrappedBuffer(new byte[] {1}));
        channel.pipeline().close();
        assertEquals(1, socket.flushedAt.size());
        channel.finishAndReleaseAll();
    }
}
