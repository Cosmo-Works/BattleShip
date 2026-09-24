package gg.shrekmc;

import gg.shrekmc.region.RegionFileFormat;
import io.canvasmc.canvas.configuration.ConfigurationProvider;
import io.canvasmc.canvas.configuration.Part;
import io.canvasmc.canvas.configuration.Resolver;
import io.canvasmc.canvas.configuration.Style;
import io.canvasmc.canvas.configuration.Validator;
import io.canvasmc.canvas.util.CanonicalReference;
import java.nio.file.Path;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"FieldMayBeFinal", "unused"})
@NullMarked
public class ShrekServerConfig extends Part {

    private static final Logger LOGGER = LoggerFactory.getLogger("ShrekServer");
    private static final Path BASE_FILE = Path.of("config/shrek-server.yml").toAbsolutePath().normalize();
    private static final int CHAR_LIM = 90;

    private static ShrekServerConfig INSTANCE;

    static {
        try {
            reload();
        } catch (final Throwable thrown) {
            LOGGER.error("Couldn't load Shrek server configuration", thrown);
            throw thrown;
        }
    }

    public static void ensureLoaded() {
    }

    public static void reload() {
        ConfigurationProvider.buildSolidConfiguration(
            BASE_FILE,
            ShrekServerConfig::new,
            CHAR_LIM,
            new Resolver<>() {
                @Override
                public void onDiffAdd(final String fullyQualifiedName) {
                    LOGGER.info("Added new server configuration option, \"{}\"", fullyQualifiedName);
                }

                @Override
                public void onDiffRemove(final String fullyQualifiedName) {
                    LOGGER.warn("Server configuration option \"{}\" no longer exists and is now removed.", fullyQualifiedName);
                }

                @Override
                public void onFinishLoad(final ShrekServerConfig instance) {
                    Validator.validateObject(instance);
                    instance.linearFormat.resolveFormat();
                    instance.players.parseBucketStackSize();
                    INSTANCE = instance;
                    LOGGER.info("Loaded Shrek server configuration from {}", BASE_FILE);
                }
            },
            Style.create()
                .literal("Server-wide configuration file for Shrek").endLine()
                .blank()
                .wordWrap(
                    "Options here apply to the whole server and cannot be overridden per-world.",
                    "You are free to modify, add, or remove comments as you please."
                ).endLine()
                .blank()
                .wordWrap(
                    "Changing the region file format does not convert existing worlds. Existing .mca",
                    "or .linear region files are always opened as the format they already are."
                ).endLine()
                .compile(60)
        );
    }

    public static RegionFileFormat regionFormat() {
        final ShrekServerConfig config = INSTANCE;
        return config == null ? RegionFileFormat.ANVIL : config.linearFormat.resolvedFormat();
    }

    public static int compressionLevel() {
        final ShrekServerConfig config = INSTANCE;
        return config == null ? 1 : config.linearFormat.compressionLevel;
    }

    public static int flushFrequency() {
        final ShrekServerConfig config = INSTANCE;
        return config == null ? 10 : config.linearFormat.flushFrequency;
    }

    public static int flushMaxThreads() {
        final ShrekServerConfig config = INSTANCE;
        return config == null ? 1 : config.linearFormat.flushMaxThreads;
    }

    {
        option("linearFormat").docs(
            "Kaiiju Linear region files. LINEAR uses ZSTD and is typically around half the size of Anvil."
        );
    }

    public LinearFormat linearFormat = new LinearFormat();

    public static class LinearFormat extends Part {

        {
            option("format")
                .docs(
                    "Region file format for newly created files.",
                    "ANVIL: standard Minecraft .mca files.",
                    "LINEAR: Kaiiju Linear .linear files with ZSTD compression."
                )
                .word();
            option("compressionLevel")
                .docs("ZSTD compression level for Linear files (1-22).")
                .between(1, 22);
            option("flushFrequency")
                .docs("How often, in seconds, Linear region files are flushed to disk.")
                .greaterThanOrEqualTo(1);
            option("flushMaxThreads")
                .docs(
                    "Threads used to flush Linear region files. Negative values are treated as",
                    "available processors plus that value, with a minimum of 1."
                );
        }

        public String format = "ANVIL";
        public int compressionLevel = 1;
        public int flushFrequency = 10;
        public int flushMaxThreads = 1;

        public final CanonicalReference<RegionFileFormat> parsedFormat = new CanonicalReference<>();

        private void resolveFormat() {
            RegionFileFormat parsed;
            try {
                parsed = RegionFileFormat.valueOf(this.format.trim().toUpperCase(Locale.ENGLISH));
            } catch (final IllegalArgumentException ignored) {
                LOGGER.warn("Invalid linear format \"{}\", falling back to ANVIL", this.format);
                parsed = RegionFileFormat.ANVIL;
            }
            this.parsedFormat.setValue(parsed);
        }

        private RegionFileFormat resolvedFormat() {
            return this.parsedFormat.value();
        }
    }

    {
        option("messages").docs("Chat messages shown by Shrek tools.");
    }

    public Messages messages = new Messages();

    public static class Messages extends Part {

        {
            option("durableBlockInteraction")
                .docs(
                    "Shown when a potato is used on a durable block.",
                    "MiniMessage. Placeholders: <remaining>, <durability>. Brace placeholders still work."
                )
                .greedyString();
            option("fpsSettingChange")
                .docs(
                    "Shown when TNT or sand visibility is toggled from a command.",
                    "MiniMessage. Placeholders: <state>, <name>."
                )
                .greedyString();
            option("mechanicInformation")
                .docs(
                    "Shown by /shrek mechanic. MiniMessage.",
                    "The <information> tag wraps hover text for the mechanic dump."
                )
                .greedyString();
            option("tpsShowEntityAndChunkCount")
                .docs("Show entity and chunk counts on TPS graph hover text.");
        }

        public String durableBlockInteraction = "<dark_gray>(<light_purple>S</light_purple>) <white>This block has <gray><remaining></gray> of <gray><durability>";
        public String fpsSettingChange = "<dark_gray>(<light_purple>S</light_purple>) <gray><state> <yellow><name>";
        public String mechanicInformation = "<dark_gray>(<light_purple>S</light_purple>) <white>To view mechanic information: <dark_gray><information><<yellow>hover here</yellow>></information>";
        public boolean tpsShowEntityAndChunkCount = true;

        public Component durableBlockInteractionComponent(final int remaining, final int durability) {
            return MiniMessage.miniMessage().deserialize(
                withMiniMessagePlaceholders(this.durableBlockInteraction),
                Placeholder.unparsed("remaining", Integer.toString(remaining)),
                Placeholder.unparsed("durability", Integer.toString(durability))
            );
        }

        public Component fpsSettingChangeComponent(final String name, final String state) {
            return MiniMessage.miniMessage().deserialize(
                withMiniMessagePlaceholders(this.fpsSettingChange),
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("state", state)
            );
        }

        public Component mechanicInformationComponent(final Component hoverComponent) {
            return MiniMessage.miniMessage().deserialize(
                this.mechanicInformation,
                TagResolver.resolver("information", Tag.styling(HoverEvent.showText(hoverComponent)))
            );
        }

        private static String withMiniMessagePlaceholders(final String template) {
            return template
                .replace("{remaining}", "<remaining>")
                .replace("{durability}", "<durability>")
                .replace("{state}", "<state>")
                .replace("{name}", "<name>");
        }
    }

    {
        option("fps").docs("Client FPS settings GUI.");
    }

    public Fps fps = new Fps();

    public static class Fps extends Part {

        {
            option("material")
                .docs("Background pane material for the FPS settings inventory.")
                .word();
        }

        public String material = "PINK_STAINED_GLASS_PANE";
    }

    public static Component durableBlockInteraction(final int remaining, final int durability) {
        return messages().durableBlockInteractionComponent(remaining, durability);
    }

    public static Component fpsSettingChange(final String name, final String state) {
        return messages().fpsSettingChangeComponent(name, state);
    }

    public static Component mechanicInformation(final Component hoverComponent) {
        return messages().mechanicInformationComponent(hoverComponent);
    }

    public static boolean tpsShowEntityAndChunkCount() {
        return messages().tpsShowEntityAndChunkCount;
    }

    private static Messages messages() {
        final ShrekServerConfig config = INSTANCE;
        return config == null ? new Messages() : config.messages;
    }

    public static ShrekServerConfig get() {
        return INSTANCE;
    }

    public static Material fpsMaterial() {
        final ShrekServerConfig config = INSTANCE;
        final String name = config == null ? "PINK_STAINED_GLASS_PANE" : config.fps.material;
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ENGLISH));
        } catch (final IllegalArgumentException ignored) {
            LOGGER.warn("Invalid FPS GUI material \"{}\", falling back to PINK_STAINED_GLASS_PANE", name);
            return Material.PINK_STAINED_GLASS_PANE;
        }
    }

    {
        option("environment").docs("Server-wide environment shortcuts.");
    }

    public Environment environment = new Environment();

    public static class Environment extends Part {

        {
            option("calculateBiomeNoiseOncePerChunkSection")
                .docs(
                    "Fill a whole chunk section with one biome sample.",
                    "Intended for plot worlds. Changes chunk generation."
                );
            option("mobSpawnerDefaults").docs("Default NBT for newly created spawners. Overrides Canvas spawner defaults.");
        }

        public boolean calculateBiomeNoiseOncePerChunkSection = false;
        public MobSpawnerDefaults mobSpawnerDefaults = new MobSpawnerDefaults();

        public static class MobSpawnerDefaults extends Part {

            {
                option("minSpawnDelay").docs("Minimum ticks between spawns.");
                option("maxSpawnDelay").docs("Maximum ticks between spawns.");
                option("spawnCount").docs("Mobs per spawn attempt.");
                option("maxNearbyEntities").docs("Nearby-entity cap.");
                option("requiredPlayerRange").docs("Player range required to tick.");
                option("spawnRange").docs("Spawn box radius.");
            }

            public int minSpawnDelay = 200;
            public int maxSpawnDelay = 800;
            public int spawnCount = 4;
            public int maxNearbyEntities = 6;
            public int requiredPlayerRange = 16;
            public int spawnRange = 4;
        }
    }

    public static boolean calculateBiomeNoiseOncePerChunkSection() {
        final ShrekServerConfig config = INSTANCE;
        return config != null && config.environment.calculateBiomeNoiseOncePerChunkSection;
    }

    public static Environment.MobSpawnerDefaults mobSpawnerDefaults() {
        final ShrekServerConfig config = INSTANCE;
        return config == null ? new Environment.MobSpawnerDefaults() : config.environment.mobSpawnerDefaults;
    }

    {
        option("players").docs("Server-wide player item behaviour: swords, spears, and bucket stacks.");
    }

    public Players players = new Players();

    public static class Players extends Part {

        {
            option("combat").docs("Sword blocking and spear reach.");
            option("bucketStackSize")
                .docs("Max stack size for buckets. \"default\" is vanilla.")
                .word();
            option("stackableMilkBuckets")
                .docs("Show milk buckets stacked on the client when bucket-stack-size is set.");
            option("stackablePowderedSnowBuckets")
                .docs("Show powdered snow buckets stacked on the client when bucket-stack-size is set.");
        }

        public Combat combat = new Combat();
        public String bucketStackSize = "default";
        public boolean stackableMilkBuckets = false;
        public boolean stackablePowderedSnowBuckets = false;
        public final CanonicalReference<Integer> parsedBucketStackSize = new CanonicalReference<>();

        public static class Combat extends Part {

            {
                option("blockWithSwords")
                    .docs("Swords can block like 1.8. Sent to the client as a BLOCKS_ATTACKS component.");
                option("spearsHaveExtraAttackRange")
                    .docs("When false, spears use a shorter attack range.");
            }

            public boolean blockWithSwords = false;
            public boolean spearsHaveExtraAttackRange = true;
        }

        public int bucketStackSizeOrDefault() {
            final Integer parsed = this.parsedBucketStackSize.valueSafe();
            return parsed == null ? -1 : parsed;
        }

        private void parseBucketStackSize() {
            final String trimmed = this.bucketStackSize.trim().toLowerCase(Locale.ENGLISH);
            if (trimmed.isEmpty() || trimmed.equals("default") || trimmed.equals("vanilla")) {
                this.parsedBucketStackSize.setValue(-1);
                return;
            }
            try {
                final int value = Integer.parseInt(trimmed);
                if (value > 0 && value <= net.minecraft.world.item.Item.ABSOLUTE_MAX_STACK_SIZE) {
                    this.parsedBucketStackSize.setValue(value);
                    return;
                }
            } catch (final NumberFormatException ignored) {
            }
            LOGGER.warn("Invalid bucket stack size \"{}\", falling back to default", this.bucketStackSize);
            this.parsedBucketStackSize.setValue(-1);
        }
    }

    {
        option("knockbackSync").docs(
            "Rewind a player's ping when applying melee knockback so high-latency hits feel like they did on the client.",
            "Same-world only. Uses Paper keepalives, sending them more often while the player is in combat."
        );
    }

    public KnockbackSync knockbackSync = new KnockbackSync();

    public static class KnockbackSync extends Part {

        {
            option("enabled")
                .docs("Apply ping-compensated knockback to player melee hits.");
            option("pingOffset")
                .docs("Milliseconds subtracted from measured ping before rewind. Knockback-sync's tested default is 25.")
                .between(0, 1000);
            option("spikeThreshold")
                .docs("If ping jumps by this many milliseconds or more, use the previous ping instead.")
                .greaterThanOrEqualTo(0);
            option("combatPingInterval")
                .docs("Keepalive interval in ticks while a player is in combat. Paper's idle interval is 20 ticks.")
                .between(1, 20);
            option("combatTimer")
                .docs("Ticks after a melee hit before combat pinging stops.")
                .greaterThanOrEqualTo(1);
            option("offGround")
                .docs("When the client would still be in the air, rewind falling velocity by ping instead of giving grounded knockback.");
        }

        public boolean enabled = true;
        public int pingOffset = 25;
        public int spikeThreshold = 20;
        public int combatPingInterval = 5;
        public int combatTimer = 30;
        public boolean offGround = true;
    }
}
