package me.samsuik.sakura.entity;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;
import me.samsuik.sakura.entity.data.PersistentEntityData;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class LinkedControllers {
    private static final Comparator<SakuraEntityController> BY_TICK_ORDER = Comparator.comparingInt(
        controller -> controller.entity.sakura$getTickOrder()
    );

    private final List<SakuraEntityController> linked = new ObjectArrayList<>();
    private final LongOpenHashSet linkedSpawnPositions;

    public LinkedControllers(final SakuraEntityController controller) {
        this.linked.add(controller);
        this.linkedSpawnPositions = new LongOpenHashSet();
    }

    private LinkedControllers(final List<SakuraEntityController> controllers, final LongOpenHashSet linkedSpawnPositions) {
        this.linked.addAll(controllers);
        this.linkedSpawnPositions = linkedSpawnPositions;
    }

    public SakuraEntityController getHead() {
        return this.linked.getFirst();
    }

    public int count() {
        return this.linked.size();
    }

    public boolean membersWouldSeparate(final ToLongFunction<SakuraEntityController> regionKey) {
        long first = Long.MIN_VALUE;
        boolean seen = false;
        for (final SakuraEntityController controller : this.linked) {
            final long key = regionKey.applyAsLong(controller);
            if (!seen) {
                first = key;
                seen = true;
            } else if (first != key) {
                return true;
            }
        }
        return false;
    }

    public void link(final SakuraEntityController controller, final LinkedControllers links) {
        this.linked.addAll(links.linked);
        this.linked.sort(BY_TICK_ORDER);
        this.linkedSpawnPositions.add(controller.encodedSpawnPosition());
        this.linkedSpawnPositions.addAll(links.linkedSpawnPositions);

        links.linked.forEach(linkedController -> linkedController.updateLinks(this));
    }

    private int findClosestIndex(final int tickOrder) {
        int low = 0;
        int high = this.linked.size() - 1;

        while (low <= high) {
            final int mid = (low + high) >>> 1;
            final int midId = this.linked.get(mid).entity.sakura$getTickOrder();

            if (midId <= tickOrder) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return high;
    }

    public @Nullable Controllers decouple(final int tickOrder) {
        final int index = this.findClosestIndex(tickOrder);
        if (index < 0 || index + 1 >= this.linked.size()) {
            return null;
        }

        final List<SakuraEntityController> toDecouple = this.linked.subList(0, index + 1);
        final List<SakuraEntityController> decoupledControllers = new ObjectArrayList<>(toDecouple);
        toDecouple.clear();

        final LinkedControllers decoupled = new LinkedControllers(decoupledControllers, this.linkedSpawnPositions);
        decoupledControllers.forEach(linkedController -> linkedController.updateLinks(decoupled));

        return new Controllers(decoupled, this);
    }

    public void save(final PersistentEntityData persistentEntityData) {
        persistentEntityData.addLinkedPositions(this.linkedSpawnPositions);
    }

    public record Controllers(LinkedControllers oldest, LinkedControllers newest) {
    }
}
