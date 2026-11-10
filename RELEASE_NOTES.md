# Backspace dev-4.3.0-rc1

Addon system development build for Minecraft Forge 1.12.2.

## Changes

- Loads Java addon JARs from `Backspace/addons` at startup. Addons can register modules, category windows, Forge event listeners, and client commands.
- Addon modules use the regular ClickGUI, settings, keybind, and configuration paths. A buildable example, modding tutorial, and API guide are included in the source.
- Includes the recent TreeBurner targeting and ignition fixes under test.
- Uses Baritone's custom goal process to travel to trees instead of the bounded local route search, which could leave TreeBurner idle.
- Supports all vanilla log species and modded logs reported by Forge's isWood or logWood Ore Dictionary APIs.
- Groups nearby logs into trees, searches for reachable ignition angles, and moves on after fire is observed.
- Detects nearby existing fire so burning trees are not repeatedly selected.
- Uses bounded path searches, configurable scan and click intervals, and temporary cooldowns for blocked trees.
- Replans when a route is obstructed or movement stops making progress.
- Waits for visible fire after using flint and steel instead of treating a successful click as a completed burn.
- Removes direct forward and jump key-state overrides that could leave movement stuck.

## Install

Build `backspace-dev-4.3.0-rc1.jar` from this source. Use the required [OneConfig Forge 1.12.2 JAR from Modrinth](https://modrinth.com/mod/oneconfig/versions?g=1.12.2&l=forge) and [Baritone API Forge 1.2.19](https://github.com/cabaletta/baritone/releases/tag/v1.2.19) for TreeBurner. Put addon JARs in `Backspace/addons`, separate from Forge `mods`. Requires Forge 14.23.5.2859 and Java 8.

Gradle build, existing diagnostic harnesses, example addon build, and addon service/registry harness passed locally. Addon loading in live Minecraft and TreeBurner behavior still need gameplay verification.
