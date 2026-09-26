package gg.shrekmc.io;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public final class AsyncFileWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger("ShrekAsyncFileWriter");

    public static final AsyncFileWriter INSTANCE = new AsyncFileWriter();

    @FunctionalInterface
    public interface Content {
        void writeTo(Path temporaryFile) throws IOException;
    }

    public record Write(Path file, @Nullable Path backup, Object snapshot, Content content, long sequence) {
    }

    private final Map<Path, Write> queued = new ConcurrentHashMap<>();
    private final Map<Path, Write> writing = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<Path> order = new LinkedBlockingQueue<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Object idle = new Object();
    private volatile @Nullable Thread thread;

    volatile @Nullable Runnable beforeWrite;

    private AsyncFileWriter() {
    }

    AsyncFileWriter(final boolean forTests) {
    }

    private void start() {
        if (this.thread != null) {
            return;
        }
        synchronized (this) {
            if (this.thread != null) {
                return;
            }
            final Thread created = new Thread(this::run, "Shrek Player IO");
            created.setDaemon(true);
            created.setPriority(Thread.NORM_PRIORITY - 1);
            this.thread = created;
            created.start();
        }
    }

    public void submit(final Path file, final @Nullable Path backup, final Object snapshot, final Content content) {
        final Write write = new Write(file, backup, snapshot, content, this.sequence.incrementAndGet());
        this.start();
        if (this.queued.put(file, write) == null) {
            this.order.add(file);
        }
    }

    public @Nullable Object pending(final Path file) {
        final Write queuedWrite = this.queued.get(file);
        if (queuedWrite != null) {
            return queuedWrite.snapshot();
        }
        final Write inFlight = this.writing.get(file);
        return inFlight == null ? null : inFlight.snapshot();
    }

    public boolean isIdle() {
        return this.queued.isEmpty() && this.writing.isEmpty();
    }

    public boolean flush(final long timeoutMillis) {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        synchronized (this.idle) {
            while (!this.isIdle()) {
                final long left = deadline - System.nanoTime();
                if (left <= 0) {
                    LOGGER.warn("Player files still being written after {} ms: {}", timeoutMillis, this.queued.size() + this.writing.size());
                    return false;
                }
                try {
                    this.idle.wait(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(left)));
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    private void run() {
        for (;;) {
            final Path file;
            try {
                file = this.order.take();
            } catch (final InterruptedException interrupted) {
                continue;
            }
            this.writeNow(file);
        }
    }

    void writeNow(final Path file) {
        final Write write = this.queued.remove(file);
        if (write == null) {
            this.notifyIfIdle();
            return;
        }
        this.writing.put(file, write);
        try {
            final Runnable hook = this.beforeWrite;
            if (hook != null) {
                hook.run();
            }
            write(write);
        } catch (final Throwable thrown) {
            LOGGER.error("Couldn't save {}", file, thrown);
        } finally {
            this.writing.remove(file, write);
            this.notifyIfIdle();
        }
    }

    private void notifyIfIdle() {
        if (this.isIdle()) {
            synchronized (this.idle) {
                this.idle.notifyAll();
            }
        }
    }

    static void write(final Write write) throws IOException {
        final Path file = write.file();
        final Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        final Path temporary = Files.createTempFile(directory, file.getFileName().toString() + "-", ".tmp");
        boolean moved = false;
        try {
            write.content().writeTo(temporary);
            if (write.backup() != null) {
                if (!net.minecraft.util.Util.safeReplaceOrMoveFile(file, temporary, write.backup(), false)) {
                    throw new IOException("Couldn't replace " + file);
                }
            } else {
                try {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (final AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
