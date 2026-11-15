# Backspace dev-4.3.0-rc3

- Adds MapSpam under World: scans loaded nearby surfaces, places item frames and inserts filled maps from inventory, with optional Baritone approach pathing.
- Configurable search radius, action delay, Baritone toggle and map ID filter (-1 accepts any filled map).
- Uses existing empty frames and skips occupied frames. Retries time out and blocked targets receive a cooldown. Requires item frames and filled maps in inventory; consumes them normally.
- Includes the rc2 BlockESP full-height loaded-chunk scanning and per-chunk cache improvements.
- Retains the rc1 addon API and authoring tutorial.
- Fixes update installation from LaunchWrapper jar URLs (FileSystemNotFoundException) and recognizes numbered release candidates correctly.

Requires Minecraft Forge 1.12.2, Java 8 and OneConfig. For navigation install Baritone API Forge 1.2.19. Put Backspace in mods and addon JARs in Backspace/addons.

Gradle build passed locally. Live placement, navigation and multiplayer behavior have not been verified.
