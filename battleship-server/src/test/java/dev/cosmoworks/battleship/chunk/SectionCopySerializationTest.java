package dev.cosmoworks.battleship.chunk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

@AllFeatures
class SectionCopySerializationTest {
    private static byte[] write(final LevelChunkSection section) {
        final ByteBuf buf = Unpooled.buffer(section.getSerializedSize());
        section.write(new FriendlyByteBuf(buf), null, 0);
        final byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    @Test
    void copyWrittenOnAnotherThreadMatchesTheOriginal() throws Exception {
        final List<BlockState> states = BuiltInRegistries.BLOCK.stream().map(Block::defaultBlockState).toList();
        final var biomes = RegistryHelper.registryAccess().lookupOrThrow(Registries.BIOME);
        final Holder<Biome> plains = biomes.listElements().findFirst().orElseThrow();
        final Random random = new Random(5);
        for (int round = 0; round < 20; ++round) {
            final PalettedContainer<BlockState> blocks = new PalettedContainer<>(states.getFirst(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
            final PalettedContainer<Holder<Biome>> biomeContainer = new PalettedContainer<>(plains, Strategy.createForBiomes(biomes.asHolderIdMap()));
            final int distinct = 1 + random.nextInt(round % 2 == 0 ? 8 : 400);
            for (int i = 0; i < 4096; ++i) {
                blocks.getAndSetUnchecked(i & 15, (i >> 8) & 15, (i >> 4) & 15, states.get(random.nextInt(distinct)));
            }
            final LevelChunkSection section = new LevelChunkSection(blocks, biomeContainer);
            final byte[] expected = write(section);
            final LevelChunkSection copy = section.copy();
            final byte[] fromOtherThread = CompletableFuture.supplyAsync(() -> write(copy)).get();
            assertArrayEquals(expected, fromOtherThread, "round " + round);

            section.setBlockState(1, 2, 3, states.get(states.size() - 1));
            assertArrayEquals(expected, write(copy));
        }
    }

    @Test
    void benchmark() {
        final List<BlockState> states = BuiltInRegistries.BLOCK.stream().map(Block::defaultBlockState).toList();
        final var biomes = RegistryHelper.registryAccess().lookupOrThrow(Registries.BIOME);
        final Holder<Biome> plains = biomes.listElements().findFirst().orElseThrow();
        final Random random = new Random(9);
        final PalettedContainer<BlockState> blocks = new PalettedContainer<>(states.getFirst(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
        for (int i = 0; i < 4096; ++i) {
            blocks.getAndSetUnchecked(i & 15, (i >> 8) & 15, (i >> 4) & 15, states.get(random.nextInt(24)));
        }
        final LevelChunkSection section = new LevelChunkSection(blocks, new PalettedContainer<>(plains, Strategy.createForBiomes(biomes.asHolderIdMap())));
        long sink = 0;
        for (int i = 0; i < 20000; ++i) {
            sink += write(section).length + section.copy().getSerializedSize();
        }
        final int rounds = 100000;
        long start = System.nanoTime();
        for (int i = 0; i < rounds; ++i) {
            sink += section.getSerializedSize();
            sink += write(section).length;
        }
        final double writeNs = (System.nanoTime() - start) / (double) rounds;
        start = System.nanoTime();
        for (int i = 0; i < rounds; ++i) {
            sink += section.copy().hashCode();
        }
        final double copyNs = (System.nanoTime() - start) / (double) rounds;
        System.out.printf("[bench] section size+write %.0f ns, copy %.0f ns (%d)%n", writeNs, copyNs, sink & 1);
    }
}
