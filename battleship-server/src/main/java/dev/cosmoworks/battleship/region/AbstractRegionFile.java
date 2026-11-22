package dev.cosmoworks.battleship.region;

import ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemRegionFile;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

public interface AbstractRegionFile extends ChunkSystemRegionFile, AutoCloseable {
    void flush() throws IOException;

    void clear(ChunkPos pos) throws IOException;

    void close() throws IOException;

    default void setStatus(final int x, final int z, final ChunkStatus status) {
    }

    void setOversized(int x, int z, boolean oversized) throws IOException;

    boolean hasChunk(ChunkPos pos);

    default boolean doesChunkExist(final ChunkPos pos) throws Exception {
        return this.hasChunk(pos);
    }

    boolean isOversized(int x, int z);

    boolean recalculateHeader() throws IOException;

    DataOutputStream getChunkDataOutputStream(ChunkPos pos) throws IOException;

    @Nullable DataInputStream getChunkDataInputStream(ChunkPos pos) throws IOException;

    CompoundTag getOversizedData(int x, int z) throws IOException;

    default @Nullable ChunkStatus getStatusIfCached(final int x, final int z) {
        return null;
    }

    int getRecalculateCount();

    default ReentrantLock getFileLock() {
        return null;
    }

    Path getPath();
}
