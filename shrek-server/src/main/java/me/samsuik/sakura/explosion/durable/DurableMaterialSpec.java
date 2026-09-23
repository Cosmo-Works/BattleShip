package me.samsuik.sakura.explosion.durable;

import org.jspecify.annotations.NullMarked;

@SuppressWarnings("FieldMayBeFinal")
@NullMarked
public class DurableMaterialSpec {
    public int durability = 1;
    public float resistance = 0.0f;
    public boolean onlyDamagedByTnt = true;

    public DurableMaterialSpec() {
    }

    public DurableMaterialSpec(final int durability, final float resistance, final boolean onlyDamagedByTnt) {
        this.durability = durability;
        this.resistance = resistance;
        this.onlyDamagedByTnt = onlyDamagedByTnt;
    }

    public DurableMaterial toMaterial() {
        return new DurableMaterial(this.durability, this.resistance, this.onlyDamagedByTnt);
    }
}
