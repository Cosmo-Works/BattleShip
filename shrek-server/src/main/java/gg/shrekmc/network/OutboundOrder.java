package gg.shrekmc.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.util.AttributeKey;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.network.HandlerNames;
import org.jspecify.annotations.Nullable;

public final class OutboundOrder {
    private static final String PACKETEVENTS_ENCODER = "io.github.retrooper.packetevents.injector.handlers.PacketEventsEncoder";
    private static final AttributeKey<State> STATE = AttributeKey.valueOf("shrek:outbound_order");

    private record State(boolean unknownHandler, @Nullable ChannelHandler packetEvents, @Nullable Field hold) {
    }

    private static final State NOTHING_IN_BETWEEN = new State(false, null, null);
    private static final State UNKNOWN = new State(true, null, null);

    private OutboundOrder() {
    }

    static volatile boolean unguardedForTest;

    public static boolean maySkipPluginHandlers(final Channel channel) {
        if (unguardedForTest) {
            return true;
        }
        State state = channel.attr(STATE).get();
        if (state == null) {
            state = scan(channel.pipeline());
            if (state == null) {
                return false;
            }
            channel.attr(STATE).set(state);
        }
        if (state.unknownHandler()) {
            return false;
        }
        if (state.packetEvents() == null) {
            return true;
        }
        try {
            return !state.hold().getBoolean(state.packetEvents());
        } catch (final IllegalAccessException | RuntimeException e) {
            return false;
        }
    }

    public static void reset(final Channel channel) {
        channel.attr(STATE).set(null);
    }

    static @Nullable State scan(final ChannelPipeline pipeline) {
        final List<String> names = pipeline.names();
        final int compress = names.indexOf(HandlerNames.COMPRESS);
        final int encoder = names.indexOf(HandlerNames.ENCODER);
        if (compress < 0 || encoder < 0) {
            return null;
        }
        final int from = Math.min(compress, encoder) + 1;
        final int to = Math.max(compress, encoder);
        ChannelHandler packetEvents = null;
        for (int i = from; i < to; ++i) {
            final ChannelHandler handler = pipeline.get(names.get(i));
            if (handler == null) {
                continue;
            }
            if (handler.getClass().getName().equals(PACKETEVENTS_ENCODER) && packetEvents == null) {
                packetEvents = handler;
            } else {
                return UNKNOWN;
            }
        }
        if (packetEvents == null) {
            return NOTHING_IN_BETWEEN;
        }
        final Field hold = holdField(packetEvents.getClass());
        return hold == null ? UNKNOWN : new State(false, packetEvents, hold);
    }

    static @Nullable Field holdField(final Class<?> clazz) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                final Field field = c.getDeclaredField("hold");
                if (field.getType() != boolean.class) {
                    return null;
                }
                field.setAccessible(true);
                return field;
            } catch (final NoSuchFieldException ignored) {
            } catch (final RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
