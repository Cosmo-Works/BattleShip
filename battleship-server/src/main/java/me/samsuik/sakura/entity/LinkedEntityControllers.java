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
public final class LinkedEntityControllers {
    private static final Comparator<SakuraEntityController> BY_ENTITY_ID = Comparator.comparingInt(
        controller -> controller.sakuraEntity.asEntity().getId()
    );

    private final List<SakuraEntityController> linked = new ObjectArrayList<>();
    private final LongOpenHashSet linkedSpawnPositions;
    private SakuraEntityController headController;
    private SakuraEntityController tailController;
    public @Nullable SakuraEntityController lastToTick;

    public LinkedEntityControllers(final SakuraEntityController controller) {
        this.linked.add(controller);
        this.linkedSpawnPositions = new LongOpenHashSet();
        this.updateHeadAndTail();
    }

    private LinkedEntityControllers(final List<SakuraEntityController> controllers, final LongOpenHashSet linkedSpawnPositions) {
        this.linked.addAll(controllers);
        this.linkedSpawnPositions = linkedSpawnPositions;
        this.updateHeadAndTail();
    }

    public SakuraEntityController getHead() {
        return this.headController;
    }

    public SakuraEntityController getTail() {
        return this.tailController;
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

    public void link(final SakuraEntityController controller, final LinkedEntityControllers links) {
        this.linked.addAll(links.linked);
        this.linked.sort(BY_ENTITY_ID);
        this.linkedSpawnPositions.add(controller.encodedSpawnPosition());
        this.linkedSpawnPositions.addAll(links.linkedSpawnPositions);

        links.linked.forEach(otherController -> otherController.updateLinks(this));
        this.updateHeadAndTail();
    }

    public @Nullable SakuraEntityController decouple(final SakuraEntityController otherController) {
        final int index = this.linked.indexOf(otherController);
        if (index < 0 || index + 1 >= this.linked.size()) {
            return null;
        }

        final List<SakuraEntityController> subList = this.linked.subList(0, index + 1);
        final List<SakuraEntityController> decoupledControllers = new ObjectArrayList<>(subList);
        subList.clear();

        final LinkedEntityControllers decoupled = new LinkedEntityControllers(decoupledControllers, this.linkedSpawnPositions);
        decoupledControllers.forEach(decoupledController -> decoupledController.updateLinks(decoupled));
        decoupled.updateHeadAndTail();

        final SakuraEntityController newHead = this.linked.getFirst();
        this.headController = newHead;
        return newHead;
    }

    public void save(final PersistentEntityData persistentEntityData) {
        persistentEntityData.addLinkedPositions(this.linkedSpawnPositions);
    }

    private void updateHeadAndTail() {
        this.headController = this.linked.getFirst();
        this.tailController = this.linked.getLast();
    }
}
