package dev.cosmoworks.battleship.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("Battleship")
class CrowdSelectionTest {
    private static final double SWAP = 4.0;

    private static void offer(final CrowdSelection selection, final int id, final double distance) {
        selection.offer(id, distance * distance);
    }

    @Test
    void selectsEveryoneUnderTheCap() {
        final CrowdSelection selection = new CrowdSelection();
        for (int id = 0; id < 5; ++id) {
            offer(selection, id, 100 - id);
        }
        selection.rebuild(10, SWAP);
        assertEquals(5, selection.selectedCount());
        for (int id = 0; id < 5; ++id) {
            assertTrue(selection.isSelected(id));
        }
    }

    @Test
    void selectsTheNearestOverTheCap() {
        final CrowdSelection selection = new CrowdSelection();
        for (int id = 0; id < 500; ++id) {
            offer(selection, id, 500 - id);
        }
        selection.rebuild(150, SWAP);
        assertEquals(150, selection.selectedCount());
        for (int id = 0; id < 500; ++id) {
            assertEquals(id >= 350, selection.isSelected(id), "id " + id);
        }
    }

    @Test
    void keepsShownPlayersUnlessClearlyCloser() {
        final CrowdSelection selection = new CrowdSelection();
        offer(selection, 1, 10);
        offer(selection, 2, 20);
        offer(selection, 3, 30);
        selection.rebuild(2, SWAP);
        assertTrue(selection.isSelected(1));
        assertTrue(selection.isSelected(2));
        assertFalse(selection.isSelected(3));

        offer(selection, 1, 10);
        offer(selection, 2, 20);
        offer(selection, 3, 17);
        selection.rebuild(2, SWAP);
        assertTrue(selection.isSelected(2));
        assertFalse(selection.isSelected(3));

        offer(selection, 1, 10);
        offer(selection, 2, 20);
        offer(selection, 3, 15.5);
        selection.rebuild(2, SWAP);
        assertTrue(selection.isSelected(1));
        assertTrue(selection.isSelected(3));
        assertFalse(selection.isSelected(2));
    }

    @Test
    void swapsSeveralFurthestForNearestNewcomers() {
        final CrowdSelection selection = new CrowdSelection();
        for (int id = 0; id < 4; ++id) {
            offer(selection, id, 50 + id);
        }
        selection.rebuild(4, SWAP);

        for (int id = 0; id < 4; ++id) {
            offer(selection, id, 50 + id);
        }
        offer(selection, 10, 5);
        offer(selection, 11, 6);
        offer(selection, 12, 51);
        selection.rebuild(4, SWAP);
        assertEquals(4, selection.selectedCount());
        assertTrue(selection.isSelected(10));
        assertTrue(selection.isSelected(11));
        assertTrue(selection.isSelected(0));
        assertTrue(selection.isSelected(1));
        assertFalse(selection.isSelected(2));
        assertFalse(selection.isSelected(3));
        assertFalse(selection.isSelected(12));
    }

    @Test
    void dropsPlayersThatWereNotOffered() {
        final CrowdSelection selection = new CrowdSelection();
        offer(selection, 1, 10);
        offer(selection, 2, 20);
        offer(selection, 3, 30);
        selection.rebuild(2, SWAP);

        offer(selection, 2, 20);
        offer(selection, 3, 30);
        offer(selection, 4, 40);
        selection.rebuild(2, SWAP);
        assertFalse(selection.isSelected(1));
        assertTrue(selection.isSelected(2));
        assertTrue(selection.isSelected(3));
        assertFalse(selection.isSelected(4));
    }

    @Test
    void shrinkingTheCapKeepsTheNearestShown() {
        final CrowdSelection selection = new CrowdSelection();
        for (int id = 0; id < 10; ++id) {
            offer(selection, id, 10 + id);
        }
        selection.rebuild(10, SWAP);
        for (int id = 0; id < 10; ++id) {
            offer(selection, id, 10 + id);
        }
        offer(selection, 99, 1);
        selection.rebuild(5, SWAP);
        assertEquals(5, selection.selectedCount());
        assertTrue(selection.isSelected(99));
        for (int id = 0; id < 4; ++id) {
            assertTrue(selection.isSelected(id));
        }
    }

    @Test
    void admitsOnlyIntoFreeSlots() {
        final CrowdSelection selection = new CrowdSelection();
        assertTrue(selection.admit(1, 2));
        assertTrue(selection.admit(2, 2));
        assertFalse(selection.admit(3, 2));
        assertEquals(2, selection.selectedCount());
    }

    @Test
    void doesNotFlickerAtTheBoundary() {
        final CrowdSelection selection = new CrowdSelection();

        int swaps = 0;
        boolean previous = false;
        for (int round = 0; round < 50; ++round) {
            offer(selection, 1, 10);
            offer(selection, 2, 30 + (round % 2 == 0 ? 1 : -1));
            offer(selection, 3, 30 + (round % 2 == 0 ? -1 : 1));
            selection.rebuild(2, SWAP);
            final boolean shown = selection.isSelected(2);
            if (round > 0 && shown != previous) {
                ++swaps;
            }
            previous = shown;
        }
        assertEquals(0, swaps);
    }

    @Test
    void windowedCandidatesExpireAndPersist() {
        final CrowdSelection selection = new CrowdSelection();

        selection.offer(1, 10 * 10, 0);
        selection.offer(2, 20 * 20, 0);
        selection.offer(3, 30 * 30, 0);
        selection.offer(4, 40 * 40, 5);
        selection.rebuild(2, SWAP, 0);
        assertTrue(selection.isSelected(1));
        assertTrue(selection.isSelected(2));

        selection.offer(3, 30 * 30, 6);
        selection.rebuild(2, SWAP, 3);
        assertFalse(selection.isSelected(1));
        assertFalse(selection.isSelected(2));
        assertTrue(selection.isSelected(3));
        assertTrue(selection.isSelected(4));

        selection.rebuild(2, SWAP, 4);
        assertTrue(selection.isSelected(3));
        assertTrue(selection.isSelected(4));
    }

    @Test
    void forceSelectTakesTheFarthestSlot() {
        final CrowdSelection selection = new CrowdSelection();
        selection.offer(1, 10 * 10, 0);
        selection.offer(2, 20 * 20, 0);
        selection.offer(3, 0.0, 0);
        selection.rebuild(3, SWAP, 0);
        selection.offer(9, 0.0, 1);
        assertEquals(2, selection.forceSelect(9, 3));
        assertEquals(3, selection.selectedCount());
        assertTrue(selection.isSelected(9));
        assertTrue(selection.isSelected(3));
        assertFalse(selection.isSelected(2));
    }

    @Test
    void neverSelectsMoreThanTheCap() {
        final CrowdSelection selection = new CrowdSelection();
        final java.util.Random random = new java.util.Random(11);
        for (int tick = 0; tick < 2000; ++tick) {
            for (int i = 0; i < 40; ++i) {
                final int id = random.nextInt(400);
                selection.offer(id, random.nextDouble() * 10000, tick);
                if (random.nextInt(50) == 0) {
                    selection.offer(id, 0.0, tick);
                    selection.forceSelect(id, 150);
                } else {
                    selection.admit(id, 150);
                }
                assertTrue(selection.selectedCount() <= 150);
            }
            if (tick % 5 == 0) {
                selection.rebuild(150, 1.0, tick - 21);
                assertTrue(selection.selectedCount() <= 150);
            }
        }
    }
}
