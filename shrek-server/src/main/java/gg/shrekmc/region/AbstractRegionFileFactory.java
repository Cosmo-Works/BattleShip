package gg.shrekmc.region;

import gg.shrekmc.ShrekServerConfig;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

public final class AbstractRegionFileFactory {
    private AbstractRegionFileFactory() {
    }

    public static AbstractRegionFile open(
        final RegionStorageInfo info,
        final Path file,
        final Path directory,
        final boolean sync
    ) throws IOException {
        if (file.toString().endsWith(RegionFileFormat.LINEAR_EXTENSION)) {
            return new LinearRegionFile(file, ShrekServerConfig.compressionLevel());
        }
        return new RegionFile(info, file, directory, sync);
    }
}
