// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.redstone;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record RedstoneConfiguration(RedstoneImplementation implementation, boolean cache) {
    public static RedstoneConfiguration withoutCache(final RedstoneImplementation implementation) {
        return new RedstoneConfiguration(implementation, false);
    }

    public static RedstoneConfiguration withCache(final RedstoneImplementation implementation) {
        return new RedstoneConfiguration(implementation, true);
    }
}
