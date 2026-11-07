# Backspace dev-4.2.2

Small patch for Minecraft Forge 1.12.2 (Java 8).

## Changes

- Added a draggable Update window to the ClickGUI. It shows release checking, download progress, errors, and a button to close Minecraft after an update has been staged.
- `/backspace update` now opens the Update window and starts the check.
- Update failures show the actual error type or HTTP status instead of `failed: null`.
- A downloaded update is validated and replaces the installed JAR after Minecraft closes.

## Install

Download `backspace-dev-4.2.2.jar` and the required [OneConfig Forge 1.12.2 JAR from Modrinth](https://modrinth.com/mod/oneconfig/versions?g=1.12.2&l=forge). Put both JARs in the Forge `mods` folder and remove older Backspace JARs. Requires Forge 14.23.5.2859 and Java 8.

The Gradle build and offline LavaCast and background-pause harnesses passed. The updater's full replacement flow has not been verified in live gameplay.
