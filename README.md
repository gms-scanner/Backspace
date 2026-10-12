# Backspace

<p align="center">
  <img src="assets/backspace-icon.png" width="112" alt="Backspace logo">
</p>

<h1 align="center">Backspace</h1>

<p align="center">
  A custom Minecraft Forge 1.12.2 client focused on clean visuals, useful features, and a modern interface.<br>
  <strong>dev-3.0-beta</strong>
</p>

<p align="center">
  <img alt="Minecraft 1.12.2" src="https://img.shields.io/badge/Minecraft-1.12.2-5b8c3a?style=flat-square">
  <img alt="Forge 14.23.5.2859" src="https://img.shields.io/badge/Forge-14.23.5.2859-e36d28?style=flat-square">
  <img alt="Java 8" src="https://img.shields.io/badge/Java-8-f89820?style=flat-square">
  <img alt="Release dev-3.0-beta" src="https://img.shields.io/badge/release-dev--3.0--beta-ffd800?style=flat-square">
</p>

---

## About

Backspace is a Minecraft Forge 1.12.2 client with a custom interface built to make the game easier to navigate and customize.

It features a searchable module browser, rounded panels, animated glow effects, an editable HUD, and settings that stay saved between launches.

The interface is built with OneConfig and NanoVG instead of Minecraft's old rectangle-based renderer. This helps keep text, icons, and other UI elements looking clean at different GUI scales.

## Features

* **Custom GUI**: Rounded panels, smooth scrolling, animated glow effects, toggles, category navigation, and a redesigned main menu.
* **Module browser**: Find modules quickly or browse categories like Combat, Movement, Player, Render, World, Exploit, Misc, Client, and HUD.
* **Module settings**: Customize available options and keybinds directly through the interface.
* **ShaderFX**: Animated rainbow, galaxy, aurora, and theme glows around hands, players, crystals, and safe holes. See [styles and settings](docs/SHADERFX.md).
* **HUD editor**: Move HUD elements around, use the ArrayList, and adjust the client's appearance.
* **Saved settings**: Your configuration and theme preferences are saved between sessions.
* **Commands and tools**: Includes waypoint and friend commands, inventory helpers, visual modules, and diagnostic utilities.
* **CrystalAura**: Independent place/break timing, motion prediction, vanilla damage estimates, offhand support, rotations, and confirmed placement tracking. See [settings, implementation, and validation](docs/CRYSTALAURA.md).
* **Developer tools**: Comes with a Gradle wrapper, GitHub Actions build workflow, changelog, release notes, and third-party notices.

## Installation

1. Install Minecraft **1.12.2** with Forge **14.23.5.2859**.
2. Download both files from the release:

   * `backspace-dev-3.0-beta.jar`
   * `OneConfig-1.12.2-forge.jar`
3. Place both JAR files in your `.minecraft/mods` folder.
4. Launch Minecraft using **Java 8**.
5. Press <kbd>Right Shift</kbd> or type `/backspace` in-game to open the client.

**Note:** OneConfig is required for Backspace to run. It is declared as a Forge dependency and is not bundled into the Backspace JAR.

## Building from source

Want to build Backspace yourself or make changes to the code? You'll need:

* JDK 8
* An internet connection for the initial ForgeGradle dependency download

Open a terminal in the project directory and run the command for your operating system.

**Windows**

```powershell
.\gradlew.bat build
```

**Linux / macOS**

```bash
./gradlew build
```

After the build finishes, the JAR will be located at:

```text
build/libs/backspace-dev-3.0-beta.jar
```

The project includes a Gradle wrapper pinned to version `4.10.3`, so there's no need to install Gradle separately.

Run the CrystalAura damage and retry checks with `./gradlew crystalAuraHarness`
(`.\gradlew.bat crystalAuraHarness` on Windows). The CrystalAura reboot is on
`main`; the existing `dev-3.0-beta` release predates it. Build `main` or download
the artifact from a successful [GitHub Actions build](https://github.com/gms-scanner/Backspace/actions).

## Controls

| Action              | Control                                                      |
| ------------------- | ------------------------------------------------------------ |
| Open Backspace      | <kbd>Right Shift</kbd> or `/backspace`                       |
| Search modules      | Click the search bar or press <kbd>Ctrl</kbd> + <kbd>F</kbd> |
| Close the GUI       | <kbd>Esc</kbd>                                               |
| Open the HUD editor | Select `HUD` in the ClickGUI header                          |

## Project structure

```text
src/main/java/                 Main Java source
src/main/resources/            Fonts, icons, textures, and mod metadata
assets/backspace-icon.png      Project logo
libs/OneConfig-1.12.2-forge.jar OneConfig dependency
tools/ncp-lab/                 Local diagnostic tools
.github/workflows/build.yml    Automated build workflow
```

## Changelog and releases

See [CHANGELOG.md](CHANGELOG.md) for changes between versions.

For details about the current release and its installation files, check [RELEASE_NOTES.md](RELEASE_NOTES.md).

## License and credits

Backspace is released under the [MIT License](LICENSE).

Third-party font credits and runtime dependency notices can be found in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
