package me.samsuik.sakura.player.item;

import me.samsuik.sakura.configuration.GlobalConfiguration;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DataComponentHelper {
    public static int bucketMaxStackSize() {
        final GlobalConfiguration config = GlobalConfiguration.get();
        if (config == null) {
            return -1;
        }
        return clampBucketStackSize(config.players.bucketStackSize.or(-1));
    }

    public static boolean itemHasComponent(final ItemStack stack, final DataComponentType<?> component) {
        return stack.getComponentsPatch().get(stack, component) != null;
    }

    public static DataComponentMap modify(final DataComponentMap components, final Consumer<DataComponentMap.Builder> consumer) {
        final DataComponentMap.Builder builder = DataComponentMap.builder()
            .addAll(components);
        consumer.accept(builder);
        return builder.build();
    }

    public static int clampBucketStackSize(final int stackSize) {
        if (stackSize > 0 && stackSize <= Item.ABSOLUTE_MAX_STACK_SIZE) {
            return stackSize;
        }
        return -1;
    }

    private DataComponentHelper() {
    }
}
