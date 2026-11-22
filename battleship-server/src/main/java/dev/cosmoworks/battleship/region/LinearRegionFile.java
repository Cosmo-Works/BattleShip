package dev.cosmoworks.battleship.region;

import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemChunkBuffer;
import ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemRegionFile;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinearRegionFile implements AbstractRegionFile {

    private static final long SUPERBLOCK = -4323716122432332390L;
    private static final byte VERSION = 2;
    private static final int HEADER_SIZE = 32;
    private static final int FOOTER_SIZE = 8;
    private static final Logger LOGGER = LoggerFactory.getLogger("Battleship-Linear");
    private static final List<Byte> SUPPORTED_VERSIONS = Arrays.asList((byte) 1, (byte) 2);
    private static final LinearRegionFileFlusher FLUSHER = new LinearRegionFileFlusher();

    private final byte[][] buffer = new byte[1024][];
    private final int[] bufferUncompressedSize = new int[1024];
    private final int[] chunkTimestamps = new int[1024];
    private final ChunkStatus[] statuses = new ChunkStatus[1024];

    private final LZ4Compressor compressor;
    private final LZ4FastDecompressor decompressor;
    private final ReentrantLock fileLock = new ReentrantLock(true);
    private final int compressionLevel;
    private final AtomicBoolean markedToSave = new AtomicBoolean(false);
    private final Path path;

    public boolean closed = false;

    public LinearRegionFile(final Path file, final int compression) throws IOException {
        this.path = file;
        this.compressionLevel = compression;
        this.compressor = LZ4Factory.fastestInstance().fastCompressor();
        this.decompressor = LZ4Factory.fastestInstance().fastDecompressor();

        final File regionFile = file.toFile();
        Arrays.fill(this.bufferUncompressedSize, 0);

        if (!regionFile.canRead()) {
            return;
        }

        try (
            final FileInputStream fileStream = new FileInputStream(regionFile);
            final DataInputStream rawDataStream = new DataInputStream(fileStream)
        ) {
            final long superBlock = rawDataStream.readLong();
            if (superBlock != SUPERBLOCK) {
                throw new RuntimeException("Invalid superblock: " + superBlock + " in " + file);
            }

            final byte version = rawDataStream.readByte();
            if (!SUPPORTED_VERSIONS.contains(version)) {
                throw new RuntimeException("Invalid version: " + version + " in " + file);
            }

            rawDataStream.skipBytes(11);

            final int dataCount = rawDataStream.readInt();
            final long fileLength = file.toFile().length();
            if (fileLength != HEADER_SIZE + dataCount + FOOTER_SIZE) {
                throw new IOException("Invalid file length: " + this.path + " " + fileLength + " "
                    + (HEADER_SIZE + dataCount + FOOTER_SIZE));
            }

            rawDataStream.skipBytes(8);

            final byte[] rawCompressed = new byte[dataCount];
            rawDataStream.readFully(rawCompressed, 0, dataCount);

            final long footer = rawDataStream.readLong();
            if (footer != SUPERBLOCK) {
                throw new IOException("Footer superblock invalid " + this.path);
            }

            try (
                final DataInputStream dataStream = new DataInputStream(
                    new ZstdInputStream(new ByteArrayInputStream(rawCompressed))
                )
            ) {
                final int[] starts = new int[1024];
                for (int i = 0; i < 1024; i++) {
                    starts[i] = dataStream.readInt();
                    dataStream.skipBytes(4);
                }

                for (int i = 0; i < 1024; i++) {
                    if (starts[i] <= 0) {
                        continue;
                    }

                    final int size = starts[i];
                    byte[] content = new byte[size];
                    dataStream.readFully(content, 0, size);

                    final int maxCompressedLength = this.compressor.maxCompressedLength(size);
                    final byte[] compressed = new byte[maxCompressedLength];
                    final int compressedLength = this.compressor.compress(content, 0, size, compressed, 0, maxCompressedLength);
                    content = new byte[compressedLength];
                    System.arraycopy(compressed, 0, content, 0, compressedLength);

                    this.buffer[i] = content;
                    this.bufferUncompressedSize[i] = size;
                }
            }
        }
    }

    @Override
    public Path getPath() {
        return this.path;
    }

    @Override
    public ReentrantLock getFileLock() {
        return this.fileLock;
    }

    @Override
    public void flush() throws IOException {
        if (this.isMarkedToSave()) {
            this.flushWrapper();
        }
    }

    private void markToSave() {
        FLUSHER.scheduleSave(this);
        this.markedToSave.set(true);
    }

    public boolean isMarkedToSave() {
        return this.markedToSave.getAndSet(false);
    }

    public void flushWrapper() {
        try {
            this.save();
        } catch (final IOException exception) {
            LOGGER.error("Failed to flush region file {}", this.path.toAbsolutePath(), exception);
        }
    }

    @Override
    public boolean doesChunkExist(final ChunkPos pos) {
        return this.hasChunk(pos);
    }

    private synchronized void save() throws IOException {
        final long timestamp = getTimestamp();
        short chunkCount = 0;
        final File tempFile = new File(this.path + ".tmp");

        try (
            final FileOutputStream fileStream = new FileOutputStream(tempFile);
            final ByteArrayOutputStream zstdByteArray = new ByteArrayOutputStream();
            final ZstdOutputStream zstdStream = new ZstdOutputStream(zstdByteArray, this.compressionLevel);
            final DataOutputStream zstdDataStream = new DataOutputStream(zstdStream);
            final DataOutputStream dataStream = new DataOutputStream(fileStream)
        ) {
            dataStream.writeLong(SUPERBLOCK);
            dataStream.writeByte(VERSION);
            dataStream.writeLong(timestamp);
            dataStream.writeByte(this.compressionLevel);

            final List<byte[]> byteBuffers = new ArrayList<>();
            for (int i = 0; i < 1024; i++) {
                if (this.bufferUncompressedSize[i] != 0) {
                    chunkCount++;
                    final byte[] content = new byte[this.bufferUncompressedSize[i]];
                    this.decompressor.decompress(this.buffer[i], 0, content, 0, this.bufferUncompressedSize[i]);
                    byteBuffers.add(content);
                } else {
                    byteBuffers.add(null);
                }
            }

            for (int i = 0; i < 1024; i++) {
                zstdDataStream.writeInt(this.bufferUncompressedSize[i]);
                zstdDataStream.writeInt(this.chunkTimestamps[i]);
            }

            for (int i = 0; i < 1024; i++) {
                final byte[] content = byteBuffers.get(i);
                if (content != null) {
                    zstdDataStream.write(content, 0, content.length);
                }
            }
            zstdDataStream.close();

            dataStream.writeShort(chunkCount);
            final byte[] compressed = zstdByteArray.toByteArray();
            dataStream.writeInt(compressed.length);
            dataStream.writeLong(0);
            dataStream.write(compressed, 0, compressed.length);
            dataStream.writeLong(SUPERBLOCK);
            dataStream.flush();
            fileStream.getFD().sync();
            fileStream.getChannel().force(true);
        }

        Files.move(tempFile.toPath(), this.path, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void setStatus(final int x, final int z, final ChunkStatus status) {
        this.statuses[getChunkIndex(x, z)] = status;
    }

    public synchronized void write(final ChunkPos pos, final ByteBuffer data) {
        try {
            byte[] content = toByteArray(new ByteArrayInputStream(data.array()));
            final int uncompressedSize = content.length;
            final int maxCompressedLength = this.compressor.maxCompressedLength(content.length);
            final byte[] compressed = new byte[maxCompressedLength];
            final int compressedLength = this.compressor.compress(content, 0, content.length, compressed, 0, maxCompressedLength);
            content = new byte[compressedLength];
            System.arraycopy(compressed, 0, content, 0, compressedLength);

            final int index = getChunkIndex(pos.x(), pos.z());
            this.buffer[index] = content;
            this.chunkTimestamps[index] = getTimestamp();
            this.bufferUncompressedSize[index] = uncompressedSize;
        } catch (final IOException exception) {
            LOGGER.error("Chunk write failed for {}", this.path, exception);
        }
        this.markToSave();
    }

    @Override
    public DataOutputStream getChunkDataOutputStream(final ChunkPos pos) {
        return new DataOutputStream(new BufferedOutputStream(new ChunkBuffer(pos)));
    }

    @Override
    public MoonriseRegionFileIO.RegionDataController.WriteData moonrise$startWrite(final CompoundTag data, final ChunkPos pos) {
        final ChunkBuffer buffer = new ChunkBuffer(pos);
        return new MoonriseRegionFileIO.RegionDataController.WriteData(
            data,
            MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.WRITE,
            new DataOutputStream(buffer),
            buffer::moonrise$write
        );
    }

    @Override
    public int getRecalculateCount() {
        return 0;
    }

    @Override
    public synchronized @Nullable DataInputStream getChunkDataInputStream(final ChunkPos pos) {
        final int index = getChunkIndex(pos.x(), pos.z());
        if (this.bufferUncompressedSize[index] == 0) {
            return null;
        }

        final byte[] content = new byte[this.bufferUncompressedSize[index]];
        this.decompressor.decompress(this.buffer[index], 0, content, 0, this.bufferUncompressedSize[index]);
        return new DataInputStream(new ByteArrayInputStream(content));
    }

    @Override
    public ChunkStatus getStatusIfCached(final int x, final int z) {
        return this.statuses[getChunkIndex(x, z)];
    }

    @Override
    public void clear(final ChunkPos pos) {
        final int index = getChunkIndex(pos.x(), pos.z());
        this.buffer[index] = null;
        this.bufferUncompressedSize[index] = 0;
        this.chunkTimestamps[index] = getTimestamp();
        this.markToSave();
    }

    @Override
    public boolean hasChunk(final ChunkPos pos) {
        return this.bufferUncompressedSize[getChunkIndex(pos.x(), pos.z())] > 0;
    }

    @Override
    public void close() throws IOException {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.flush();
    }

    @Override
    public boolean recalculateHeader() {
        return false;
    }

    @Override
    public void setOversized(final int x, final int z, final boolean oversized) {
    }

    @Override
    public CompoundTag getOversizedData(final int x, final int z) throws IOException {
        throw new IOException("Oversized chunk data is not supported in Linear format");
    }

    @Override
    public boolean isOversized(final int x, final int z) {
        return false;
    }

    private byte[] toByteArray(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] tempBuffer = new byte[4096];
        int length;
        while ((length = in.read(tempBuffer)) >= 0) {
            out.write(tempBuffer, 0, length);
        }
        return out.toByteArray();
    }

    private static int getChunkIndex(final int x, final int z) {
        return (x & 31) + ((z & 31) << 5);
    }

    private static int getTimestamp() {
        return (int) (System.currentTimeMillis() / 1000L);
    }

    private class ChunkBuffer extends ByteArrayOutputStream implements ChunkSystemChunkBuffer {
        private final ChunkPos pos;
        private boolean writeOnClose;

        private ChunkBuffer(final ChunkPos chunkPos) {
            this.pos = chunkPos;
        }

        @Override
        public boolean moonrise$getWriteOnClose() {
            return this.writeOnClose;
        }

        @Override
        public void moonrise$setWriteOnClose(final boolean value) {
            this.writeOnClose = value;
        }

        @Override
        public void moonrise$write(final ChunkSystemRegionFile regionFile) {
            if (regionFile instanceof final LinearRegionFile linear) {
                linear.write(this.pos, ByteBuffer.wrap(this.buf, 0, this.count));
            }
        }

        @Override
        public void close() throws IOException {
            if (this.writeOnClose) {
                LinearRegionFile.this.write(this.pos, ByteBuffer.wrap(this.buf, 0, this.count));
            }
        }
    }
}
