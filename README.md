<p align="center">
  <img src="assets/backspace-icon.png" width="112" alt="Backspace logo">
</p>

<h1 align="center">Backspace</h1>

<p align="center">
  A modern Minecraft Forge 1.12.2 client.<br>
  <strong>dev-3.0-beta</strong>
</p>

<p align="center">
  <img alt="Minecraft 1.12.2" src="https://img.shields.io/badge/Minecraft-1.12.2-5b8c3a?style=flat-square">
  <img alt="Forge 14.23.5.2859" src="https://img.shields.io/badge/Forge-14.23.5.2859-e36d28?style=flat-square">
  <img alt="Java 8" src="https://img.shields.io/badge/Java-8-f89820?style=flat-square">
  <img alt="Release dev-3.0-beta" src="https://img.shields.io/badge/release-dev--3.0--beta-ffd800?style=flat-square">
</p>

---

## Overview

Backspace is a Forge 1.12.2 client with a rounded, GPU-rendered interface,
theme-aware glow treatment, a searchable module browser, HUD editing, and
persistent configuration. The UI is built on OneConfig and NanoVG rather than
Minecraft's legacy rectangle renderer, so text, icons, rounded cards, and
scrolling stay sharp across GUI scales.

## Highlights

- **Modern client UI** — anti-aliased rounded panels, cards, toggles, search,
  category navigation, module inspector, animated glow, and a rebuilt
  Backspace main menu.
- **Organized module system** — Combat, Movement, Player, Render, World,
  Exploit, Misc, Client, and HUD categories with configurable module settings
  and keybinds.
- **HUD and persistence** — drag-based HUD editor, ArrayList support, theme
  preferences, and config state saved between launches.
- **Client tooling** — waypoint and friend commands, inventory helpers,
  visual modules, diagnostics, and build-time harnesses.
- **Clean source release** — Java 8 Gradle wrapper, GitHub Actions build,
  changelog, release notes, bundled UI dependency, and third-party notices.

## Install

1. Install **Minecraft 1.12.2** with **Forge 14.23.5.2859**.
2. Download both release assets:
   - `backspace-dev-3.0-beta.jar`
   - `OneConfig-1.12.2-forge.jar`
3. Put both jars in the same `.minecraft/mods` folder.
4. Launch the game with **Java 8**.
5. Press <kbd>Right Shift</kbd> or run `/backspace` to open Backspace.

> OneConfig is required at runtime. Backspace declares it as a Forge
> dependency and does not bundle its classes into the Backspace jar.

## Build from source

### Requirements

- JDK 8
- Internet access for ForgeGradle's first dependency resolution

### Commands

```powershell
# Windows
.\gradlew.bat build
```

```bash
# Linux / macOS
./gradlew build
```

The finished artifact is written to:

```text
build/libs/backspace-dev-3.0-beta.jar
```

The included Gradle wrapper pins Gradle `4.10.3`, so builds do not depend on a
machine-local Gradle install.

## Controls

| Action | Control |
| --- | --- |
| Open Backspace | <kbd>Right Shift</kbd> or `/backspace` |
| Search modules | Click search or <kbd>Ctrl</kbd> + <kbd>F</kbd> |
| Close Backspace | <kbd>Esc</kbd> |
| Edit HUD | `HUD` from the ClickGUI header |

## Project layout

```text
src/main/java/                 Backspace source
src/main/resources/            fonts, icons, textures, mod metadata
assets/backspace-icon.png      repository and release artwork
libs/OneConfig-1.12.2-forge.jar required UI runtime/build dependency
tools/ncp-lab/                 local diagnostic utilities
.github/workflows/build.yml    CI build
```

## Release notes

See [CHANGELOG.md](CHANGELOG.md) for version changes and
[RELEASE_NOTES.md](RELEASE_NOTES.md) for the current install bundle.

## License and credits

Backspace is released under the [MIT License](LICENSE). Third-party font and
runtime notices are retained in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

