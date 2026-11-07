# Backspace dev-4.2.1

Fix release for Minecraft Forge 1.12.2 (Java 8).

## Changes since dev-4.2

- LavaCast: repeatable lava/water casting, dynamic cast height, bounded incremental flow scans, shorter edge verification, and water collection once connected flow reaches 3–5 blocks below the player (4 by default). The HUD shows completed scan counts and current wait state.
- Keep the integrated world and Backspace modules ticking when Escape or settings screens are open or Minecraft loses focus. Inventory screens still protect inventory actions.
- Compact ClickGUI settings and floating theme editor with color selection and presets.
- Ten additional animated main-menu backgrounds with a saved selector.
- Smoother small text in the menu, ClickGUI, theme editor, and CustomChat; rounded menu selection accents.

## Install

Download `backspace-dev-4.2.1.jar` and the required [OneConfig Forge 1.12.2 JAR from Modrinth](https://modrinth.com/mod/oneconfig/versions?g=1.12.2&l=forge). Put both JARs in the Forge `mods` folder and remove older Backspace JARs. Requires Forge 14.23.5.2859 and Java 8.

The Gradle build and offline LavaCast, background-pause, and CrystalAura harnesses passed. LavaCast timing and in-game menu visuals have not been verified in live gameplay.
