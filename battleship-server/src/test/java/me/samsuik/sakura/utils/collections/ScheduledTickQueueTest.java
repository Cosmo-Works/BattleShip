package me.samsuik.sakura.utils.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("Battleship")
class ScheduledTickQueueTest {
    // The queue must hand out ticks in the same order as the priority queue it replaces
    @Test
    void drainsInTheSameOrderAsAPriorityQueue() {
        final Random random = new Random(42L);
        final TickPriority[] priorities = TickPriority.values();
        final PriorityQueue<ScheduledTick<String>> expected = new PriorityQueue<>(ScheduledTick.DRAIN_ORDER);
        final ScheduledTickQueue<ScheduledTick<String>> queue = new ScheduledTickQueue<>();
        long subTickOrder = 0L;

        for (int step = 0; step < 20_000; ++step) {
            final int action = random.nextInt(10);
            if (action < 6) {
                final ScheduledTick<String> tick = new ScheduledTick<>(
                    "type", BlockPos.ZERO, random.nextInt(40), priorities[random.nextInt(priorities.length)], subTickOrder++
                );
                expected.add(tick);
                queue.add(tick);
            } else if (action < 9) {
                assertSame(expected.peek(), queue.peek());
                assertSame(expected.poll(), queue.poll());
            } else {
                // removeIf goes through the iterator
                final long removeBelow = random.nextInt(40);
                expected.removeIf(tick -> tick.triggerTick() < removeBelow && tick.subTickOrder() % 3 == 0);
                final Iterator<ScheduledTick<String>> iterator = queue.iterator();
                while (iterator.hasNext()) {
                    final ScheduledTick<String> tick = iterator.next();
                    if (tick.triggerTick() < removeBelow && tick.subTickOrder() % 3 == 0) {
                        iterator.remove();
                    }
                }
            }
            assertEquals(expected.size(), queue.size());
        }

        while (!expected.isEmpty()) {
            assertSame(expected.poll(), queue.poll());
        }
        assertEquals(0, queue.size());
    }
}
