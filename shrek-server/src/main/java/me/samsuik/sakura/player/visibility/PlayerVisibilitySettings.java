// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.player.visibility;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PlayerVisibilitySettings implements VisibilitySettings {
    private static final String VISIBILITY_SETTINGS_TAG = "client_visibility_settings";

    // Shrek - region threading: settings can be read and written by different region threads
    // concurrently (tracker checks run on the entity's region, commands on the player's).
    private final Map<VisibilityType, VisibilityState> visibilityStates = new ConcurrentHashMap<>();
    // Shrek - cheap per-packet check: ConcurrentHashMap#isEmpty sums its counters on every call
    private volatile boolean modified;

    @Override
    public VisibilityState get(final VisibilityType type) {
        final VisibilityState state = this.visibilityStates.get(type);
        //noinspection ConstantValue
        return state != null ? state : type.getDefault();
    }

    @Override
    public VisibilityState set(final VisibilityType type, final VisibilityState state) {
        synchronized (this.visibilityStates) {
            if (type.isDefault(state)) {
                this.visibilityStates.remove(type);
            } else {
                this.visibilityStates.put(type, state);
            }
            this.modified = !this.visibilityStates.isEmpty();
        }
        return state;
    }

    @Override
    public VisibilityState currentState() {
        final int modifiedCount = this.visibilityStates.size();
        if (modifiedCount == 0) {
            return VisibilityState.ON;
        } else if (modifiedCount != VisibilityTypes.types().size()) {
            return VisibilityState.MODIFIED;
        } else {
            return VisibilityState.OFF;
        }
    }

    @Override
    public boolean playerModified() {
        return this.modified;
    }

    public void loadData(final ValueInput input) {
        input.child(VISIBILITY_SETTINGS_TAG).ifPresent(settings -> {
            for (final VisibilityType type : VisibilityTypes.types()) {
                final String typeKey = type.key();
                final String stateName = settings.getStringOr(typeKey, type.getDefault().name());
                this.set(type, VisibilityState.valueOf(stateName));
            }
        });
    }

    public void saveData(final ValueOutput output) {
        final ValueOutput settings = output.child(VISIBILITY_SETTINGS_TAG);
        this.visibilityStates.forEach((type, state) -> settings.putString(type.key(), state.name()));
    }
}
