package dev.cosmoworks.battleship.event.block;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class BlockPreGrowEvent extends BlockEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Block target;
    private final Material growing;
    private final int height;
    private boolean cancelled;

    public BlockPreGrowEvent(final Block block, final Block target, final Material growing, final int height) {
        super(block);
        this.target = target;
        this.growing = growing;
        this.height = height;
    }

    public Block getTarget() {
        return this.target;
    }

    public Material getGrowing() {
        return this.growing;
    }

    public int getHeight() {
        return this.height;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    public static boolean hasListeners() {
        return HANDLER_LIST.getRegisteredListeners().length > 0;
    }
}
