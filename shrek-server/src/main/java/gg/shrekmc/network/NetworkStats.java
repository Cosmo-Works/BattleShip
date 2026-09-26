package gg.shrekmc.network;

import gg.shrekmc.ShrekServerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkStats {
    private static final Logger LOGGER = LoggerFactory.getLogger("ShrekNetwork");

    public static final LongAdder SENT_FLUSH = new LongAdder();
    public static final LongAdder SENT_NO_FLUSH = new LongAdder();

    public static final LongAdder WRITES = new LongAdder();
    public static final LongAdder FLUSH_CALLS = new LongAdder();
    public static final LongAdder SOCKET_FLUSHES = new LongAdder();

    public static final LongAdder SHARED_FRAMES = new LongAdder();
    public static final LongAdder CAPTURED_FRAMES = new LongAdder();

    public static final io.netty.util.concurrent.FastThreadLocal<Class<?>> CURRENT_PACKET = new io.netty.util.concurrent.FastThreadLocal<>();

    private static final class Bytes {
        final LongAdder packets = new LongAdder();
        final LongAdder bytes = new LongAdder();
    }

    private static final Map<Class<?>, Bytes> COMPRESSED = new ConcurrentHashMap<>();
    private static final Map<Class<?>, LongAdder> SENT = new ConcurrentHashMap<>();

    public static void sent(final Class<?> packetClass) {
        LongAdder adder = SENT.get(packetClass);
        if (adder == null) {
            adder = SENT.computeIfAbsent(packetClass, k -> new LongAdder());
        }
        adder.increment();
    }
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private NetworkStats() {
    }

    public static boolean enabled() {
        return ShrekServerConfig.network().statsLogInterval > 0;
    }

    public static void compressed(final Class<?> packetClass, final int uncompressedBytes) {
        final Bytes bytes = COMPRESSED.computeIfAbsent(packetClass == null ? Void.class : packetClass, k -> new Bytes());
        bytes.packets.increment();
        bytes.bytes.add(uncompressedBytes);
    }

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        final Thread thread = new Thread(NetworkStats::loop, "Shrek Network Stats");
        thread.setDaemon(true);
        thread.start();
    }

    private static void loop() {
        long last = System.nanoTime();
        while (true) {
            final int interval = ShrekServerConfig.network().statsLogInterval;
            try {
                Thread.sleep(Math.max(1, interval) * 1000L);
            } catch (final InterruptedException e) {
                return;
            }
            final long now = System.nanoTime();
            final double seconds = (now - last) / 1.0e9;
            last = now;
            if (interval <= 0) {
                reset();
                continue;
            }
            try {
                log(seconds);
            } catch (final Throwable thrown) {
                LOGGER.warn("Couldn't log network stats", thrown);
            }
        }
    }

    private static void reset() {
        for (final LongAdder adder : List.of(SENT_FLUSH, SENT_NO_FLUSH, WRITES, FLUSH_CALLS, SOCKET_FLUSHES, SHARED_FRAMES, CAPTURED_FRAMES)) {
            adder.reset();
        }
        COMPRESSED.clear();
        SENT.clear();
    }

    private static void log(final double seconds) {
        final long flush = SENT_FLUSH.sumThenReset();
        final long noFlush = SENT_NO_FLUSH.sumThenReset();
        final long writes = WRITES.sumThenReset();
        final long flushCalls = FLUSH_CALLS.sumThenReset();
        final long socketFlushes = SOCKET_FLUSHES.sumThenReset();
        final long shared = SHARED_FRAMES.sumThenReset();
        final long captured = CAPTURED_FRAMES.sumThenReset();
        LOGGER.info(
            "Send path per second: packets {} (flush {}, no flush {}), socket writes {}, flush calls {}, socket flushes {} ({} writes per socket flush), consolidate-flushes {}; shared frames {} (captured {})",
            rate(flush + noFlush, seconds), rate(flush, seconds), rate(noFlush, seconds), rate(writes, seconds),
            rate(flushCalls, seconds), rate(socketFlushes, seconds),
            socketFlushes == 0 ? "-" : String.format("%.1f", writes / (double) socketFlushes),
            ShrekServerConfig.network().consolidateFlushes ? "on" : "off",
            rate(shared, seconds), rate(captured, seconds)
        );
        final List<Map.Entry<Class<?>, long[]>> rows = new ArrayList<>();
        for (final Map.Entry<Class<?>, Bytes> entry : COMPRESSED.entrySet()) {
            rows.add(Map.entry(entry.getKey(), new long[] {entry.getValue().packets.sumThenReset(), entry.getValue().bytes.sumThenReset()}));
        }
        rows.removeIf(row -> row.getValue()[0] == 0);
        rows.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
        long total = 0;
        for (final Map.Entry<Class<?>, long[]> row : rows) {
            total += row.getValue()[1];
        }
        final StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(10, rows.size()); ++i) {
            final Map.Entry<Class<?>, long[]> row = rows.get(i);
            final String name = row.getKey() == Void.class ? "plugin-written" : row.getKey().getSimpleName();
            if (i > 0) {
                top.append(", ");
            }
            top.append(name).append(' ').append(total == 0 ? 0 : row.getValue()[1] * 100 / total).append("% (")
                .append(rate(row.getValue()[0], seconds)).append("/s)");
        }
        LOGGER.info("Compressed per second: {} KiB; by packet (share of bytes, packets/s): {}", rate(total / 1024, seconds), top);
        final List<Map.Entry<Class<?>, Long>> sent = new ArrayList<>();
        long sentTotal = 0;
        for (final Map.Entry<Class<?>, LongAdder> entry : SENT.entrySet()) {
            final long count = entry.getValue().sumThenReset();
            if (count > 0) {
                sent.add(Map.entry(entry.getKey(), count));
                sentTotal += count;
            }
        }
        sent.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        final StringBuilder sentTop = new StringBuilder();
        for (int i = 0; i < Math.min(10, sent.size()); ++i) {
            if (i > 0) {
                sentTop.append(", ");
            }
            final Map.Entry<Class<?>, Long> row = sent.get(i);
            sentTop.append(simpleName(row.getKey())).append(' ').append(rate(row.getValue(), seconds)).append("/s (")
                .append(sentTotal == 0 ? 0 : row.getValue() * 100 / sentTotal).append("%)");
        }
        LOGGER.info("Written per second: {} packets; top types: {}", rate(sentTotal, seconds), sentTop);
    }

    private static String simpleName(final Class<?> clazz) {
        final String name = clazz.getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static long rate(final long count, final double seconds) {
        return seconds <= 0 ? count : Math.round(count / seconds);
    }
}
