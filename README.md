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

- `battleship-server.yml` holds the server-wide switches, `battleship-worlds.yml` the per-world settings
  (mechanics, redstone, cannons, entities, chunks)
- `/sakura` has subcommands for cannon testing and inspection: tick freeze and `fps`, TNT and sand
  visibility toggles, mechanics and config inspection, region and TPS overview, reload

### API

- `BlockPreGrowEvent`, plus the Sakura API (durable materials, visibility, local config)

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
