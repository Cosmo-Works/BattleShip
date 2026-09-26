package gg.shrekmc.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

@AllFeatures
class RandomTickSectionsTest {
    private static final class Chunk implements RandomTickSections.Holder {
        long mask;
        int age;

        @Override
        public long shrek$tickingSectionMask() {
            return this.mask;
        }

        @Override
        public void shrek$tickingSectionMask(final long mask) {
            this.mask = mask;
        }

        @Override
        public int shrek$tickingMaskAge() {
            return this.age;
        }

        @Override
        public void shrek$tickingMaskAge(final int age) {
            this.age = age;
        }
    }

    private static LevelChunkSection[] sections(final int count) {
        final var biomes = RegistryHelper.registryAccess().lookupOrThrow(Registries.BIOME);
        final Holder<Biome> plains = biomes.listElements().findFirst().orElseThrow();
        final LevelChunkSection[] sections = new LevelChunkSection[count];
        for (int i = 0; i < count; ++i) {
            sections[i] = new LevelChunkSection(
                new PalettedContainer<>(Blocks.STONE.defaultBlockState(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),
                new PalettedContainer<>(plains, Strategy.createForBiomes(biomes.asHolderIdMap()))
            );
        }
        return sections;
    }

    private static List<Integer> visited(final LevelChunkSection[] sections, final long mask) {
        final List<Integer> ret = new ArrayList<>();
        for (int i = 0; i < sections.length; ++i) {
            if (!RandomTickSections.contains(mask, i)) {
                continue;
            }
            if (sections[i].isRandomlyTickingBlocks()) {
                ret.add(i);
            }
        }
        return ret;
    }

    @Test
    void sameSectionsAsThePlainLoopForChangesThroughTheChunk() {
        final LevelChunkSection[] sections = sections(24);
        final Chunk chunk = new Chunk();
        final Random random = new Random(3);
        final BlockState[] states = {
            Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(),
            Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.WHEAT.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState()
        };
        assertEquals(true, Blocks.GRASS_BLOCK.defaultBlockState().isRandomlyTicking());
        for (int tick = 0; tick < 2000; ++tick) {
            for (int c = 0; c < 3; ++c) {
                final int s = random.nextInt(sections.length);
                final BlockState state = states[random.nextInt(states.length)];
                sections[s].setBlockState(random.nextInt(16), random.nextInt(16), random.nextInt(16), state);
                if (state.isRandomlyTicking()) {
                    RandomTickSections.markTicking(chunk, s);
                }
            }
            final long mask = RandomTickSections.mask(chunk, sections, 20);
            assertEquals(visited(sections, RandomTickSections.ALL), visited(sections, mask), "tick " + tick);
        }
    }

    @Test
    void directSectionWritesArePickedUpByTheRebuild() {
        final LevelChunkSection[] sections = sections(24);
        final Chunk chunk = new Chunk();
        RandomTickSections.mask(chunk, sections, 20);
        sections[7].setBlockState(1, 2, 3, Blocks.GRASS_BLOCK.defaultBlockState());
        int ticksUntilSeen = 0;
        while (!visited(sections, RandomTickSections.mask(chunk, sections, 20)).contains(7)) {
            ++ticksUntilSeen;
        }
        assertEquals(true, ticksUntilSeen < 20, "seen within the interval");
    }

    @Test
    void moreThan64SectionsAlwaysUseEverySection() {
        final LevelChunkSection[] sections = sections(70);
        assertEquals(RandomTickSections.ALL, RandomTickSections.mask(new Chunk(), sections, 20));
    }
}
