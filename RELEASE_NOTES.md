# Backspace dev-4.2.4

Build dependency patch for Minecraft Forge 1.12.2 (Java 8).

## Changes

- Kept the existing OneConfig-based screens and behavior.
- Fixed GitHub Actions compilation by downloading the pinned, full OneConfig 1.12.2 Forge JAR and checking its SHA-256 before the Gradle build.
- Removed the outdated workflow artifact upload path. The release JAR is attached to this release instead.

## Install

Download `backspace-dev-4.2.4.jar` and the required [OneConfig Forge 1.12.2 JAR from Modrinth](https://modrinth.com/mod/oneconfig/versions?g=1.12.2&l=forge). Put both JARs in the Forge `mods` folder and remove older Backspace JARs. Requires Forge 14.23.5.2859 and Java 8.

The Gradle build and existing CrystalAura, LavaCast, and background harnesses passed locally. The OneConfig screen has not been verified in live gameplay for this patch.
