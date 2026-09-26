package gg.shrekmc.network;

public final class SharedFrames {
    private SharedFrames() {
    }

    public record Frame(Object packet, int threshold, boolean nativeCompressor, byte[] body) {
        public boolean matches(final Object packet, final int threshold, final boolean nativeCompressor) {
            return this.packet == packet && this.threshold == threshold && this.nativeCompressor == nativeCompressor;
        }
    }

    public record Message(byte[] body) {
    }

    public static boolean enabled() {
        return gg.shrekmc.ShrekServerConfig.network().shareChunkFrames;
    }

    public static final class Broadcast {
        private final net.minecraft.network.protocol.Packet<?> packet;
        private volatile Object[] frames = new Object[0];

        private record BroadcastFrame(int threshold, boolean nativeCompressor, java.util.@org.jspecify.annotations.Nullable Locale locale, byte[] body) {
        }

        Broadcast(final net.minecraft.network.protocol.Packet<?> packet) {
            this.packet = packet;
        }

        public net.minecraft.network.protocol.Packet<?> packet() {
            return this.packet;
        }

        public byte @org.jspecify.annotations.Nullable [] find(final int threshold, final boolean nativeCompressor, final java.util.@org.jspecify.annotations.Nullable Locale locale) {
            for (final Object object : this.frames) {
                final BroadcastFrame frame = (BroadcastFrame) object;
                if (frame.threshold() == threshold && frame.nativeCompressor() == nativeCompressor && java.util.Objects.equals(frame.locale(), locale)) {
                    return frame.body();
                }
            }
            return null;
        }

        public synchronized void store(final int threshold, final boolean nativeCompressor, final java.util.@org.jspecify.annotations.Nullable Locale locale, final byte[] body) {
            if (this.find(threshold, nativeCompressor, locale) != null || this.frames.length >= 8) {
                return;
            }
            final Object[] frames = java.util.Arrays.copyOf(this.frames, this.frames.length + 1);
            frames[frames.length - 1] = new BroadcastFrame(threshold, nativeCompressor, locale, body);
            this.frames = frames;
        }
    }

    private static final ThreadLocal<Broadcast> CURRENT = new ThreadLocal<>();

    public static @org.jspecify.annotations.Nullable Broadcast begin(final net.minecraft.network.protocol.Packet<?> packet, final int viewers) {
        final gg.shrekmc.ShrekServerConfig.Network config = gg.shrekmc.ShrekServerConfig.network();
        if (!config.shareBroadcastFrames || viewers < 2
            || packet.type().flow() != net.minecraft.network.protocol.PacketFlow.CLIENTBOUND
            || !PacketEventsBypass.types(config.packeteventsBypassTypes).contains(packet.type().id())) {
            return null;
        }
        final Broadcast broadcast = new Broadcast(packet);
        CURRENT.set(broadcast);
        return broadcast;
    }

    public static void end(final @org.jspecify.annotations.Nullable Broadcast broadcast) {
        if (broadcast != null) {
            CURRENT.remove();
        }
    }

    public static @org.jspecify.annotations.Nullable Broadcast current(final net.minecraft.network.protocol.Packet<?> packet) {
        final Broadcast broadcast = CURRENT.get();
        return broadcast != null && broadcast.packet == packet ? broadcast : null;
    }
}
