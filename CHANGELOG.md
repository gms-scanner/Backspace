# Changelog

## Unreleased

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
