# Backspace dev-4.0.4-beta

This hotfix resolves the crash when expanding module settings with right-click.

## Fix

- Allocate the 16-element buffer required by LWJGL 2 when reading OpenGL scissor state in the pixel and modern ClickGUI renderers. The expanded settings, transparency, marquee text, and sliders from dev-4.0.3-beta are preserved.

## Install

1. Download `backspace-dev-4.0.4-beta.jar` from this release and the required [OneConfig Forge 1.12.2 JAR from Modrinth](https://modrinth.com/mod/oneconfig/versions?g=1.12.2&l=forge).
2. Put both in the Minecraft 1.12.2 Forge `mods` folder, replacing the previous Backspace JAR.
3. Launch Forge 14.23.5.2859 with Java 8. Press Right Shift or run `/backspace`.
