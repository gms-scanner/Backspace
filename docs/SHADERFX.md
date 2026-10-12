# ShaderFX

Open **Render → ShaderFX** and enable it. Hands, players, crystals, and safe
holes have separate toggles. This updates the existing module; saved settings
and imported overlay images remain supported.

## Styles

| Style value | Effect |
| --- | --- |
| 0 | Warm gold |
| 1 | Violet |
| 2 | Ember orange |
| 3 | Animated rainbow gradient |
| 4 | Galaxy: moving procedural nebula and twinkling stars |
| 5 | Aurora: flowing mint, cyan, and violet |
| 6 | Current ClickGUI theme color |

The global default is Rainbow. Each target has a style override; `-1` follows
the global style. Holes default to Theme. The inspector and HUD show the
global style name. Existing profiles retain their previous style value.

For galaxy hands with rainbow crystals, set **Hand style = 4** and
**Crystal style = 3**. No image download or external shader pack is required.

## Appearance

- **Glow intensity** controls effect opacity, from 0–150%.
- **Glow radius** controls the feathered bloom width, from 2–24 physical pixels.
- **Outline width** controls the silhouette edge, from 1–4 physical pixels.
- **Model fill opacity** controls the animated color inside the silhouette.
  Set it to 0 for outlines and exterior bloom only.
- **Animation speed** controls elapsed-time animation. Set 0 for a still effect.
- **Through walls** applies to players, crystals, and holes. When off, the mask
  shader compares each fragment against a copy of the current scene depth.
- **Entity range** and **Models per category** bound model rendering work.
- **Imported crystal overlay** optionally projects the imported image inside
  crystal silhouettes. The normal animated border and bloom remain visible.

## Hole detection

A valid single-block hole has:

1. Air at foot level and one air block above it.
2. A floor made of obsidian or bedrock.
3. All four adjacent blocks at foot level made of obsidian or bedrock.

Mixed obsidian/bedrock holes qualify. **Only all-bedrock holes** restricts all
five surrounding blocks to bedrock. Ender chests, dirt, liquids, double holes,
and openings in a side do not qualify. Detection never places or breaks blocks.

Nearby holes receive a shaded floor and optional short side walls using the
same animated GLSL/bloom pipeline. **Hole side height** controls wall height;
0 draws just the floor. **Hole fill opacity** is independent of model fill.
Scan radius, vertical range, and refresh interval are configurable. The scan
is limited to loaded terrain, keeps the nearest 128 holes, and rechecks each
visible hole before drawing so newly filled holes disappear immediately.
Entity occupancy does not change the structural classification of a hole.

## Rendering implementation

`SilhouetteShader` renders actual Minecraft player/crystal/hand geometry to a
transparent framebuffer, applies horizontal and vertical Gaussian blur passes,
then composites a GLSL 1.20 color effect using the original silhouette and the
blurred mask. This replaces the previous crystal bounding boxes, particle
decoration, and vanilla glowing-player flags. Names and debug boxes are excluded
from mask capture. Hand rendering retains vanilla animation and draws the
normal hand before the colored composite.

Framebuffer dimensions follow window resizing. Shader resources reload with
Minecraft resources. Framebuffers, programs, and imported textures are released
on disable/world unload. Capture scopes restore framebuffer binding, viewport,
matrices, texture units, depth, blend, lighting, and Minecraft's cached state.

The shader files are under `assets/backspace/shaders/effects/`. The renderer
requires framebuffer support and GLSL. Initialization failures appear in the
module status and game log; toggling the module or reloading resources retries
initialization. Compatibility with third-party full-scene shader packs and
custom entity renderers has not been visually verified.

## Build

```powershell
.\gradlew.bat assemble --no-daemon
```

Output: `build/libs/backspace-dev-3.0-beta.jar`. Keep the existing OneConfig
dependency beside it. Java compilation does not validate GLSL execution on a
particular graphics driver; an in-game visual check remains necessary.
