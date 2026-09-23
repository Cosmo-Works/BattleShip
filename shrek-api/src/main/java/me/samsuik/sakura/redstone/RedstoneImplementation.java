// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.redstone;

import org.jspecify.annotations.NullMarked;

@NullMarked
public enum RedstoneImplementation {
    VANILLA("vanilla"),
    EIGENCRAFT("eigencraft"),
    ALTERNATE_CURRENT("alternate-current");

    private final String friendlyName;

    RedstoneImplementation(final String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public String getFriendlyName() {
        return this.friendlyName;
    }
}
