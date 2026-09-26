package gg.shrekmc.network;

import gg.shrekmc.ShrekServerConfig;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

public final class FlushConsolidation extends ChannelDuplexHandler {
    public static final int EXPLICIT_FLUSH_AFTER_FLUSHES = 256;

    private final BooleanSupplier consolidate;
    private int flushPendingCount;
    private boolean readInProgress;
    private ChannelHandlerContext ctx;
    private Future<?> nextScheduledFlush;
    private final Runnable flushTask = () -> {
        if (this.flushPendingCount > 0 && !this.readInProgress) {
            this.flushPendingCount = 0;
            this.nextScheduledFlush = null;
            this.socketFlush(this.ctx);
        }
    };

    private FlushConsolidation(final BooleanSupplier consolidate) {
        this.consolidate = consolidate;
    }

    public static ChannelHandler newHandler() {
        NetworkStats.start();
        return new FlushConsolidation(() -> ShrekServerConfig.network().consolidateFlushes);
    }

    public static ChannelHandler newHandler(final boolean consolidateOutsideReads) {
        return new FlushConsolidation(() -> consolidateOutsideReads);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
        NetworkStats.WRITES.increment();
        ctx.write(msg, promise);
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) {
        NetworkStats.FLUSH_CALLS.increment();
        if (this.readInProgress) {
            if (++this.flushPendingCount == EXPLICIT_FLUSH_AFTER_FLUSHES) {
                this.flushNow(ctx);
            }
        } else if (this.consolidate.getAsBoolean()) {
            if (++this.flushPendingCount == EXPLICIT_FLUSH_AFTER_FLUSHES) {
                this.flushNow(ctx);
            } else if (this.nextScheduledFlush == null) {
                this.nextScheduledFlush = ctx.channel().eventLoop().submit(this.flushTask);
            }
        } else {
            this.flushNow(ctx);
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        this.readInProgress = true;
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        this.resetReadAndFlushIfNeeded(ctx);
        ctx.fireChannelReadComplete();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        this.resetReadAndFlushIfNeeded(ctx);
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void disconnect(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        this.resetReadAndFlushIfNeeded(ctx);
        ctx.disconnect(promise);
    }

    @Override
    public void close(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        this.resetReadAndFlushIfNeeded(ctx);
        ctx.close(promise);
    }

    @Override
    public void channelWritabilityChanged(final ChannelHandlerContext ctx) {
        if (!ctx.channel().isWritable()) {
            this.flushIfNeeded(ctx);
        }
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        this.flushIfNeeded(ctx);
    }

    private void resetReadAndFlushIfNeeded(final ChannelHandlerContext ctx) {
        this.readInProgress = false;
        this.flushIfNeeded(ctx);
    }

    private void flushIfNeeded(final ChannelHandlerContext ctx) {
        if (this.flushPendingCount > 0) {
            this.flushNow(ctx);
        }
    }

    private void flushNow(final ChannelHandlerContext ctx) {
        if (this.nextScheduledFlush != null) {
            this.nextScheduledFlush.cancel(false);
            this.nextScheduledFlush = null;
        }
        this.flushPendingCount = 0;
        this.socketFlush(ctx);
    }

    private void socketFlush(final ChannelHandlerContext ctx) {
        NetworkStats.SOCKET_FLUSHES.increment();
        ctx.flush();
    }
}
