package dev.cosmoworks.battleship.chunk;

import net.minecraft.world.level.chunk.LevelChunkSection;

public final class RandomTickSections {
    public static final long ALL = -1L;

    public interface Holder {
        long battleship$tickingSectionMask();

        void battleship$tickingSectionMask(long mask);

        int battleship$tickingMaskAge();

        void battleship$tickingMaskAge(int age);
    }

    private RandomTickSections() {
    }

    public static long mask(final Holder chunk, final LevelChunkSection[] sections, final int interval) {
        if (sections.length > 64) {
            return ALL;
        }
        final int age = chunk.battleship$tickingMaskAge() - 1;
        if (age <= 0) {
            final long mask = compute(sections);
            chunk.battleship$tickingSectionMask(mask);
            chunk.battleship$tickingMaskAge(Math.max(1, interval));
            return mask;
        }
        chunk.battleship$tickingMaskAge(age);
        return chunk.battleship$tickingSectionMask();
    }

    public static long compute(final LevelChunkSection[] sections) {
        long mask = 0L;
        for (int i = 0, len = Math.min(64, sections.length); i < len; ++i) {
            final LevelChunkSection section = sections[i];
            if (section != null && section.isRandomlyTickingBlocks()) {
                mask |= 1L << i;
            }
        }
        return mask;
    }

    public static void markTicking(final Holder chunk, final int sectionIndex) {
        if (sectionIndex >= 0 && sectionIndex < 64) {
            chunk.battleship$tickingSectionMask(chunk.battleship$tickingSectionMask() | (1L << sectionIndex));
        }
    }

    public static boolean contains(final long mask, final int sectionIndex) {
        return mask == ALL || (sectionIndex < 64 && (mask & (1L << sectionIndex)) != 0L);
    }
}
