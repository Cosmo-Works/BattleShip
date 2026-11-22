package dev.cosmoworks.battleship;

import dev.cosmoworks.battleship.region.RegionFileFormat;
import io.canvasmc.canvas.configuration.ConfigurationProvider;
import io.canvasmc.canvas.configuration.Part;
import io.canvasmc.canvas.configuration.Resolver;
import io.canvasmc.canvas.configuration.Style;
import io.canvasmc.canvas.configuration.Validator;
import io.canvasmc.canvas.util.CanonicalReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"FieldMayBeFinal", "unused"})
@NullMarked
public class BattleshipServerConfig extends Part {

    private static final Logger LOGGER = LoggerFactory.getLogger("BattleshipServer");
    private static final Path BASE_FILE = Path.of("config/battleship-server.yml").toAbsolutePath().normalize();
    private static final int CHAR_LIM = 90;

    private static BattleshipServerConfig INSTANCE;

    static {
        try {
            reload();
        } catch (final Throwable thrown) {
            LOGGER.error("Couldn't load Battleship server configuration", thrown);
            throw thrown;
        }
    }

    public static void ensureLoaded() {
    }

    public static void reload() {
        ConfigurationProvider.buildSolidConfiguration(
            BASE_FILE,
            BattleshipServerConfig::new,
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
                public void onFinishLoad(final BattleshipServerConfig instance) {
                    Validator.validateObject(instance);
                    instance.linearFormat.resolveFormat();
                    INSTANCE = instance;
                    LOGGER.info("Loaded Battleship server configuration from {}", BASE_FILE);
                }
            },
            Style.create()
                .literal("Server-wide configuration file for Battleship").endLine()
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
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? RegionFileFormat.ANVIL : config.linearFormat.resolvedFormat();
    }

    public static int compressionLevel() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? 1 : config.linearFormat.compressionLevel;
    }

    public static int flushFrequency() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? 10 : config.linearFormat.flushFrequency;
    }

    public static int flushMaxThreads() {
        final BattleshipServerConfig config = INSTANCE;
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

    public static BattleshipServerConfig get() {
        return INSTANCE;
    }

    {
        option("environment").docs("Server-wide environment shortcuts.");
    }

    public Environment environment = new Environment();

    public static class Environment extends Part {

        {
            option("skipUnusedSpawnState")
                .docs(
                    "Do not count every mob for natural spawning in ticks where no player can naturally spawn mobs",
                    "(spawning off, or every player's PlayerNaturallySpawnCreaturesEvent cancelled).",
                    "Only /paper mobcaps and debug reports read the count then; they show nothing for those ticks."
                );
        }

        public boolean skipUnusedSpawnState = true;
    }

    public static boolean skipUnusedSpawnState() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null || config.environment.skipUnusedSpawnState;
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

    {
        option("spawnCrowd").docs(
            "Crowd controls for a busy spawn. Inside the spawn region each player sees only the nearest",
            "players as entities (the tab list and chat are unaffected), and other players' sprint",
            "particles and step sounds are hidden. Plugins can move the center with BattleshipAPI.spawnCrowd()."
        );
    }

    public SpawnCrowd spawnCrowd = new SpawnCrowd();

    public static class SpawnCrowd extends Part {

        {
            option("enabled")
                .docs("Master switch. false turns every spawn crowd control off, including regions set by plugins.");
            option("radius")
                .docs("Horizontal radius in blocks around the world spawn point (follows /setworldspawn).")
                .greaterThanOrEqualTo(0);
            option("capEverywhere")
                .docs("Apply the visible player cap to every player in every world, not only inside the region.");
            option("maxVisiblePlayers")
                .docs(
                    "A player inside the region sees at most this many other players: the nearest ones. A hard",
                    "limit: riding players and the spectated player are picked first but take a slot too.",
                    "0 or less means no cap."
                );
            option("reselectInterval")
                .docs("Ticks between re-picking the nearest players.")
                .between(1, 200);
            option("swapDistance")
                .docs(
                    "A hidden player replaces a shown one only when this many blocks closer, so players at",
                    "the edge of the cap do not flicker."
                )
                .greaterThanOrEqualTo(0);
            option("hideSprintParticles")
                .docs("Other players do not see sprint particles from players inside the region.");
            option("hideStepSounds")
                .docs("Other players do not hear footsteps and swim sounds from players inside the region.");
            option("worlds")
                .docs(
                    "Worlds with a spawn region: world names or dimension keys.",
                    "minecraft:overworld is the main overworld whatever level-name is."
                );
        }

        public boolean enabled = true;
        public int radius = 128;
        public boolean capEverywhere = false;
        public int maxVisiblePlayers = 150;
        public int reselectInterval = 5;
        public double swapDistance = 1.0;
        public boolean hideSprintParticles = true;
        public boolean hideStepSounds = true;
        public List<String> worlds = new ArrayList<>(List.of("minecraft:overworld"));
    }

    public static SpawnCrowd spawnCrowd() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? DEFAULT_SPAWN_CROWD : config.spawnCrowd;
    }

    private static final SpawnCrowd DEFAULT_SPAWN_CROWD = new SpawnCrowd();

    {
        option("chunkPacketCache").docs(
            "Share one built chunk packet between players loading the same chunk shortly after each other",
            "(a crowd joining or teleporting to spawn). Any change to the chunk drops it. Never used for",
            "anti-xray obfuscated chunks or players hiding spawners."
        );
    }

    public ChunkPacketCache chunkPacketCache = new ChunkPacketCache();

    public static class ChunkPacketCache extends Part {

        {
            option("enabled").docs("Cache chunk packets.");
            option("maxAge")
                .docs("Milliseconds a cached packet is reused, at most. Any change to the chunk drops it sooner.")
                .greaterThanOrEqualTo(0);
            option("maxEntries")
                .docs("Most chunk packets kept at once, server-wide; the oldest are dropped first. 0 for no limit.")
                .greaterThanOrEqualTo(0);
            option("cacheFirstSend")
                .docs("Keep a packet from the first time its chunk is sent. Off: only from the second send within max-age.");
            option("softReferences")
                .docs("Hold cached packets by soft references, so the garbage collector can reclaim them under memory pressure.");
            option("asyncSerialization")
                .docs(
                    "Copy a chunk's sections on the region thread and write the packet on a worker thread (DivineMC's",
                    "async chunk sending). The packet waits in the connection queue until written, at most a tick."
                );
            option("asyncSerializationThreads")
                .docs("Worker threads for async chunk serialization.")
                .greaterThanOrEqualTo(1);
            option("statsLogInterval")
                .docs("Log cache hits and misses every this many seconds. 0 turns it off.")
                .greaterThanOrEqualTo(0);
        }

        public boolean enabled = true;
        public int maxAge = 60000;
        public int maxEntries = 0;
        public boolean cacheFirstSend = true;
        public boolean asyncSerialization = false;
        public int asyncSerializationThreads = 2;
        public boolean softReferences = true;
        public int statsLogInterval = 60;
    }

    public static ChunkPacketCache chunkPacketCache() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? DEFAULT_CHUNK_PACKET_CACHE : config.chunkPacketCache;
    }

    private static final ChunkPacketCache DEFAULT_CHUNK_PACKET_CACHE = new ChunkPacketCache();

    {
        option("inventorySync").docs(
            "Cheaper per-tick inventory work for every player. Sends exactly the packets vanilla sends."
        );
    }

    public InventorySync inventorySync = new InventorySync();

    public static class InventorySync extends Part {

        {
            option("skipUnchangedSlots")
                .docs(
                    "Skip comparing inventory slots whose stack, last-seen copy and client copy have not",
                    "changed since the last check, instead of comparing every slot every tick."
                );
            option("fullCheckInterval")
                .docs("Compare every slot on every Nth sync anyway. 0 never does.")
                .greaterThanOrEqualTo(0);
            option("skipNoOpInventoryTicks")
                .docs("Do not call the per-tick inventory hook of items that do nothing in it (most items).");
        }

        public boolean skipUnchangedSlots = true;
        public int fullCheckInterval = 20;
        public boolean skipNoOpInventoryTicks = true;
    }

    public static InventorySync inventorySync() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? DEFAULT_INVENTORY_SYNC : config.inventorySync;
    }

    private static final InventorySync DEFAULT_INVENTORY_SYNC = new InventorySync();

    {
        option("tracker").docs(
            "Entity tracker work per tick. Adapted from ShreddedPaper (GPLv3): see shreddedpaper.yml's",
            "tracker-full-update-frequency and maximum-trackers-per-entity."
        );
    }

    public Tracker tracker = new Tracker();

    public static class Tracker extends Part {

        {
            option("fullUpdateFrequency")
                .docs(
                    "Every how many ticks each entity re-checks the players already tracking it (range,",
                    "visibility). Players that do not track it yet are still checked every tick, so entities",
                    "appear as soon as vanilla shows them; players leaving range stop tracking up to this many",
                    "ticks later. Vanilla is 1."
                )
                .between(1, 100);
            option("maximumTrackersPerEntity")
                .docs(
                    "At most this many players, the nearest ones, track any one entity. 0 or less for no limit.",
                    "Players with the permission battleship.maximumtrackerbypass always count as nearest."
                );
        }

        public int fullUpdateFrequency = 20;
        public int maximumTrackersPerEntity = 500;
    }

    public static Tracker tracker() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? DEFAULT_TRACKER : config.tracker;
    }

    private static final Tracker DEFAULT_TRACKER = new Tracker();

    {
        option("advancements").docs("Advancement criteria work.");
    }

    public Advancements advancements = new Advancements();

    public static class Advancements extends Part {

        {
            option("indexInventoryChangeListeners")
                .docs(
                    "On an inventory change, only test the inventory_changed listeners that can match the",
                    "changed item (most listeners, such as recipe unlocks, wait for one specific item).",
                    "Same results and order as vanilla."
                );
        }

        public boolean indexInventoryChangeListeners = true;
    }

    public static Advancements advancements() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? DEFAULT_ADVANCEMENTS : config.advancements;
    }

    private static final Advancements DEFAULT_ADVANCEMENTS = new Advancements();

    {
        option("ticking").docs(
            "How often some bookkeeping runs. Adapted from ShreddedPaper (GPLv3) options of the same names."
        );
    }

    public Ticking ticking = new Ticking();

    public static class Ticking extends Part {

        {
            option("purgeStaleTicketsFrequency")
                .docs(
                    "Every how many ticks expired chunk tickets are removed. Tickets may outlive their timeout by",
                    "up to this many ticks minus one, never less. Paper is 1."
                )
                .between(1, 100);
            option("entityActivationCheckFrequency")
                .docs(
                    "Every how many ticks entity activation ranges are recomputed. Entities woken by a player stay",
                    "active until the next check; entities entering range wake up to this many ticks later",
                    "(new entities are always active for their first 10 seconds). Paper is 1."
                )
                .between(1, 100);
            option("countKeepDimensionActiveTickets")
                .docs(
                    "Keep one count of tickets that keep a dimension active instead of a count per chunk (only",
                    "\"are there any\" is ever asked). Every player chunk ticket paid for the per-chunk table. Needs a restart."
                );
            option("skipDisabledWaypointWork")
                .docs(
                    "With canvas-server.yml disable-locator-bar-in-all-worlds, do not schedule a waypoint cleanup task on every",
                    "player each time a player teleports, dies or leaves: no waypoint connection was ever made."
                );
            option("batchTicketUpdates")
                .docs("Apply a player's chunk ticket changes under one lock instead of one lock per chunk.");
            option("randomTickSectionMask")
                .docs(
                    "Keep a mask of each chunk's sections that have randomly ticking blocks, so random ticking does not",
                    "visit the others (a memory load per section per chunk per tick). Same sections, same order, same random",
                    "numbers. Blocks placed through the world update it at once; section data written directly by plugins",
                    "(FastAsyncWorldEdit) is picked up by the rebuild every random-tick-mask-interval ticks."
                );
            option("randomTickMaskInterval")
                .docs("Ticks between rebuilding a chunk's random tick section mask from its sections.")
                .greaterThanOrEqualTo(1);
            option("skipUnusedNearbyPlayerMaps")
                .docs(
                    "Do not maintain the two largest nearby-player chunk maps (33 and 10 chunk radius), which",
                    "nothing on the server reads. Makes players joining, leaving and crossing chunks much cheaper."
                );
        }

        public int purgeStaleTicketsFrequency = 20;
        public boolean skipUnusedNearbyPlayerMaps = true;
        public boolean randomTickSectionMask = false;
        public int randomTickMaskInterval = 20;
        public boolean batchTicketUpdates = true;
        public boolean countKeepDimensionActiveTickets = false;
        public boolean skipDisabledWaypointWork = false;
        public int entityActivationCheckFrequency = 20;
    }

    public static Ticking ticking() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? DEFAULT_TICKING : config.ticking;
    }

    private static final Ticking DEFAULT_TICKING = new Ticking();

    {
        option("network").docs("Connection handling.");
    }

    public Network network = new Network();

    public static class Network extends Part {

        {
            option("useLazyExecuteWhenNotFlushing")
                .docs(
                    "Queue packets that are not flushed yet without waking the connection's IO thread; the flush",
                    "at the end of the tick wakes it once. Packet order is unchanged. From ShreddedPaper (GPLv3)."
                );
            option("profileLookupFailureCacheSeconds")
                .docs(
                    "Do not repeat a profile lookup (player heads) that failed or found nothing for this many seconds,",
                    "and pause all lookups for Mojang's Retry-After (at least 30 s) after a 429. 0 turns both off."
                )
                .greaterThanOrEqualTo(0);
            option("fastSendPath")
                .docs(
                    "Send a packet straight to the write batch when nothing is queued before it, instead of through",
                    "the pending queue, and check once per tick whether a player belongs to the sending thread."
                );
            option("batchWrites")
                .docs("Write all packets a thread sent to a connection in one IO thread task instead of one task each. Order unchanged.");
            option("consolidateFlushes")
                .docs(
                    "Merge flushes that arrive outside a read into one flush at the end of the connection's queued IO work",
                    "(one writev instead of one send() per packet). Covers packets flushed one by one by plugins",
                    "(PacketEvents sendPacket) and by threads that do not own the player. Adds no delay beyond the work",
                    "already queued on that IO thread; at most 256 flushes are merged."
                );
            option("suspendFlushingWholeTick")
                .docs(
                    "Hold back flushes to a region's players for the whole region tick, including the packet handling,",
                    "region tasks and entity scheduler tasks that run before the world tick, and flush once at the end.",
                    "Replies to player packets then leave at the end of the tick, as the rest of the tick's packets do."
                );
            option("flushOncePerBatch")
                .docs(
                    "When the IO thread writes a batch of queued packets (batch-writes), write them all and flush once at",
                    "the end instead of flushing after every packet that asked for a flush. Order is unchanged."
                );
            option("statsLogInterval")
                .docs(
                    "Log send-path counters (packets with/without flush, socket writes and flushes, compressed bytes by",
                    "packet type) every this many seconds. 0 turns the log off."
                )
                .greaterThanOrEqualTo(0);
            option("packeteventsBypass")
                .docs(
                    "Let the packet types in packetevents-bypass-types skip plugin handlers between the packet encoder",
                    "and compression (PacketEvents builds an event and calls every listener for every packet). Plugins",
                    "listening to those types through PacketEvents would not see them. Connections with compression only."
                );
            option("packeteventsBypassTypes")
                .docs("Packet types (ids) that skip PacketEvents when packetevents-bypass is on.");
            option("adventureCollapseWithoutConversion")
                .docs(
                    "Answer the network codec's \"is this plain text?\" question for Adventure components (plugin chat,",
                    "action bars, scoreboards) from the Adventure component itself instead of converting it to a vanilla",
                    "component first; the conversion was thrown away. Same answer, same bytes."
                );
            option("shareBroadcastFrames")
                .docs(
                    "Encode a packet the entity tracker sends to several players once per IO thread: the first connection",
                    "keeps the compression encoder's output (per compression threshold and player language) and the others",
                    "write those bytes, skipping encoding, PacketEvents and compression. Only packet types listed in",
                    "packetevents-bypass-types take part. Framing and encryption stay per connection."
                );
            option("shareChunkFrames")
                .docs(
                    "Compress a cached chunk packet once: the first connection keeps the compressed bytes and later",
                    "connections with the same compression settings send them, skipping encoding, compression and",
                    "PacketEvents listeners for that packet. Framing and encryption stay per connection."
                );
            option("oneSwingBroadcastPerTick")
                .docs(
                    "When a player restarts the same arm swing within one tick, send viewers the swing packet once:",
                    "the repeat would restart the same animation from the same point, which is not visible."
                );
        }

        public boolean useLazyExecuteWhenNotFlushing = true;
        public int profileLookupFailureCacheSeconds = 300;
        public boolean fastSendPath = true;
        public boolean batchWrites = true;
        public boolean oneSwingBroadcastPerTick = true;
        public boolean consolidateFlushes = false;
        public boolean suspendFlushingWholeTick = false;
        public boolean shareChunkFrames = false;
        public boolean shareBroadcastFrames = false;
        public boolean adventureCollapseWithoutConversion = false;
        public boolean packeteventsBypass = false;
        public List<String> packeteventsBypassTypes = new ArrayList<>(dev.cosmoworks.battleship.network.PacketEventsBypass.DEFAULT_TYPES);
        public boolean flushOncePerBatch = false;
        public int statsLogInterval = 60;
    }

    public static Network network() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? DEFAULT_NETWORK : config.network;
    }

    private static final Network DEFAULT_NETWORK = new Network();

    {
        option("parallel").docs(
            "Work of one region's tick spread over helper threads while the region thread waits. The helpers act",
            "for the region (Folia's ownership checks pass on them) and never run plugin code. Off by default."
        );
    }

    public Parallel parallel = new Parallel();

    public static class Parallel extends Part {

        {
            option("entityTracker")
                .docs(
                    "Run the entity tracker's visibility scan and packet building on helper threads. Track and",
                    "untrack decisions, their events and pairing packets stay on the region thread, in order."
                );
            option("flushConnections")
                .docs(
                    "Flush every player's connection at the end of the region tick on helper threads. Each connection",
                    "is flushed by one thread; connections already take packets from several threads."
                );
            option("minPlayersPerBatch")
                .docs("Players per helper batch, at least, for per-player work.")
                .greaterThanOrEqualTo(1);
            option("helperThreads")
                .docs("Helper threads shared by all regions. 0 or less: a quarter of the cores, 1 to 4.");
            option("minEntitiesPerBatch")
                .docs("Tracked entities per helper batch, at least. Regions with fewer than twice this stay serial.")
                .greaterThanOrEqualTo(1);
            option("crowdSelection")
                .docs(
                    "With entity-tracker: the spawn crowd cap's per-viewer selection (nearest players, every reselect",
                    "interval) runs on the helper threads, one viewer per task; untracking the players it dropped stays",
                    "on the region thread, in player order."
                );
            option("chunkLoaderQueues")
                .docs(
                    "Rebuild the chunk load/send queues of players whose view moved on the helper threads. View distance",
                    "updates, tickets, chunk sending and unload packets (and their events) stay on the region thread, in",
                    "player order. Used when the region has at least twice min-players-per-batch players."
                );
            option("debugSingleThreaded")
                .docs("Run the parallel phases on the region thread only, in the same order. For telling ordering bugs from threading bugs.");
        }

        public boolean entityTracker = false;
        public boolean flushConnections = false;
        public int minPlayersPerBatch = 32;
        public int helperThreads = 0;
        public int minEntitiesPerBatch = 64;
        public boolean debugSingleThreaded = false;
        public boolean crowdSelection = false;
        public boolean chunkLoaderQueues = false;
    }

    public static Parallel parallel() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? DEFAULT_PARALLEL : config.parallel;
    }

    private static final Parallel DEFAULT_PARALLEL = new Parallel();

    {
        option("movement").docs("Player movement packet handling. Off by default until measured.");
    }

    public Movement movement = new Movement();

    public static class Movement extends Part {

        {
            option("collapseRedundantMovePackets")
                .docs(
                    "Drop a rotation-only or status-only move packet when the player's next queued move packet carries",
                    "everything it does with the same on-ground and collision flags (only the last rotation matters).",
                    "The move-rate bookkeeping is still done for it. Plugins see fewer PlayerMoveEvents for rotation."
                );
            option("skipZeroPlayerMoves")
                .docs("Skip the block lookups of a player's zero-length move when they cannot change anything (idea from Gale/VMP).");
            option("cacheSupportingBlock")
                .docs("Reuse a standing player's supporting block while its box, held item, boots and the chunk's blocks are unchanged.");
            option("skipUnlistenedMoveEvents")
                .docs("Do not build PlayerMoveEvent and its locations when no plugin listens to it.");
        }

        public boolean collapseRedundantMovePackets = false;
        public boolean skipZeroPlayerMoves = false;
        public boolean cacheSupportingBlock = false;
        public boolean skipUnlistenedMoveEvents = false;
    }

    public static Movement movement() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? DEFAULT_MOVEMENT : config.movement;
    }

    private static final Movement DEFAULT_MOVEMENT = new Movement();

    {
        option("io").docs("Disk writes kept off the region threads.");
    }

    public Io io = new Io();

    public static class Io extends Part {

        {
            option("asyncPlayerSaves")
                .docs(
                    "Write playerdata, stats and advancements on a dedicated IO thread. The data is captured on the",
                    "player's thread; only the newest save of each file is written, every write replaces the file",
                    "atomically, a rejoining player reads a save that is still pending, and shutdown and",
                    "save-all flush wait for all writes. Idea from Leaf (GPLv3)."
                );
        }

        public boolean asyncPlayerSaves = true;
    }

    public static Io io() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? DEFAULT_IO : config.io;
    }

    private static final Io DEFAULT_IO = new Io();

    {
        option("debug").docs("Diagnostics. Off by default.");
    }

    public Debug debug = new Debug();

    public static class Debug extends Part {

        {
            option("regionPhaseTiming")
                .docs(
                    "Time the phases of every region tick (packets by type, entity ticking by kind, tracker, chunk ticking,",
                    "chunk loader, scheduler tasks, block events, connection ticks and flush) and log the busiest regions",
                    "every region-phase-log-interval seconds; /sakura regions shows the last report. Costs two",
                    "System.nanoTime() calls per phase, per entity and per packet."
                );
            option("regionPhaseLogInterval")
                .docs("Seconds between region timing reports (at least 5).")
                .greaterThanOrEqualTo(5);
            option("regionPhaseLogRegions")
                .docs("Regions per report, busiest first.")
                .greaterThanOrEqualTo(1);
        }

        public boolean regionPhaseTiming = false;
        public int regionPhaseLogInterval = 60;
        public int regionPhaseLogRegions = 3;
    }

    public static Debug debug() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? DEFAULT_DEBUG : config.debug;
    }

    private static final Debug DEFAULT_DEBUG = new Debug();

    {
        option("join").docs(
            "Login steps that wait for the spawn region. On Folia a joining player waits for the region that owns",
            "its spawn several times, once per region tick each; on a busy spawn region these waits, not CPU work,",
            "make up most of the join time. Off by default."
        );
    }

    public Join join = new Join();

    public static class Join extends Part {

        {
            option("skipLoadedChunkWaits")
                .docs(
                    "When the spawn chunks are already loaded, do not wait for the spawn region to confirm it: add the",
                    "spawn tickets from the login thread and queue the spawn on the region right away. The spawn task",
                    "checks the chunk again on the region and falls back to the normal wait if it is not ticking."
                );
            option("spawnPositionCacheSeconds")
                .docs(
                    "Give a new player (no saved position) a random spawn position found for another new player in the",
                    "last this many seconds instead of searching on the spawn region. 0 turns it off."
                )
                .greaterThanOrEqualTo(0);
            option("spawnPositionCacheSize")
                .docs("Positions kept per world; the pool refills itself in the background, one search at a time.")
                .greaterThanOrEqualTo(1);
        }

        public boolean skipLoadedChunkWaits = false;
        public int spawnPositionCacheSeconds = 0;
        public int spawnPositionCacheSize = 16;
    }

    public static Join join() {
        final BattleshipServerConfig config = INSTANCE;
        return config == null ? DEFAULT_JOIN : config.join;
    }

    private static final Join DEFAULT_JOIN = new Join();
}
