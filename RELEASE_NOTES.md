# Backspace dev-4.2.5

TreeBurner pathfinding and multi-tree reliability update for Minecraft Forge 1.12.2.

## Changes

- Groups nearby logs into trees, searches for reachable ignition angles, and moves on after fire is observed.
- Detects nearby existing fire so burning trees are not repeatedly selected.
- Uses bounded path searches, configurable scan and click intervals, and temporary cooldowns for blocked trees.
- Replans when a route is obstructed or movement stops making progress.
- Waits for visible fire after using flint and steel instead of treating a successful click as a completed burn.
- Removes direct forward and jump key-state overrides that could leave movement stuck.

## Install

Download `backspace-dev-4.2.5.jar` and the required [OneConfig Forge 1.12.2 JAR from Modrinth](https://modrinth.com/mod/oneconfig/versions?g=1.12.2&l=forge). Put both JARs in the Forge `mods` folder and remove older Backspace JARs. Requires Forge 14.23.5.2859 and Java 8.

Gradle compilation and existing diagnostic harnesses passed locally. TreeBurner's in-game behavior still needs live verification on the servers where it will be used.
