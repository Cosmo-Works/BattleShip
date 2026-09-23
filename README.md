<p align="center"><img src="shrek-icon.png" width="128" alt="Shrek"></p>

## Shrek - a cannoning-focused fork of Canvas

Shrek is a fork of [Canvas](https://github.com/CraftCanvasMC/Canvas) (Folia-based, region-threaded)
that ports the cannoning mechanics and tooling of [Sakura](https://github.com/Samsuik/Sakura)
to a region-threaded server.

Planned features, learned from Sakura:

- Version-targeted cannon mechanics emulation (`mechanics-target`, e.g. `legacy` / `1.8.2+spigot`)
- Cannon entity optimizations (TNT / falling sand), TNT merging, configurable spread
- Explosion-durable blocks (regen walls), waterlogged/lava destruction rules
- Configurable left-shooting, chunk loading on cannon entity movement
- Pluggable redstone implementations (vanilla / eigencraft / alternate-current), ported region-safe
- Cannon testing commands: tick freeze (`/fps`), TNT & sand visibility toggles, mechanics inspection

#### Useful Links

- [Canvas](https://canvasmc.io) &middot; [Canvas Discord](https://canvasmc.io/discord)
- [Sakura](https://github.com/Samsuik/Sakura) (mechanics reference)
- [Weaver](https://github.com/CraftCanvasMC/Weaver) (build tooling)

---

This fork is built with [Weaver](https://github.com/CraftCanvasMC/Weaver) (a paperweight fork),
scaffolded from the [Baguette](https://github.com/CraftCanvasMC/Baguette) template.

Familiarity with paperweight-style forking helps: see [Paper's CONTRIBUTING](https://github.com/PaperMC/Paper/blob/HEAD/CONTRIBUTING.md).

*Weaver adds an additional patch set `base` which is applied before the fork's source and feature
patches. See the [example base patch](shrek-server/minecraft-patches/base/0001-Appreciate-base-patches-more.patch)
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
