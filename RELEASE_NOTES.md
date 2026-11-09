# Backspace dev-4.2.6

TreeBurner Baritone pathfinding fix for Minecraft Forge 1.12.2.

## Changes

- Uses Baritone's custom goal process to travel to trees instead of the bounded local route search, which could leave TreeBurner idle.
- Supports all vanilla log species and modded logs reported by Forge's isWood or logWood Ore Dictionary APIs.
- Groups nearby logs into trees, searches for reachable ignition angles, and moves on after fire is observed.
- Detects nearby existing fire so burning trees are not repeatedly selected.
- Uses bounded path searches, configurable scan and click intervals, and temporary cooldowns for blocked trees.
- Replans when a route is obstructed or movement stops making progress.
- Waits for visible fire after using flint and steel instead of treating a successful click as a completed burn.
- Removes direct forward and jump key-state overrides that could leave movement stuck.

## Install

Download `backspace-dev-4.2.6.jar`, the required [OneConfig Forge 1.12.2 JAR from Modrinth](https://modrinth.com/mod/oneconfig/versions?g=1.12.2&l=forge), and [Baritone API Forge 1.2.19](https://github.com/cabaletta/baritone/releases/tag/v1.2.19). Put all three JARs in the Forge `mods` folder and remove older Backspace JARs. Use Baritone's **api-forge** build, not its standalone build; the standalone JAR obfuscates the integration API. Requires Forge 14.23.5.2859 and Java 8.

Gradle compilation and existing diagnostic harnesses passed locally. TreeBurner's in-game movement still needs live verification.
