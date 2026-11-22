package dev.cosmoworks.battleship;

import io.canvasmc.canvas.configuration.ConfigurationProvider;
import io.canvasmc.canvas.configuration.Part;
import io.canvasmc.canvas.configuration.Resolver;
import io.canvasmc.canvas.configuration.Style;
import io.canvasmc.canvas.configuration.Validator;
import io.canvasmc.canvas.util.CanonicalReference;
import io.papermc.paper.configuration.WorldConfiguration;
import io.papermc.paper.threadedregions.TickRegions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.samsuik.sakura.entity.BrainActivation;
import me.samsuik.sakura.explosion.ProtectionBlocks;
import me.samsuik.sakura.world.KeepLoadedArea;
import me.samsuik.sakura.world.KeepLoadedChunks;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"FieldMayBeFinal", "unused"})
@NullMarked
public class BattleshipWorldConfig extends Part {
    private static final Logger LOGGER = LoggerFactory.getLogger("BattleshipWorlds");
    private static final Path BASE_FILE = Path.of("config/battleship-worlds.yml").toAbsolutePath().normalize();
    private static final int CHAR_LIM = 90;

    static {
        try {
            reload();
        } catch (final Throwable thrown) {
            LOGGER.error("Couldn't load Battleship worlds default configuration", thrown);
            throw thrown;
        }
    }

    public static void ensureLoaded() {
    }

    public static void reload() {
        ConfigurationProvider.buildSolidConfiguration(
            BASE_FILE,
            BattleshipWorldConfig::new,
            CHAR_LIM,
            new Resolver<>() {
                @Override
                public void onDiffAdd(final String fullyQualifiedName) {
                    LOGGER.info("Added new world configuration option, \"{}\"", fullyQualifiedName);
                }

                @Override
                public void onDiffRemove(final String fullyQualifiedName) {
                    LOGGER.warn("World configuration option \"{}\" no longer exists and is now removed.", fullyQualifiedName);
                }

                @Override
                public void onFinishLoad(final BattleshipWorldConfig instance) {
                    Validator.validateObject(instance);
                    LOGGER.info("Loaded Battleship world defaults from {}", BASE_FILE);
                }
            },
            Style.create()
                .literal("Worlds default configuration file for Battleship").endLine()
                .blank()
                .wordWrap(
                    "This is the defaults for the per-world configuration file for Battleship.",
                    "Each option can be overridden by the patch variant in each dimension folder. You are",
                    "free to modify, add, or remove comments as you please."
                ).endLine()
                .blank()
                .wordWrap(
                    "Battleship is a cannoning-focused fork of Canvas, porting the cannon mechanics",
                    "and tooling of Sakura (https://github.com/Samsuik/Sakura) to region threading."
                ).endLine()
                .compile(60)
        );

        if (TickRegions.hasStarted()) {
            for (final ServerLevel level : MinecraftServer.getServer().getAllLevels()) {
                level.reloadBattleshipConfig();
            }
        }
    }

    public static BattleshipWorldConfig buildForLevel(final ServerLevel level, final ResourceKey<Level> dimension) {
        final BattleshipWorldConfig[] result = new BattleshipWorldConfig[1];

        ConfigurationProvider.buildPatchableConfiguration(
            MinecraftServer.getServer().storageSource.getDimensionPath(dimension).resolve("battleship-patch.yml"),
            BASE_FILE,
            BattleshipWorldConfig::new,
            instance -> {
                LOGGER.info("Loaded Battleship config patch for level {}", dimension.identifier());

                result[0] = instance;

                instance.onLoad(level);
            },
            Style.create()
                .literal("Patch configuration file for world " + dimension.identifier()).endLine()
                .blank()
                .wordWrap(
                    "This configuration file can be used to override the values in the default configuration",
                    "for worlds defined in \"/config/battleship-worlds.yml\""
                ).endLine()
                .blank()
                .wordWrap(
                    "To override values in this, just copy the same option path to override the value. Think of",
                    "the values you place in here for each option as a replacement for the default one for this world specifically"
                )
                .compile(60)
        );

        return result[0];
    }

    private void onLoad(final ServerLevel level) {
        Validator.validateObject(this);

        final String worldName = level.dimension().identifier().toString();
        cannons.explosion.parsedProtectionBlocks.setValue(
            ProtectionBlocks.parse(cannons.explosion.protectionBlocks, LOGGER, worldName)
        );
        redstone.parsedImplementation.setValue(parseRedstoneImplementation(redstone.implementation, level, worldName));
        BrainActivation.parseBlacklist(entities.brainActivation, LOGGER, worldName);
        KeepLoadedChunks.sync(level);
    }

    private static WorldConfiguration.Misc.RedstoneImplementation parseRedstoneImplementation(
        final String raw,
        final ServerLevel level,
        final String worldName
    ) {
        final String trimmed = raw.trim().toLowerCase(Locale.ENGLISH);
        return switch (trimmed) {
            case "", "paper", "default" -> level.paperConfig().misc.redstoneImplementation;
            case "vanilla" -> WorldConfiguration.Misc.RedstoneImplementation.VANILLA;
            case "eigencraft" -> WorldConfiguration.Misc.RedstoneImplementation.EIGENCRAFT;
            case "alternate-current", "alternatecurrent", "ac" -> WorldConfiguration.Misc.RedstoneImplementation.ALTERNATE_CURRENT;
            default -> {
                LOGGER.warn("Invalid redstone implementation \"{}\" for level {}, falling back to paper", raw, worldName);
                yield level.paperConfig().misc.redstoneImplementation;
            }
        };
    }

    {
        option("redstone").docs("Redstone wire algorithm.");
    }

    public Redstone redstone = new Redstone();
    public static class Redstone extends Part {
        {
            option("implementation")
                .docs(
                    "Which redstone wire algorithm to use.",
                    "paper: follow paper-world.yml, vanilla, eigencraft, or alternate-current.",
                    "Legacy mechanics-target always uses panda wire instead."
                ).word();
        }

        public String implementation = "paper";
        public final CanonicalReference<WorldConfiguration.Misc.RedstoneImplementation> parsedImplementation = new CanonicalReference<>();
    }

    {
        option("cannons").docs("Cannon-specific gameplay options.");
    }

    public Cannons cannons = new Cannons();
    public static class Cannons extends Part {
        {
            option("crossRegions")
                .docs(
                    "Let TNT and falling blocks continue into neighbouring Folia regions instead of bouncing off the region edge.",
                    "The destination region takes ownership through Canvas entity transfer. Same-world only."
                );
        }

        public boolean crossRegions = true;

        {
            option("tnt").docs("Primed TNT behaviour.");
        }

        public Tnt tnt = new Tnt();
        public static class Tnt extends Part {
            {
                option("removeMaxTntPerTick")
                    .docs(
                        "Removes Spigot's max-tnt-ticks-per-tick limit.",
                        "The limit breaks large cannons by silently skipping TNT ticks."
                    );
            }

            public boolean removeMaxTntPerTick = true;
        }

        {
            option("sand").docs("Falling block (sand) behaviour.");
        }

        public Sand sand = new Sand();
        public static class Sand extends Part {
            {
                option("forcePositionUpdates")
                    .docs(
                        "Force tracker position packets for falling blocks that are touching water and not pushed by fluid.",
                        "Some clients otherwise keep a ghost sand in the barrel after TNT launches it."
                    );
            }

            public boolean forcePositionUpdates = false;
        }

        {
            option("sulfur").docs("Sulfur cube cannon behaviour.");
        }

        public Sulfur sulfur = new Sulfur();
        public static class Sulfur extends Part {
            {
                option("heightParity").docs(
                    "Uses the entity position instead of the eye position for height-based checks.",
                    "Always disabled when emulating legacy (1.8) mechanics."
                );
            }

            public boolean heightParity = false;
        }

        {
            option("explosion").docs("Explosion protection blocks.");
        }

        public Explosion explosion = new Explosion();
        public static class Explosion extends Part {
            {
                option("protectionBlocks")
                    .docs(
                        "Block ids that cancel explosion block damage when found in the same-world column below the blast.",
                        "This is the native form of TNT protection-block plugins. Empty turns the search off."
                    );
            }

            public List<String> protectionBlocks = new ArrayList<>();
            public final CanonicalReference<Set<Block>> parsedProtectionBlocks = new CanonicalReference<>();

            public Set<Block> protectionBlocks() {
                final Set<Block> parsed = this.parsedProtectionBlocks.valueSafe();
                return parsed != null ? parsed : Set.of();
            }
        }
    }

    {
        option("entities").docs("Mob despawning, tracker culling, projectile and activation rules.");
    }

    public Entities entities = new Entities();
    public static class Entities extends Part {
        {
            option("despawnAfterPickingUpItems")
                .docs(
                    "Mobs that pick up gear can still despawn. Nametagged mobs stay. Picked items drop when they despawn.",
                    "Same-world and same Folia region only."
                );
            option("trackerOcclusionCulling")
                .docs(
                    "Hide entities from the tracker when a solid block is between the player's eyes and the entity.",
                    "Cannon entities, glowing entities, and spectators are never culled. Same-world and same region only."
                );
            option("skipSameTypeProjectileHits")
                .docs("Snowballs, pearls, and similar projectiles skip colliding with another of the same type. Arrows still collide.");
            option("brainActivation")
                .docs(
                    "Tick mob brains and pathfinder goals less often the farther they are from a player.",
                    "Same-world and same Folia region only. Nearby mobs keep full AI.",
                    "Mobs in water keep full AI so they can swim. Based on Pufferfish DAB."
                );
            option("verticalActivationRange")
                .docs(
                    "How far above and below a player entities stay fully active.",
                    "Spigot's activation range is a full-height cylinder. 0 keeps that. 16 is a usual cap for stacked farms.",
                    "Same-world and same Folia region only. Matches Spigot's animals, monsters, raiders, misc, water, villagers, and flying-monsters groups."
                );
        }

        public boolean despawnAfterPickingUpItems = true;
        public boolean trackerOcclusionCulling = true;
        public boolean skipSameTypeProjectileHits = true;
        public BrainActivation brainActivation = new BrainActivation();
        public VerticalActivationRange verticalActivationRange = new VerticalActivationRange();

        public static class BrainActivation extends Part {
            {
                option("enabled")
                    .docs("Throttle distant mob AI. Leave on unless you want full vanilla brains at any range.");
                option("startDistance")
                    .docs("Blocks to the nearest player before throttling starts.")
                    .greaterThanOrEqualTo(0);
                option("maxTickFreq")
                    .docs("Furthest mobs still think at least this often, in ticks. 20 is once per second.")
                    .greaterThanOrEqualTo(1);
                option("activationDistMod")
                    .docs(
                        "Higher means distant mobs think more often. 8 is the usual value. Do not go below 6."
                    )
                    .between(6, 16);
                option("skipInWater")
                    .docs("Do not throttle non-aquatic mobs that are in water, so they can still swim.");
                option("blacklistedEntities")
                    .docs("Entity ids that always keep full AI, e.g. villager or minecraft:warden.");
            }

            public boolean enabled = true;
            public int startDistance = 12;
            public int maxTickFreq = 20;
            public int activationDistMod = 8;
            public boolean skipInWater = true;
            public List<String> blacklistedEntities = new ArrayList<>();
            public final CanonicalReference<Set<EntityType<?>>> parsedBlacklist = new CanonicalReference<>();

            public int startDistanceSquared() {
                return this.startDistance * this.startDistance;
            }
        }

        public static class VerticalActivationRange extends Part {
            {
                option("animals")
                    .docs("Blocks above and below a player for animals. 0 is the full world height.")
                    .greaterThanOrEqualTo(0);
                option("monsters")
                    .docs("Blocks above and below a player for monsters. 0 is the full world height.")
                    .greaterThanOrEqualTo(0);
                option("raiders")
                    .docs("Blocks above and below a player for raiders. 0 is the full world height.")
                    .greaterThanOrEqualTo(0);
                option("misc")
                    .docs("Blocks above and below a player for misc entities. 0 is the full world height.")
                    .greaterThanOrEqualTo(0);
                option("water")
                    .docs("Blocks above and below a player for water mobs. 0 is the full world height.")
                    .greaterThanOrEqualTo(0);
                option("villagers")
                    .docs("Blocks above and below a player for villagers. 0 is the full world height.")
                    .greaterThanOrEqualTo(0);
                option("flyingMonsters")
                    .docs("Blocks above and below a player for flying monsters. 0 is the full world height.")
                    .greaterThanOrEqualTo(0);
            }

            public int animals = 0;
            public int monsters = 0;
            public int raiders = 0;
            public int misc = 0;
            public int water = 0;
            public int villagers = 0;
            public int flyingMonsters = 0;
        }

    }

    {
        option("chunks").docs("Keep chosen areas loaded so joining those chunks does not stall on disk I/O.");
    }

    public Chunks chunks = new Chunks();
    public static class Chunks extends Part {
        {
            option("keepLoaded")
                .docs(
                    "Named squares to load at startup and keep loaded until shutdown.",
                    "x and z are block coordinates in this world. size is the width in chunks of the square around that point.",
                    "Example: size 8 around 0, 0 keeps an 8 by 8 chunk square centered on block 0, 0.",
                    "Same-world only. Size is capped at 64."
                );
        }

        public Map<String, KeepLoadedArea> keepLoaded = new LinkedHashMap<>();
    }
}
