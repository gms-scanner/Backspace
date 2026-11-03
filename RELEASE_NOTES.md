# Backspace 4.2

Backspace 4.2 adds a searchable ClickGUI and an automatic release updater, alongside the development changes since the last published build.

## Changes

- Draggable ClickGUI search window that collapses to a bottom-right tab. Matching modules pulse red, and category lists scroll around the window.
- GitHub release updater. It checks on startup and stages a newer Backspace JAR for installation after Minecraft closes. Use `/backspace update` to check manually or `/backspace update install` to install an available release.
- Movement, combat, exploration, and utility module updates, including Staircase, ElytraFly, EChestFarm, Flight, Spam, TrailMaker, and module target selection.
- Additional module configuration controls in the ClickGUI.

## Install

1. Download `backspace-dev-4.2.jar` and the required [OneConfig Forge 1.12.2 JAR from Modrinth](https://modrinth.com/mod/oneconfig/versions?g=1.12.2&l=forge).
2. Put both JARs in the Minecraft 1.12.2 Forge `mods` folder. Remove older Backspace JARs.
3. Launch Forge 14.23.5.2859 with Java 8. Press Right Shift or run `/backspace`.

The updater begins working after this version is installed; earlier builds cannot update themselves retroactively. Server-specific movement and combat behavior has not been verified on every server.
