package io.github.retrooper.packetevents.injector.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.util.ArrayDeque;

/**
 * Test stand-in for PacketEvents' encoder (same class name, so Battleship recognises it): sits between the packet encoder
 * and compression, passes non-ByteBuf messages on at once, and while {@code hold} is set queues every ByteBuf, like
 * PacketEvents does from PlayerConnectionValidateLoginEvent until PlayerJoinEvent.
 */
public class PacketEventsEncoder extends ChannelOutboundHandlerAdapter {

    private record Queued(Object msg, ChannelPromise promise) {
    }

    private boolean hold;
    private final ArrayDeque<Queued> queued = new ArrayDeque<>();
    private ChannelHandlerContext ctx;
    public int byteBufsSeen;

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    /** Like PacketEvents' setHold: run on the channel's event loop. */
    public void setHold(final boolean hold) {
        this.hold = hold;
        if (!hold) {
            Queued next;
            while ((next = this.queued.poll()) != null) {
                this.ctx.write(next.msg(), next.promise());
            }
            this.ctx.flush();
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
        if (this.hold && msg instanceof ByteBuf) {
            this.queued.add(new Queued(msg, promise));
            return;
        }
        if (msg instanceof ByteBuf) {
            ++this.byteBufsSeen;
        }
        ctx.write(msg, promise);
    }
}
