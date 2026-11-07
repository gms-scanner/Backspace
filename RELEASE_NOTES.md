# Backspace dev-4.2.3

Small patch for Minecraft Forge 1.12.2 (Java 8).

## Changes

- Fixed the custom main screen layout at larger Minecraft UI scale settings. The menu now fits smaller scaled screen dimensions instead of letting controls run off-screen.
- Kept hover and click targets aligned with the resized menu.

## Install

Download `backspace-dev-4.2.3.jar` and the required [OneConfig Forge 1.12.2 JAR from Modrinth](https://modrinth.com/mod/oneconfig/versions?g=1.12.2&l=forge). Put both JARs in the Forge `mods` folder and remove older Backspace JARs. Requires Forge 14.23.5.2859 and Java 8.

The Gradle build passed. The UI scale fix has not been verified in live gameplay.
