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
import me.samsuik.sakura.entity.EntityTravelLimits;
import me.samsuik.sakura.entity.TntSpread;
import me.samsuik.sakura.entity.merge.MergeLevel;
import me.samsuik.sakura.item.ItemIds;
import me.samsuik.sakura.explosion.ProtectionBlocks;
import me.samsuik.sakura.explosion.durable.DurableMaterial;
import me.samsuik.sakura.explosion.durable.DurableMaterialParser;
import me.samsuik.sakura.explosion.durable.DurableMaterialSpec;
import me.samsuik.sakura.mechanics.MinecraftMechanicsTarget;
import me.samsuik.sakura.world.KeepLoadedArea;
import me.samsuik.sakura.world.KeepLoadedChunks;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

        final String rawTarget = mechanics.mechanicsTarget.trim();
        MinecraftMechanicsTarget parsed;
        try {
            parsed = MinecraftMechanicsTarget.fromString(rawTarget);
        } catch (final RuntimeException ex) {
            LOGGER.warn("Invalid mechanics-target \"{}\" for level {}, falling back to \"latest\"",
                rawTarget, level.dimension().identifier());
            parsed = MinecraftMechanicsTarget.latest();
        }
        mechanics.parsedTarget.setValue(parsed);

        if (!parsed.is(MinecraftMechanicsTarget.latest().mechanicVersion())) {
            LOGGER.info("Level {} is emulating cannon mechanics for {}", level.dimension().identifier(), parsed);
        }

        final String rawSpread = cannons.tnt.spread.trim().toLowerCase(Locale.ENGLISH);
        final TntSpread parsedSpread = switch (rawSpread) {
            case "all", "default" -> TntSpread.ALL;
            case "y" -> TntSpread.Y;
            case "none" -> TntSpread.NONE;
            default -> {
                LOGGER.warn("Invalid tnt spread \"{}\" for level {}, falling back to \"all\"",
                    rawSpread, level.dimension().identifier());
                yield TntSpread.ALL;
            }
        };
        cannons.tnt.parsedSpread.setValue(parsedSpread);

        final String rawMerge = cannons.mergeLevel.trim().toLowerCase(Locale.ENGLISH);
        final MergeLevel parsedMerge = switch (rawMerge) {
            case "none" -> MergeLevel.NONE;
            case "strict" -> MergeLevel.STRICT;
            case "lenient" -> MergeLevel.LENIENT;
            case "spawn" -> MergeLevel.SPAWN;
            default -> {
                LOGGER.warn("Invalid merge level \"{}\" for level {}, falling back to \"lenient\"",
                    rawMerge, level.dimension().identifier());
                yield MergeLevel.LENIENT;
            }
        };
        cannons.parsedMergeLevel.setValue(parsedMerge);

        final String worldName = level.dimension().identifier().toString();
        cannons.explosion.parsedDurableMaterials.setValue(
            DurableMaterialParser.parse(cannons.explosion.durableMaterials, LOGGER, worldName)
        );
        cannons.explosion.parsedExpirationSeconds.setValue(
            DurableMaterialParser.expirationSeconds(cannons.explosion.durableMaterialsExpiration, LOGGER, worldName)
        );
        cannons.explosion.parsedProtectBlocksAboveY.setValue(
            DurableMaterialParser.protectAboveY(cannons.explosion.protectBlocksAboveY, LOGGER, worldName)
        );
        cannons.explosion.parsedProtectionBlocks.setValue(
            ProtectionBlocks.parse(cannons.explosion.protectionBlocks, LOGGER, worldName)
        );
        cannons.restrictions.parsedLeftShootingThreshold.setValue(
            parseDisabledInt(cannons.restrictions.leftShootingThreshold, "left-shooting-threshold", worldName)
        );
        cannons.restrictions.parsedMaxAdjustDistance.setValue(
            parseDisabledInt(cannons.restrictions.maxAdjustDistance, "max-adjust-distance", worldName)
        );
        cannons.restrictions.parsedInstantBlockFallingLimit.setValue(
            parseDisabledInt(cannons.restrictions.instantBlockFallingLimit, "instant-block-falling-limit", worldName)
        );
        mechanics.parsedHeightLimit.setValue(
            parseHeightLimit(mechanics.heightLimit, worldName)
        );
        redstone.parsedImplementation.setValue(parseRedstoneImplementation(redstone.implementation, level, worldName));
        entities.parsedChunkTravelLimit.setValue(
            EntityTravelLimits.parseChunkLimits(entities.chunkTravelLimit, LOGGER, worldName)
        );
        BrainActivation.parseBlacklist(entities.brainActivation, LOGGER, worldName);
        entities.items.blastResistant.parse(LOGGER, worldName);
        entities.items.explosionItemDrops.parse(LOGGER, worldName);
        players.combat.parse(LOGGER, worldName);
        players.knockback.parse(LOGGER, worldName);
        KeepLoadedChunks.sync(level);
    }

    private static int parseDisabledInt(final String raw, final String option, final String worldName) {
        final String trimmed = raw.trim().toLowerCase(Locale.ENGLISH);
        if (trimmed.isEmpty() || trimmed.equals("disabled") || trimmed.equals("off") || trimmed.equals("none") || trimmed.equals("-1")) {
            return -1;
        }

        try {
            return Integer.parseInt(trimmed);
        } catch (final NumberFormatException ignored) {
            LOGGER.warn("Invalid {} \"{}\" for level {}, falling back to disabled", option, raw, worldName);
            return -1;
        }
    }

    private static int parseHeightLimit(final String raw, final String worldName) {
        final int parsed = parseDisabledInt(raw, "height-limit", worldName);
        return parsed == -1 ? Integer.MIN_VALUE : parsed;
    }

    private static double parseDisabledDouble(final String raw, final String option, final String worldName) {
        final String trimmed = raw.trim().toLowerCase(Locale.ENGLISH);
        if (trimmed.isEmpty() || trimmed.equals("disabled") || trimmed.equals("off") || trimmed.equals("none")) {
            return -1.0;
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (final NumberFormatException ignored) {
            LOGGER.warn("Invalid {} \"{}\" for level {}, falling back to disabled", option, raw, worldName);
            return -1.0;
        }
    }

    private static Double parseOptionalDouble(final String raw, final String option, final String worldName) {
        final String trimmed = raw.trim().toLowerCase(Locale.ENGLISH);
        if (trimmed.isEmpty() || trimmed.equals("default") || trimmed.equals("vanilla")) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (final NumberFormatException ignored) {
            LOGGER.warn("Invalid {} \"{}\" for level {}, falling back to default", option, raw, worldName);
            return Double.NaN;
        }
    }

    private static int parseDefaultInt(final String raw, final String option, final String worldName, final int fallback) {
        final String trimmed = raw.trim().toLowerCase(Locale.ENGLISH);
        if (trimmed.isEmpty() || trimmed.equals("default") || trimmed.equals("vanilla")) {
            return fallback;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (final NumberFormatException ignored) {
            LOGGER.warn("Invalid {} \"{}\" for level {}, falling back to {}", option, raw, worldName, fallback);
            return fallback;
        }
    }

    private static Map<Item, Double> parseItemDamageOverride(
        final Map<String, Double> raw,
        final Logger logger,
        final String worldName
    ) {
        final Map<Item, Double> parsed = new LinkedHashMap<>();
        for (final Map.Entry<String, Double> entry : raw.entrySet()) {
            for (final Item item : ItemIds.parse(List.of(entry.getKey()), logger, worldName)) {
                parsed.put(item, entry.getValue());
            }
        }
        return parsed;
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
        option("mechanics").docs(
            "Cannon mechanics emulation, ported from Sakura.",
            "All options in this section are region-thread safe and evaluated per-world."
        );
    }

    public Mechanics mechanics = new Mechanics();
    public static class Mechanics extends Part {
        {
            option("mechanicsTarget")
                .docs(
                    "The Minecraft version and server software to emulate cannon mechanics for.",
                    "Format: <version>[+<server-type>[.<properties>]], e.g. \"latest\", \"legacy\", \"1.8.2+spigot\".",
                    "\"legacy\" targets 1.8.2 mechanics, \"latest\" uses the mechanics of the running version."
                ).greedyString();
            option("heightLimit")
                .docs(
                    "Discard TNT and falling blocks above this Y level.",
                    "Use \"disabled\" to follow Paper's TNT and falling-block height nerfs instead."
                )
                .word();
        }

        public String mechanicsTarget = "latest";
        public String heightLimit = "disabled";

        public final CanonicalReference<MinecraftMechanicsTarget> parsedTarget = new CanonicalReference<>();
        public final CanonicalReference<Integer> parsedHeightLimit = new CanonicalReference<>();

        public int heightLimitOr(final int paperFallback) {
            final Integer parsed = this.parsedHeightLimit.valueSafe();
            if (parsed == null || parsed == Integer.MIN_VALUE) {
                return paperFallback;
            }
            return parsed;
        }
    }

    {
        option("redstone").docs("Redstone wire algorithm and optional vanilla/eigencraft network cache.");
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
            option("cache")
                .docs(
                    "Replay vanilla and eigencraft wire updates when the same network fires again.",
                    "Ignored for panda wire and alternate-current."
                );
        }

        public String implementation = "paper";
        public boolean cache = true;
        public final CanonicalReference<WorldConfiguration.Misc.RedstoneImplementation> parsedImplementation = new CanonicalReference<>();
    }

    {
        option("cannons").docs("Cannon-specific gameplay options.");
    }

    public Cannons cannons = new Cannons();
    public static class Cannons extends Part {
        {
            option("mergeLevel")
                .docs(
                    "How similar cannon entities (TNT and falling blocks) are merged to skip duplicate physics.",
                    "none: never merge, strict: only merge with the last entity that ticked, lenient: merge entities in the same block,",
                    "spawn: also remember spawn positions so later waves can merge immediately."
                ).word();
        }

        public String mergeLevel = "lenient";

        public final CanonicalReference<MergeLevel> parsedMergeLevel = new CanonicalReference<>();

        {
            option("loadChunks")
                .docs(
                    "Load and keep chunks entity-ticking while TNT and falling blocks move through them.",
                    "Off by default because it can prevent chunks from unloading."
                );
        }

        public boolean loadChunks = false;

        {
            option("collideWithNonFullBlocks")
                .docs(
                    "Treat thin blocks such as fences, honey, and ladders as full cubes when TNT or falling sand is moving faster than 64 blocks per tick.",
                    "Off by default."
                );
        }

        public boolean collideWithNonFullBlocks = false;

        {
            option("crossRegions")
                .docs(
                    "Let TNT and falling blocks continue into neighbouring Folia regions instead of bouncing off the region edge.",
                    "The destination region takes ownership through Canvas entity transfer. Same-world only."
                );
        }

        public boolean crossRegions = true;

        {
            option("tntAndSandAffectedByBubbleColumns")
                .docs("Whether bubble columns push primed TNT and falling blocks.");
        }

        public boolean tntAndSandAffectedByBubbleColumns = true;

        {
            option("restrictions").docs("Limits that stop cannons from shooting sideways, adjusting too far, or stacking instant falling blocks.");
        }

        public Restrictions restrictions = new Restrictions();
        public static class Restrictions extends Part {
            {
                option("leftShootingThreshold")
                    .docs(
                        "How far falling sand can travel before it can no longer change horizontal direction.",
                        "Use \"disabled\" to turn this off. Same-world only."
                    );
                option("maxAdjustDistance")
                    .docs(
                        "How far a cannon entity can adjust sideways during collision.",
                        "Use \"disabled\" to turn this off."
                    );
                option("instantBlockFallingLimit")
                    .docs(
                        "How many falling blocks in a column can fall in the same tick before the rest of the stack is delayed.",
                        "Use \"disabled\" to turn this off."
                    );
            }

            public String leftShootingThreshold = "disabled";
            public String maxAdjustDistance = "disabled";
            public String instantBlockFallingLimit = "disabled";
            public final CanonicalReference<Integer> parsedLeftShootingThreshold = new CanonicalReference<>();
            public final CanonicalReference<Integer> parsedMaxAdjustDistance = new CanonicalReference<>();
            public final CanonicalReference<Integer> parsedInstantBlockFallingLimit = new CanonicalReference<>();
        }

        {
            option("tnt").docs("Primed TNT behaviour, ported from Sakura.");
        }

        public Tnt tnt = new Tnt();
        public static class Tnt extends Part {
            {
                option("spread")
                    .docs(
                        "Controls the random velocity applied to TNT when it is primed.",
                        "all: vanilla behaviour, y: removes the horizontal spread, none: removes all spread.",
                        "Note: legacy mechanics (mechanics.mechanics-target = legacy) always remove the horizontal spread."
                    ).word();
                option("flowsInWater")
                    .docs(
                        "Whether primed TNT is pushed by fluids.",
                        "Legacy (1.8) mechanics always disable fluid pushing."
                    );
                option("removeMaxTntPerTick")
                    .docs(
                        "Removes Spigot's max-tnt-ticks-per-tick limit.",
                        "The limit breaks large cannons by silently skipping TNT ticks."
                    );
                option("forcePositionUpdates")
                    .docs(
                        "Force tracker position packets for TNT that is touching water and not pushed by fluid.",
                        "Some clients otherwise keep a ghost TNT in the barrel."
                    );
            }

            public String spread = "all";
            public boolean flowsInWater = true;
            public boolean removeMaxTntPerTick = true;
            public boolean forcePositionUpdates = false;

            public final CanonicalReference<TntSpread> parsedSpread = new CanonicalReference<>();
        }

        {
            option("sand").docs("Falling block (sand) behaviour, ported from Sakura.");
        }

        public Sand sand = new Sand();
        public static class Sand extends Part {
            {
                option("dropItems").docs("Whether falling blocks drop as items when they break or fail to turn back into blocks.");
                option("concreteSolidifyInWater").docs("Whether concrete powder solidifies when moving into water.");
                option("despawnInsideMovingPistons").docs("Allows falling blocks stuck inside moving pistons to despawn after 600 ticks, even while on the ground.");
                option("canStackAtWorldHeight").docs("Whether falling blocks can turn back into blocks one block below the build limit.");
                option("canStackAgainstBorder").docs("Whether falling blocks can turn back into blocks directly against the world border.");
                option("replaceBlocksWithoutCollision").docs("Allows falling blocks to replace blocks without a collision shape, e.g. torches, when landing on the same block type.");
                option("floatingPointFix").docs(
                    "Mitigates MC-261789 - falling blocks breaking when landing on a block below y 0.",
                    "This fix allows some tech that uses \"indirect clipping\" to still function.",
                    "Always enabled when emulating mechanics before 1.17."
                );
                option("heightParity").docs(
                    "Uses the entity position instead of the eye position for height-based checks.",
                    "Always disabled when emulating legacy (1.8) mechanics."
                );
                option("forcePositionUpdates")
                    .docs(
                        "Force tracker position packets for falling blocks that are touching water and not pushed by fluid.",
                        "Some clients otherwise keep a ghost sand in the barrel after TNT launches it."
                    );
            }

            public boolean dropItems = true;
            public boolean concreteSolidifyInWater = true;
            public boolean despawnInsideMovingPistons = false;
            public boolean canStackAtWorldHeight = true;
            public boolean canStackAgainstBorder = true;
            public boolean replaceBlocksWithoutCollision = false;
            public boolean floatingPointFix = false;
            public boolean heightParity = false;
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
            option("explosion").docs("TNT explosion batching and raycast shortcuts, ported from Sakura.");
        }

        public Explosion explosion = new Explosion();
        public static class Explosion extends Part {
            {
                option("optimiseProtectedRegions")
                    .docs(
                        "Skip block searches when a protection plugin would cancel the explosion.",
                        "Probes plugins with a dummy explode event; leave off unless you know you need it."
                    );
                option("avoidRedundantBlockSearches")
                    .docs("Skip later block searches once a stacked TNT explosion has already swung through every axis without hitting anything.");
                option("reuseBlockCacheAcrossExplosions")
                    .docs(
                        "Reuse Paper's explosion block cache across stacked TNT explosions.",
                        "Incompatible with plugins that change blocks during the explosion event."
                    );
                option("batchExplosions")
                    .docs("Batch stacked TNT explosions so one explosion can consume following TNT with the same fuse and state.");
                option("reduceExposureRaycasts")
                    .docs("Skip some exposure rays for block-sized cannon entities when the first rays agree.");
                option("brokenPaperExplosionBehaviour")
                    .docs(
                        "Uses Paper's optimize-explosions density cache.",
                        "That option hurts performance and breaks cannons; leave this off unless you are matching a specific old server.",
                        "Also turns on automatically for legacy mechanics when Paper's optimize-explosions is enabled."
                    );
                option("durableMaterials")
                    .docs(
                        "Blocks that take several explosions to break, like regen walls.",
                        "Keys are block ids. Negative durability only changes blast resistance."
                    );
                option("durableMaterialsExpiration")
                    .docs(
                        "How long unused durable-block hit counts are remembered. Examples: 30s, 1m, 1h.",
                        "Changing this at runtime resets remembered hit counts."
                    )
                    .word();
                option("destroyWaterloggedBlocks")
                    .docs("Treat waterlogged blocks as having no blast resistance so explosions can clear them.");
                option("explodeLava")
                    .docs("Let explosions destroy lava. Lava is replaced without neighbour updates to avoid lag.");
                option("breakBlocksWhenOutsideTheWorldBorder")
                    .docs("When false, explosions whose centre is outside the world border do not break blocks.");
                option("protectBlocksAboveY")
                    .docs(
                        "Blocks at or above this Y level are treated as barriers for explosions.",
                        "Use \"disabled\" to turn this off. Useful for nether-roof protection when bedrock is durable."
                    )
                    .word();
                option("protectionBlocks")
                    .docs(
                        "Block ids that cancel explosion block damage when found in the same-world column below the blast.",
                        "This is the native form of TNT protection-block plugins. Empty turns the search off."
                    );
                option("protectScaffoldingFromCreepers")
                    .docs("Treat scaffolding as a barrier when a creeper explodes, so creeper grief cannot break it.");
                option("consistentRadius")
                    .docs("Use a fixed explosion ray power instead of vanilla's random 0.7–1.3 multiplier.");
                option("explosionsHurtPlayers")
                    .docs("Whether explosions can damage players.");
                option("explosionsDropItems")
                    .docs("Whether blocks broken by explosions drop items.");
            }

            public boolean optimiseProtectedRegions = false;
            public boolean avoidRedundantBlockSearches = false;
            public boolean reuseBlockCacheAcrossExplosions = false;
            public boolean batchExplosions = true;
            public boolean reduceExposureRaycasts = false;
            public boolean brokenPaperExplosionBehaviour = false;
            public Map<String, DurableMaterialSpec> durableMaterials = defaultDurableMaterials();

            private static Map<String, DurableMaterialSpec> defaultDurableMaterials() {
                final Map<String, DurableMaterialSpec> materials = new LinkedHashMap<>();
                materials.put("minecraft:obsidian", new DurableMaterialSpec(4, Blocks.COBBLESTONE.getExplosionResistance(), true));
                materials.put("minecraft:anvil", new DurableMaterialSpec(3, Blocks.END_STONE.getExplosionResistance(), true));
                materials.put("minecraft:chipped_anvil", new DurableMaterialSpec(3, Blocks.END_STONE.getExplosionResistance(), true));
                materials.put("minecraft:damaged_anvil", new DurableMaterialSpec(3, Blocks.END_STONE.getExplosionResistance(), true));
                return materials;
            }

            public String durableMaterialsExpiration = "1m";
            public boolean destroyWaterloggedBlocks = false;
            public boolean explodeLava = false;
            public boolean breakBlocksWhenOutsideTheWorldBorder = true;
            public String protectBlocksAboveY = "disabled";
            public List<String> protectionBlocks = new ArrayList<>();
            public boolean protectScaffoldingFromCreepers = false;
            public boolean consistentRadius = false;
            public boolean explosionsHurtPlayers = true;
            public boolean explosionsDropItems = true;

            public final CanonicalReference<Map<Block, DurableMaterial>> parsedDurableMaterials = new CanonicalReference<>();
            public final CanonicalReference<Long> parsedExpirationSeconds = new CanonicalReference<>();
            public final CanonicalReference<Integer> parsedProtectBlocksAboveY = new CanonicalReference<>();
            public final CanonicalReference<Set<Block>> parsedProtectionBlocks = new CanonicalReference<>();

            public boolean protectsFromExplosions(final int y) {
                final Integer protectAbove = this.parsedProtectBlocksAboveY.valueSafe();
                return protectAbove != null && protectAbove != Integer.MIN_VALUE && y >= protectAbove;
            }

            public Set<Block> protectionBlocks() {
                final Set<Block> parsed = this.parsedProtectionBlocks.valueSafe();
                return parsed != null ? parsed : Set.of();
            }

            public boolean useBrokenPaperExplosionBehaviour(final Level level) {
                return (level.paperConfig().environment.optimizeExplosions && level.mechanicsTarget().isLegacy())
                    || this.brokenPaperExplosionBehaviour;
            }
        }
    }

    {
        option("players").docs("Player collision, fishing rods, spawn eggs, and cobwebs.");
    }

    public Players players = new Players();
    public static class Players extends Part {
        {
            option("posesShrinkCollisionBox")
                .docs("When false, swimming, gliding, and riptide keep the standing hitbox so players cannot squeeze into 1-block holes.");
            option("fishingHooksPullEntities")
                .docs("When false, fishing hooks only pull items, not mobs or players.");
            option("fishingHooksApplyKnockback")
                .docs("Deal 0 damage (still applying knockback) when a fishing hook hits an entity.");
            option("preventPlacingSpawnEggsInsideBlocks")
                .docs("Reject spawn-egg placement when the mob's spawn box would intersect a block.");
            option("collideWithCobwebs")
                .docs("Cobwebs under a full block have a solid top for players, so they can walk on them.");
            option("combat").docs("Legacy combat, damage caps, lunge cooldown, and attack-damage overrides.");
            option("knockback").docs("Knockback strength, sprint extra knockback, and shield hits.");
        }

        public boolean posesShrinkCollisionBox = true;
        public boolean fishingHooksPullEntities = true;
        public boolean fishingHooksApplyKnockback = false;
        public boolean preventPlacingSpawnEggsInsideBlocks = false;
        public boolean collideWithCobwebs = false;
        public Combat combat = new Combat();
        public Knockback knockback = new Knockback();

        public static class Combat extends Part {
            {
                option("legacyCombatMechanics")
                    .docs("1.8-style attack speed, sweep rules, sharpness, and armour.");
                option("allowSweepAttacks")
                    .docs("When false, sweeping edge never hits extra targets.");
                option("shieldDamageReduction")
                    .docs("Halve damage from the front when blocking with a shield that has no BLOCKS_ATTACKS component.");
                option("oldPotionEffects")
                    .docs("Strength and weakness use the old attack-damage formulas.");
                option("oldEnchantedGoldenApple")
                    .docs("Notch apples apply the 1.8 effect list.");
                option("oldSoundsAndParticleEffects")
                    .docs("Skip modern combat sounds and damage-indicator particles.");
                option("fastHealthRegen")
                    .docs("Saturated players heal every tick while at full hunger. Vanilla.");
                option("lungeCooldown")
                    .docs(
                        "Lunge cooldown in milliseconds.",
                        "\"disabled\" turns lunge off. 0 is vanilla with no extra delay."
                    )
                    .word();
                option("maxDamage")
                    .docs("Cap incoming player damage per hit. \"disabled\" leaves vanilla uncapped.")
                    .word();
                option("maxArmourDamage")
                    .docs("Cap armour durability lost per hit. \"default\" is vanilla.")
                    .word();
                option("itemAttackDamageOverride")
                    .docs("Item id to attack damage. Keys are item ids, e.g. minecraft:netherite_sword.");
            }

            public boolean legacyCombatMechanics = false;
            public boolean allowSweepAttacks = true;
            public boolean shieldDamageReduction = false;
            public boolean oldPotionEffects = false;
            public boolean oldEnchantedGoldenApple = false;
            public boolean oldSoundsAndParticleEffects = false;
            public boolean fastHealthRegen = true;
            public String lungeCooldown = "0";
            public String maxDamage = "disabled";
            public String maxArmourDamage = "default";
            public Map<String, Double> itemAttackDamageOverride = new LinkedHashMap<>();
            public final CanonicalReference<Integer> parsedLungeCooldown = new CanonicalReference<>();
            public final CanonicalReference<Double> parsedMaxDamage = new CanonicalReference<>();
            public final CanonicalReference<Integer> parsedMaxArmourDamage = new CanonicalReference<>();
            public final CanonicalReference<Map<Item, Double>> parsedItemAttackDamageOverride = new CanonicalReference<>();

            public void parse(final Logger logger, final String worldName) {
                int cooldown = parseDisabledInt(this.lungeCooldown, "lunge-cooldown", worldName);
                if (cooldown >= 0 && this.legacyCombatMechanics) {
                    cooldown = Math.max(650, cooldown);
                }
                this.parsedLungeCooldown.setValue(cooldown);
                this.parsedMaxDamage.setValue(parseDisabledDouble(this.maxDamage, "max-damage", worldName));
                this.parsedMaxArmourDamage.setValue(parseDefaultInt(this.maxArmourDamage, "max-armour-damage", worldName, -1));
                this.parsedItemAttackDamageOverride.setValue(parseItemDamageOverride(this.itemAttackDamageOverride, logger, worldName));
            }

            public int lungeCooldownMillis() {
                final Integer parsed = this.parsedLungeCooldown.valueSafe();
                return parsed == null ? 0 : parsed;
            }

            public double maxDamageOrDisabled() {
                final Double parsed = this.parsedMaxDamage.valueSafe();
                return parsed == null ? -1.0 : parsed;
            }

            public int maxArmourDamageOrDisabled() {
                final Integer parsed = this.parsedMaxArmourDamage.valueSafe();
                return parsed == null ? -1 : parsed;
            }

            public Double attackDamageFor(final Item item) {
                final Map<Item, Double> parsed = this.parsedItemAttackDamageOverride.valueSafe();
                return parsed == null ? null : parsed.get(item);
            }
        }

        public static class Knockback extends Part {
            {
                option("knockbackVertical")
                    .docs("Vertical knockback added on hit. \"default\" uses vanilla (half current Y plus knockback power).")
                    .word();
                option("knockbackVerticalLimit")
                    .docs("Cap on vertical knockback.");
                option("verticalKnockbackRequireGround")
                    .docs("Only apply the vertical knockback cap while on the ground.");
                option("baseKnockback")
                    .docs("Horizontal knockback from a regular hit. Vanilla is 0.4.");
                option("sweepingEdgeKnockback")
                    .docs("Knockback from sweeping edge. Vanilla is 0.4.");
                option("knockbackResistanceModifier")
                    .docs("Multiplies the knockback resistance attribute.");
                option("shieldHitKnockback")
                    .docs("Knockback dealt to someone who blocked with a shield.");
                option("sprinting").docs("Extra knockback from sprint hits.");
            }

            public String knockbackVertical = "default";
            public double knockbackVerticalLimit = 0.4;
            public boolean verticalKnockbackRequireGround = true;
            public double baseKnockback = 0.4;
            public double sweepingEdgeKnockback = 0.4;
            public double knockbackResistanceModifier = 1.0;
            public double shieldHitKnockback = 0.5;
            public Sprinting sprinting = new Sprinting();
            public final CanonicalReference<Double> parsedKnockbackVertical = new CanonicalReference<>();

            public static class Sprinting extends Part {
                {
                    option("requireFullAttack")
                        .docs("Sprint knockback only applies on a full-strength attack.");
                    option("extraKnockback")
                        .docs("Extra knockback from a sprint hit. Vanilla is 0.5.");
                    option("knockbackDelay")
                        .docs("Milliseconds between extra sprint knockback hits. \"default\" is 0.")
                        .word();
                }

                public boolean requireFullAttack = true;
                public double extraKnockback = 0.5;
                public String knockbackDelay = "default";
                public final CanonicalReference<Integer> parsedKnockbackDelay = new CanonicalReference<>();
            }

            public void parse(final Logger logger, final String worldName) {
                this.parsedKnockbackVertical.setValue(parseOptionalDouble(this.knockbackVertical, "knockback-vertical", worldName));
                this.sprinting.parsedKnockbackDelay.setValue(
                    parseDefaultInt(this.sprinting.knockbackDelay, "knockback-delay", worldName, 0)
                );
            }

            public double knockbackVerticalOr(final double power) {
                final Double parsed = this.parsedKnockbackVertical.valueSafe();
                return parsed == null || parsed.isNaN() ? power : parsed;
            }

            public int knockbackDelayMillis() {
                final Integer parsed = this.sprinting.parsedKnockbackDelay.valueSafe();
                return parsed == null ? 0 : parsed;
            }
        }
    }

    {
        option("entities").docs("Per-entity limits and mob, item, potion, and pearl rules.");
    }

    public Entities entities = new Entities();
    public static class Entities extends Part {
        {
            option("chunkTravelLimit")
                .docs(
                    "How many chunks an entity can travel from its spawn origin before it is discarded.",
                    "Keys are entity ids, e.g. minecraft:ender_pearl. Types not listed have no limit.",
                    "Same-world only."
                );
            option("insertItemsIntoHoppersOnDeath")
                .docs(
                    "When a mob dies standing on a hopper, try to put its drops into that hopper first.",
                    "Only the hopper in the same world and Folia region is used; otherwise drops fall as items."
                );
            option("disableMobAi")
                .docs("Spawn mobs with Paper's unaware flag so they have no AI.");
            option("waterSensitivity")
                .docs("Endermen and other water-sensitive mobs take drown damage in water or rain.");
            option("instantDeathAnimation")
                .docs("Skip the 20-tick death pose for non-player mobs.");
            option("ironGolemsTakeFalldamage")
                .docs("Iron golems take fall damage. Vanilla golems are immune.");
            option("nerfedMobsCanPushEntities")
                .docs("Unaware (nerfed) mobs still push other entities. Off keeps them from shoving players.");
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

        public Map<String, Integer> chunkTravelLimit = defaultChunkTravelLimit();
        public boolean insertItemsIntoHoppersOnDeath = false;
        public boolean disableMobAi = false;
        public boolean waterSensitivity = true;
        public boolean instantDeathAnimation = false;
        public boolean ironGolemsTakeFalldamage = false;
        public boolean nerfedMobsCanPushEntities = false;
        public boolean despawnAfterPickingUpItems = true;
        public boolean trackerOcclusionCulling = true;
        public boolean skipSameTypeProjectileHits = true;
        public BrainActivation brainActivation = new BrainActivation();
        public VerticalActivationRange verticalActivationRange = new VerticalActivationRange();

        private static Map<String, Integer> defaultChunkTravelLimit() {
            final Map<String, Integer> limits = new LinkedHashMap<>();
            limits.put("minecraft:ender_pearl", 8);
            return limits;
        }

        public final CanonicalReference<Map<EntityType<?>, Integer>> parsedChunkTravelLimit = new CanonicalReference<>();

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

        {
            option("items").docs("Item explosion immunity and which items explosions can drop.");
        }

        public Items items = new Items();
        public static class Items extends Part {
            {
                option("blastResistant").docs("Items whose dropped stacks ignore explosions.");
            }

            public ItemFilter blastResistant = new ItemFilter();

            {
                option("explosionItemDrops").docs("Which items explosions are allowed to drop from broken blocks.");
            }

            public ItemFilter explosionItemDrops = new ItemFilter();
        }

        public static class ItemFilter extends Part {
            {
                option("items").docs("Item ids, e.g. minecraft:netherite_ingot.");
                option("whitelist")
                    .docs(
                        "false: the list is immune / skipped.",
                        "true: only the list is affected, everything else is immune / skipped."
                    );
            }

            public List<String> items = new ArrayList<>();
            public boolean whitelist = false;
            public final CanonicalReference<Set<Item>> parsedItems = new CanonicalReference<>();

            public boolean ignoresExplosions(final Item item) {
                return this.listedDiffersFromWhitelist(item);
            }

            public boolean dropsFromExplosions(final Item item) {
                return !this.listedDiffersFromWhitelist(item);
            }

            public void parse(final Logger logger, final String worldName) {
                this.parsedItems.setValue(ItemIds.parse(this.items, logger, worldName));
            }

            private boolean listedDiffersFromWhitelist(final Item item) {
                final Set<Item> parsed = this.parsedItems.valueSafe();
                final boolean listed = parsed != null && parsed.contains(item);
                return listed != this.whitelist;
            }
        }

        {
            option("thrownPotion").docs("Thrown splash and lingering potions.");
        }

        public ThrownPotion thrownPotion = new ThrownPotion();
        public static class ThrownPotion extends Part {
            {
                option("horizontalSpeed").docs("Multiplier for horizontal throw velocity. 1.0 is vanilla.");
                option("verticalSpeed").docs("Multiplier for vertical throw velocity. 1.0 is vanilla.");
                option("allowBreakingInsideEntities")
                    .docs("Potions can collide with their thrower after 5 ticks, so they splash in crowds.");
                option("disableRelativePotionVelocity")
                    .docs("Ignore the thrower's movement when launching potions.");
            }

            public double horizontalSpeed = 1.0;
            public double verticalSpeed = 1.0;
            public boolean allowBreakingInsideEntities = false;
            public boolean disableRelativePotionVelocity = false;
        }

        {
            option("enderPearl").docs("Ender pearl throw, water, and teleport rules. Same-world only.");
        }

        public EnderPearl enderPearl = new EnderPearl();
        public static class EnderPearl extends Part {
            {
                option("useOutlineForCollision")
                    .docs("Pearls hit block outlines instead of collision boxes, so they can clip through thin shapes.");
                option("preventTeleportingInsideBlocks")
                    .docs("If the landing spot is inside a block, try one block down, then discard the pearl.");
                option("slowedDownByWater").docs("Pearls slow down in water.");
                option("randomSpreadWhenThrown").docs("Vanilla inaccuracy when throwing pearls.");
            }

            public boolean useOutlineForCollision = false;
            public boolean preventTeleportingInsideBlocks = false;
            public boolean slowedDownByWater = true;
            public boolean randomSpreadWhenThrown = true;
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

    {
        option("environment").docs("World environment rules that are not cannon-entity specific.");
    }

    public Environment environment = new Environment();
    public static class Environment extends Part {
        {
            option("allowWaterInTheNether")
                .docs("Water buckets and melted ice can place water in the nether instead of evaporating.");
            option("disableFastNetherLava")
                .docs("Nether lava uses overworld flow speed.");
            option("disableFluidsFlowingThroughTheWorldBorder")
                .docs("Fluids do not spread or form cobble/obsidian across the world border.");
            option("blockGeneration").docs("How liquids turn into cobblestone and obsidian.");
        }

        public boolean allowWaterInTheNether = false;
        public boolean disableFastNetherLava = false;
        public boolean disableFluidsFlowingThroughTheWorldBorder = false;

        public BlockGeneration blockGeneration = new BlockGeneration();
        public static class BlockGeneration extends Part {
            {
                option("legacyBlockFormation")
                    .docs(
                        "Force 1.8 cobble and obsidian generation even when mechanics-target is a later version.",
                        "Lava can replace water from the side, and flowing lava only solidifies when it fills enough of the block."
                    );
            }

            public boolean legacyBlockFormation = false;
        }

        {
            option("crops").docs("Cactus and sugar cane growth.");
        }

        public Crops crops = new Crops();
        public static class Crops extends Part {
            {
                option("useRandomChanceToGrow")
                    .docs("Use a single random roll per tick instead of ageing over many ticks.");
                option("minCactusFlowerGrowthHeight")
                    .docs("Cactus must be at least this tall before it can grow a flower. 0 is vanilla.");
            }

            public boolean useRandomChanceToGrow = false;
            public int minCactusFlowerGrowthHeight = 0;
        }

        {
            option("mobSpawner").docs("Spawner rules for this world.");
        }

        public MobSpawner mobSpawner = new MobSpawner();
        public static class MobSpawner extends Part {
            {
                option("checkSpawnConditions")
                    .docs("Honour vanilla spawn rules and obstruction checks. Off lets spawners place mobs anywhere they fit.");
                option("requireNearbyPlayer")
                    .docs("Spawners only tick when a player is in range.");
                option("ignoreEntityLimit")
                    .docs("Ignore the nearby-entity cap. Canvas disable-max-nearby-entities-check also skips it.");
            }

            public boolean checkSpawnConditions = true;
            public boolean requireNearbyPlayer = true;
            public boolean ignoreEntityLimit = false;
        }
    }

    {
        option("technical").docs("Performance shortcuts that can change redstone timing.");
    }

    public Technical technical = new Technical();
    public static class Technical extends Part {
        {
            option("optimiseIdleHopperTicking")
                .docs(
                    "Only tick hoppers when items can move.",
                    "This can break redstone that uses DUDs to detect when a hopper fails to move items."
                );
            option("dispenserRandomItemSelection")
                .docs(
                    "Dispensers pick a random occupied slot, like vanilla.",
                    "When false, dispensers always take the first occupied slot. Droppers stay random."
                );
            option("allowTntDuplication")
                .docs(
                    "Allow TNT and coral-plant piston duplication while Paper's allow-piston-duplication stays off.",
                    "Other piston dupes remain disabled."
                );
            option("redstone").docs("Technical redstone options that are not the wire algorithm.");
        }

        public boolean optimiseIdleHopperTicking = true;
        public boolean dispenserRandomItemSelection = true;
        public boolean allowTntDuplication = false;
        public Redstone redstone = new Redstone();

        public static class Redstone extends Part {
            {
                option("fluidsBreakRedstone")
                    .docs(
                        "Whether flowing fluids can replace redstone components and carpets.",
                        "When false, fluids treat those blocks as solid for placement."
                    );
            }

            public boolean fluidsBreakRedstone = true;
        }
    }
}
