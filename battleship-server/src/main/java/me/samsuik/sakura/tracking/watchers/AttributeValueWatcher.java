package me.samsuik.sakura.tracking.watchers;

import me.samsuik.sakura.tracking.ChangeListener;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class AttributeValueWatcher implements ChangeListener<AttributeInstance> {
    private final AttributeMap attributes;
    private final Holder<Attribute> holder;
    private @Nullable AttributeInstance instance;
    private double value;
    private boolean changed = true;

    public AttributeValueWatcher(final AttributeMap attributes, final Holder<Attribute> holder) {
        this.attributes = attributes;
        this.holder = holder;
    }

    @Override
    public void changed(final AttributeInstance instance) {
        this.changed = true;
    }

    public double getValue() {
        if (!this.changed) {
            return this.value;
        }

        final AttributeInstance instance = this.attributes.getInstance(this.holder);
        if (instance == null) {
            return this.attributes.supplyDefaultValue(this.holder);
        }

        this.instance = instance;
        instance.watch(this);

        this.value = instance.getValue();
        this.changed = false;
        return this.value;
    }
}
