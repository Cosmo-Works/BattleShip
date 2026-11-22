package me.samsuik.sakura.tps;

import dev.cosmoworks.battleship.BattleshipServerConfig;
import me.samsuik.sakura.tps.graph.GraphComponents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record ServerTickInformation(
    long identifier,
    double tps,
    double averageTick,
    long longestTick,
    float targetTickRate,
    int chunks,
    int entities
) {
    public static final ServerTickInformation UNKNOWN = new ServerTickInformation(0, 0.0, 0.0, 0, 0.0f, 0, 0);

    public TextColor colour() {
        if (this.targetTickRate <= 0.0f) {
            return GraphComponents.colour(0.0f);
        }
        final float tpsLoss = (float) this.tps / this.targetTickRate;
        return GraphComponents.colour(tpsLoss);
    }

    public Component hoverComponent(final TextColor colour) {
        final TextComponent.Builder builder = Component.text();
        builder.append(Component.text("TPS: ")
            .append(Component.text("%.1f".formatted(this.tps), colour)));
        builder.appendNewline();
        builder.append(Component.text("MSPT: ")
            .append(Component.text("%.1f".formatted(this.averageTick), colour))
            .append(Component.text("/"))
            .append(Component.text(this.longestTick, colour)));
        if (BattleshipServerConfig.tpsShowEntityAndChunkCount()) {
            builder.appendNewline();
            builder.append(Component.text("Entities: " + this.entities));
            builder.appendNewline();
            builder.append(Component.text("Chunks: " + this.chunks));
        }
        return builder.build();
    }
}
