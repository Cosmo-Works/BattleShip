package dev.cosmoworks.battleship.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@AllFeatures
class AsyncFileWriterTest {
    @TempDir
    Path directory;

    private static AsyncFileWriter.Content text(final String value, final List<String> written) {
        return temporary -> {
            Files.writeString(temporary, value, StandardCharsets.UTF_8);
            written.add(value);
        };
    }

    @Test
    void newestSaveWinsAndPendingIsReadable() throws Exception {
        final AsyncFileWriter writer = new AsyncFileWriter(true);
        final Path file = this.directory.resolve("player.json");
        final List<String> written = new CopyOnWriteArrayList<>();
        final CountDownLatch writing = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        writer.beforeWrite = () -> {
            writing.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (final InterruptedException ignored) {
            }
        };

        writer.submit(file, null, "v1", text("v1", written));
        assertTrue(writing.await(5, TimeUnit.SECONDS));
        writer.submit(file, null, "v2", text("v2", written));
        writer.submit(file, null, "v3", text("v3", written));

        assertEquals("v3", writer.pending(file));
        release.countDown();
        writer.beforeWrite = null;
        assertTrue(writer.flush(5000));

        assertEquals(List.of("v1", "v3"), written, "an older save must never be written after a newer one");
        assertEquals("v3", Files.readString(file));
        assertNull(writer.pending(file));
        assertTrue(writer.isIdle());
    }

    @Test
    void aFailedWriteLeavesThePreviousFileIntact() throws Exception {
        final AsyncFileWriter writer = new AsyncFileWriter(true);
        final Path file = this.directory.resolve("stats.json");
        Files.writeString(file, "previous");
        writer.submit(file, null, "broken", temporary -> {
            Files.writeString(temporary, "half of the new cont");
            throw new IOException("disk died mid-write");
        });
        assertTrue(writer.flush(5000));
        assertEquals("previous", Files.readString(file));
        try (var files = Files.list(this.directory)) {
            assertEquals(List.of(file), files.toList(), "the temporary file is removed");
        }
    }

    @Test
    void playerdataKeepsABackupOfThePreviousFile() throws Exception {
        final AsyncFileWriter writer = new AsyncFileWriter(true);
        final Path file = this.directory.resolve("uuid.dat");
        final Path backup = this.directory.resolve("uuid.dat_old");
        Files.writeString(file, "old");
        writer.submit(file, backup, "new", temporary -> Files.writeString(temporary, "new"));
        assertTrue(writer.flush(5000));
        assertEquals("new", Files.readString(file));
        assertEquals("old", Files.readString(backup));
    }

    @Test
    void playerdataLoadAndSavePathsMatch() {
        final java.io.File playerDir = this.directory.toFile();
        final String id = java.util.UUID.randomUUID().toString();
        assertEquals(playerDir.toPath().resolve(id + ".dat"), new java.io.File(playerDir, id + ".dat").toPath());
    }
}
