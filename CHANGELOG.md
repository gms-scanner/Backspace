# Changelog

## dev-4.0.3-beta 2026-10-25

- Corrected pixel window transparency with a full 0–100% range and live GUI editor preview.
- Added hover marquee text for long module and setting labels, with scissor clipping.
- Replaced numeric controls with draggable step-aware sliders in both module browsers, including decimal values.
- Updated menu and ClickGUI version labels to use the client version constant.


## dev-4.0.2-beta 2026-10-25

- Rebuilt module settings as independently animated inline dropdowns; multiple modules can stay open.
- Category windows now follow their content height and scroll when the content exceeds the screen.
- Removed the screen dimmer and stacked settings fill so window opacity reveals the world consistently.


## dev-4.0.1-beta 2026-10-24

- Moved pixel ClickGUI settings directly below the module opened with right-click.
- Removed pixel ClickGUI text shadows.
- Added a GUI editor for window opacity and Gold, Red, Mint, and Blue themes.


## dev-4.0-beta 2026-10-24

- Reworked module browser with pixel-styled yellow and black category windows.
- Added Inventory HUD, DIESP, PingSpoof, WhisperSpam, BoatPhase, and AutoTotem offhand crystal mode.
- Kept the CrystalAura harness in the release build workflow.

## dev-3.5-beta 2026-10-13

### Added

- BreakESP: server-reported block damage progress with miner labels and stale-record cleanup.
- PopChams: fading six-part player silhouettes captured on totem activation packets.
- LogoutSpots: last observed player positions, removed on rejoin and cleared on world changes.
- PearlTrace: opponent pearl trajectory and landing prediction from live motion, block/entity collision, water drag, and gravity.

### Updated

- Client, main menu, ClickGUI, and build metadata now identify beta 3.5.

## Unreleased

### ShaderFX

- Replaced crystal boxes and vanilla outline flags with model silhouette masks,
  Gaussian bloom, and animated GLSL composition for hands, players, and crystals.
- Added rainbow, procedural galaxy, aurora, and theme colors with independent
  target styles, fill opacity, glow radius, and animation speed.
- Added single-block obsidian/bedrock hole detection and animated floor/side
  highlights, including an all-bedrock filter and configurable scan bounds.
- Added scene-depth masking, framebuffer resize/resource reload handling, and
  scoped OpenGL state restoration. See [ShaderFX settings](docs/SHADERFX.md).

### CrystalAura reboot

- Independent place/break scheduling, per-entity retry pacing, ping-aware spawn
  confirmation windows, and bounded failure backoff.
- Corrected 1.12.2 explosion strength, integer truncation, difficulty, armor,
  toughness, enchantment, and Resistance calculations.
- Fresh per-tick targeting, bounded motion prediction, cached candidate damage,
  lethal-target priority, faceplace, and configurable damage ratios.
- Offhand crystals, synchronized hotbar selection/restoration, silent or camera
  rotations, and weakness-aware weapon selection.
- Full placement-column collision checks, visible-face selection, separate wall
  ranges, and live reach/target checks immediately before interactions.
- Health reserve, friend protection, aggregate same-tick damage limits, pause
  controls, world/death cleanup, and diagnostic counters.
- Added the `crystalAuraHarness` task and CI coverage for damage and retry rules.
  See [CrystalAura settings and validation](docs/CRYSTALAURA.md).

## dev-3.0-beta 2026-10-12

### Added

- Modern rounded Backspace ClickGUI with anti-aliased NanoVG rendering.
- Backspace main menu, HUD editor, configuration persistence, and themed
  visual assets.
- Combat, movement, player, world, render, and HUD module suites for Forge
  1.12.2.
- Diagnostic harnesses for LavaCast flow tracking and NCP passable behavior.

### Refined

- Reworked module browser layout, text measurement, scrolling, cards, and
  inspector controls.
- Rebuilt the Backspace logo mark and release packaging.

### Runtime dependency

- OneConfig for Forge 1.12.2 is required beside Backspace.
