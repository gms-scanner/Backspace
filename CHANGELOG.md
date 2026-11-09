# Changelog

## dev-4.2.5 2026-11-09

- Reworked TreeBurner around grouped tree targets, existing-fire detection, bounded route search, visible ignition positions, and stuck recovery.
- Added scan interval, click delay, and stuck timeout settings.
- Wait for observed fire before marking a tree complete, and stop forcing the forward and jump key states.

## dev-4.2.4 2026-11-07

- Kept the OneConfig GUI and fixed CI builds by fetching the pinned full OneConfig 1.12.2 Forge dependency before compilation.
- Removed the stale workflow artifact upload path for dev-4.0.4-beta.

## Unreleased movement and Surround work — 2026-10-30

- Added Burrow's one-shot obsidian placement attempt with separate server block and position observations.
- Added EntityDesync's local detach, passenger-packet state, and remount request on disable.
- Added AntiVoid's bounded support scan, Elytra recovery requests, and optional low-Y disconnect.
- Added PacketMine with hardness-based start/stop timing, retries, and server block-update confirmation.
- Added MultiTask's bounded item-use action during mining.
- Added NoRotate's server position packet rewrite that retains teleport acknowledgement.
- Added AntiHunger's low-food walking profile.
- Rebuilt Surround target selection around the player's actual footprint, including floor repair and supported side placement. It now prioritizes obsidian and ender chests and restores the selected hotbar item.
- Added experimental BoatFly and EntityFly with motion, packet, glide, mounting, speed, acceleration, packet interval, Y lock, and server vehicle-correction counters.
- Added a movement conflict manager that gives one active controller ownership and disables competing movement modules.
- Added bounded Blink movement queuing and ordered flush on disable.
- Added ReplicationDebug HUD showing local/requested coordinates, inbound position packets, and packet counts.
- Added PacketFly correction cooldown, packet interval, and explicit direct, motion, and pulse modes.
- Documented unverified modes and outstanding modules in [movement status](docs/ANARCHY_MOVEMENT.md).

## dev-4.0.4-beta 2026-10-25

- Fixed the right-click ClickGUI crash: LWJGL 2 requires a 16-element buffer for scissor-state reads in both GUI renderers.


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
