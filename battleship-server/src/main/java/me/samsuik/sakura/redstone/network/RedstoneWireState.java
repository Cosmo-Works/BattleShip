// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.redstone.network;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record RedstoneWireState(
    BlockPos position,
    int oldPower,
    int newPower,
    boolean updateShapes,
    boolean skip,
    int updateIndex
) {
    public RedstoneWireState newPower(final int newPower) {
        return new RedstoneWireState(this.position, this.oldPower, newPower, this.updateShapes, this.skip, this.updateIndex);
    }

    public RedstoneWireState updateShapes(final boolean updateShapes) {
        return new RedstoneWireState(this.position, this.oldPower, this.newPower, updateShapes, this.skip, this.updateIndex);
    }

    public RedstoneWireState skip(final boolean skip) {
        return new RedstoneWireState(this.position, this.oldPower, this.newPower, this.updateShapes, this.skip, this.updateIndex);
    }

    public boolean isRedundantWireUpdate(final RedstoneWireState wireState) {
        return this.oldPower != wireState.oldPower && this.newPower != wireState.newPower;
    }
}
