<p align="center"><img src="battleship-icon.png" width="128" alt="Battleship"></p>

## Battleship - a cannoning-focused fork of Canvas

Battleship is a fork of [Canvas](https://github.com/CraftCanvasMC/Canvas) (Folia-based, region-threaded)
for Minecraft 26.3. It ports the cannoning mechanics and tooling of
[Sakura](https://github.com/Samsuik/Sakura) to a region-threaded server and adds optimisations for
large player counts.

### Cannoning (ported from Sakura)

- Version-targeted cannon mechanics (`mechanics-target`, e.g. `latest` / `legacy` / `1.8.2+spigot`)
- Cannon entity optimisations (TNT / falling sand), TNT merging, configurable spread
- Explosion-durable blocks (regen walls), waterlogged and lava destruction rules
- Configurable left-shooting, chunk loading on cannon entity movement
- Redstone implementations per world: `vanilla`, `eigencraft`, `alternate-current`
- Knockback and combat options, including knockback sync

### Large player counts

- Spawn crowd view cap: each player sees only the nearest N players in a crowd
- Chunk packet cache and shared packet frames for packets sent to many players
- Flush consolidation and ordered outbound sending on the network threads
- Parallel entity tracking and per-player work inside a region
- Path search caches for mob pathfinding
- Player-simulated chunks for chunk loaders
- Linear region file format, asynchronous player data saves
- Cheaper inventory sync and random ticking

### Configuration and commands

- Sakura's options are in Sakura's own files, with Sakura's option names and defaults:
  `config/sakura-global.yml` (messages, FPS menu, sword/spear/bucket options, spawner defaults),
  `config/sakura-world-defaults.yml` (cannons, mechanics, explosions, combat, knockback, entities,
  environment, technical) and a `sakura-world.yml` in each world folder to override the defaults
- Battleship's own options are in `config/battleship-server.yml` (server-wide) and
  `config/battleship-worlds.yml` (per world, with a `battleship-patch.yml` in each world folder)
- `/sakura` has subcommands for cannon testing and inspection: tick freeze and `fps`, TNT and sand
  visibility toggles, mechanics and config inspection, region and TPS overview. `/sakura reload`
  reloads both Sakura's and Battleship's files

#### Options that moved to Sakura's files

Older versions kept these in Battleship's files. They are not migrated: copy your values over by hand.

| Old path in `battleship-worlds.yml` | New path in `sakura-world-defaults.yml` / `sakura-world.yml` |
|---|---|
| `mechanics.mechanics-target` | `cannons.mechanics.mechanics-target` (`mechanic-version` + `server-type`) |
| `mechanics.height-limit` | `cannons.mechanics.height-limit` |
| `redstone.cache` | `technical.redstone.redstone-cache` |
| `cannons.tnt.spread` | `cannons.mechanics.tnt-spread` |
| `cannons.tnt.flows-in-water` | `cannons.mechanics.tnt-flows-in-water` |
| `cannons.sand.floating-point-fix` | `cannons.mechanics.floating-point-fix` |
| `cannons.sand.height-parity` | `cannons.mechanics.height-parity` |
| `cannons.explosion.broken-paper-explosion-behaviour` | `cannons.mechanics.broken-paper-explosion-behaviour` |
| `players.fishing-hooks-apply-knockback` | `players.knockback.projectiles.fishing-hooks-apply-knockback` |
| `entities.<option>` (see below) | `entity.<option>` |
| every other moved option | same path |

Moved with the same path: `cannons.merge-level`, `cannons.load-chunks`, `cannons.collide-with-non-full-blocks`,
`cannons.tnt-and-sand-affected-by-bubble-columns`, `cannons.restrictions.*`, `cannons.tnt.force-position-updates`,
`cannons.sand.*` except `force-position-updates`, `cannons.explosion.*` except `protection-blocks`, `players.*`,
`environment.*` and `technical.*`. Moved from `entities` to `entity`: `chunk-travel-limit`,
`insert-items-into-hoppers-on-death`, `disable-mob-ai`, `water-sensitivity`, `instant-death-animation`,
`iron-golems-take-falldamage`, `nerfed-mobs-can-push-entities`, `items.*`, `thrown-potion.*`, `ender-pearl.*`.

From `battleship-server.yml` to `sakura-global.yml`, same paths: `messages.fps-setting-change`,
`messages.mechanic-information`, `messages.tps-show-entity-and-chunk-count`, `fps.material`,
`environment.calculate-biome-noise-once-per-chunk-section`, `environment.mob-spawner-defaults.*`, `players.*`.
`messages.durable-block-interaction` is gone (it was never used).

`cannons.sand.despawn-inside-moving-pistons` now defaults to `true` (Sakura's default; it was `false`).

#### Sakura update (up to `0a1a01b`)

- New option `technical.optimise-auto-farms` (default `false`) in `sakura-world-defaults.yml` / `sakura-world.yml`.
  It reduces redstone and piston updates in chunk sections where melon or pumpkin stems grew in the
  last five minutes. It changes the redstone update order there, and pistons that only break a
  block skip the piston extend and retract events
- `technical.redstone.redstone-cache` no longer skips repeated wire updates by itself. That now
  only happens around tracked auto farms, with `technical.optimise-auto-farms` enabled
- Sulfur cubes no longer take `cannons.mechanics.height-parity` when they spawn. Battleship's own
  `cannons.sulfur.height-parity` in `battleship-worlds.yml` still applies

### API

- `BlockPreGrowEvent`, plus the Sakura API (durable materials, visibility, local config)
- Sakura renamed `BlockPreDispenseEvent` to `DispenserPreTickEvent`. The old event is deprecated for
  removal and still fires first; listen to `DispenserPreTickEvent` instead

#### Useful Links

- [Canvas](https://canvasmc.io) &middot; [Canvas Discord](https://canvasmc.io/discord)
- [Sakura](https://github.com/Samsuik/Sakura) (mechanics reference)
- [Weaver](https://github.com/CraftCanvasMC/Weaver) (build tooling)

---

This fork is built with [Weaver](https://github.com/CraftCanvasMC/Weaver) (a paperweight fork),
scaffolded from the [Baguette](https://github.com/CraftCanvasMC/Baguette) template.

Familiarity with paperweight-style forking helps: see [Paper's CONTRIBUTING](https://github.com/PaperMC/Paper/blob/HEAD/CONTRIBUTING.md).

*Weaver adds an additional patch set `base` which is applied before the fork's source and feature
patches. See the [example base patch](battleship-server/minecraft-patches/base/0001-Appreciate-base-patches-more.patch)
and `build.gradle.kts` for details.*

---

### Building

Requirements: Git, JDK 25. On Windows, enable long paths
([docs](https://learn.microsoft.com/en-us/windows/win32/fileio/maximum-file-path-limitation)).

```
./gradlew applyAllPatches       # construct the full source tree
./gradlew createPaperclipJar    # build the runnable jar (build/libs)
./gradlew runDevServer          # run a local dev server
```
