# Backspace dev-4.0.2-beta

This beta corrects the module ClickGUI dropdown layout and window transparency.

## Fixes

- Right-click any module to smoothly expand or collapse its settings immediately below its row. Several modules can remain open together, and every later row moves with the animated content.
- Category windows fit their visible rows by default, grow and shrink with settings, and scroll when their content exceeds the screen.
- Remove the full-screen dimmer and duplicate settings background layer. Window opacity now reveals the game consistently; the default is 63% opacity. The GUI Editor still lets you change opacity and theme.

## Install

1. Download `backspace-dev-4.0.2-beta.jar` from this release and the required [`OneConfig-1.12.2-forge.jar`](https://github.com/gms-scanner/Backspace/releases/download/dev-3.5-beta/OneConfig-1.12.2-forge.jar) dependency.
2. Put both in the Minecraft 1.12.2 Forge `mods` folder.
3. Launch Forge 14.23.5.2859 with Java 8. Press Right Shift or run `/backspace`.
