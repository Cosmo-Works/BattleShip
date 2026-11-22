package dev.cosmoworks.battleship.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.cosmoworks.battleship.BattleshipServerConfig;
import io.papermc.paper.adventure.AdventureComponent;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@AllFeatures
class AdventureCollapseTest {
    private final boolean before = BattleshipServerConfig.network().adventureCollapseWithoutConversion;

    @AfterEach
    void restore() {
        BattleshipServerConfig.network().adventureCollapseWithoutConversion = this.before;
    }

    private static List<Component> samples() {
        return List.of(
            Component.text("plain"),
            Component.text(""),
            Component.empty(),
            Component.text("red", NamedTextColor.RED),
            Component.text("bold").decorate(TextDecoration.BOLD),
            Component.text("not bold").decoration(TextDecoration.BOLD, TextDecoration.State.FALSE),
            Component.text("unset").decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET),
            Component.text("click").clickEvent(ClickEvent.runCommand("/f")),
            Component.text("insert").insertion("x"),
            Component.text("font").font(Key.key("minecraft", "uniform")),
            Component.text("parent").append(Component.text("child")),
            Component.text("").append(Component.text("only child")),
            Component.text("a").append(Component.empty()),
            Component.text().content("styled parent").color(NamedTextColor.GOLD).append(Component.text("x")).build(),
            Component.text("empty style", Style.empty()),
            Component.translatable("chat.type.text", Component.text("a"), Component.text("b")),
            Component.translatable("item.minecraft.diamond"),
            Component.keybind("key.jump"),
            Component.score("name", "objective"),
            Component.selector("@p"),
            Component.blockNBT("path", net.kyori.adventure.text.BlockNBTComponent.Pos.fromString("1 2 3"))
        );
    }

    @Test
    void sameAnswerAsConverting() {
        for (final Component adventure : samples()) {
            BattleshipServerConfig.network().adventureCollapseWithoutConversion = false;
            final String expected = new AdventureComponent(adventure).tryCollapseToString();
            BattleshipServerConfig.network().adventureCollapseWithoutConversion = true;
            final AdventureComponent fast = new AdventureComponent(adventure);
            assertEquals(expected, fast.tryCollapseToString(), () -> "for " + adventure);
        }
    }

    @Test
    void plainAndNonTextComponentsAreNotConverted() {
        BattleshipServerConfig.network().adventureCollapseWithoutConversion = true;
        for (final Component adventure : List.of(Component.text("plain"), Component.text("a").append(Component.text("b")), Component.translatable("x"))) {
            final AdventureComponent component = new AdventureComponent(adventure);
            component.tryCollapseToString();
            assertNull(component.deepConvertedIfPresent(), () -> "converted " + adventure);
        }
    }
}
