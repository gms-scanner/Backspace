# Backspace dev-4.0.3-beta

This prerelease fixes ClickGUI transparency, long labels, and numeric setting controls.

## Changes

- Set panel transparency from 0% (opaque) to 100% (fully clear). The background alpha is independent of text, border, and control colors. The GUI Editor now previews the change against the live ClickGUI.
- Hover long module names, descriptions, or setting labels to scroll them horizontally after a short delay. Text stays clipped within its row.
- Drag or click sliders to change numeric settings. Values snap to their configured steps, including decimal settings, and save to the client configuration.
- Main menu and both ClickGUI views display the current version.

## Install

1. Download `backspace-dev-4.0.3-beta.jar` from this release and the required [`OneConfig-1.12.2-forge.jar`](https://github.com/gms-scanner/Backspace/releases/download/dev-3.5-beta/OneConfig-1.12.2-forge.jar) dependency.
2. Put both in the Minecraft 1.12.2 Forge `mods` folder.
3. Launch Forge 14.23.5.2859 with Java 8. Press Right Shift or run `/backspace`.
