# CrystalAura

CrystalAura places and breaks end crystals through Backspace's existing tick,
controller, packet, settings, and rendering systems. It targets Minecraft
Forge 1.12.2. All world reads and interactions stay on the client thread.

## What changed

The previous implementation shared one cooldown for placement and breaking,
skipped attacked crystals for roughly nine ticks, and reused placement plans
from a slower rendering refresh. Its damage estimate also used an incorrect
explosion multiplier and Resistance reduction.

The replacement plans from current world state each tick. Break and place have
independent schedules. A break can be followed by a placement on another valid
base in the same tick. The original base remains occupied until the server's
entity removal reaches the client. Rendering consumes the plan without running
combat calculations.

Damage follows the 1.12.2 strength-six explosion calculation: radius 12,
integer truncation before difficulty scaling, world exposure, armor/toughness,
Resistance, and explosion protection. Absorption contributes to available
health. Faceplace relaxes enemy minimum damage only; it does not bypass the
self-damage or health-reserve checks.

## Settings

Open **Combat → CrystalAura**. Existing saved fields and keybinds are retained.
**An old profile saved as Strict remains Strict**: choose `Profile = 1` to use
HvH scheduling, and inspect the retained extra action delay.

| Setting | Default | Behavior |
| --- | --- | --- |
| Profile | 1 / HvH | Strict limits pending placements and breaks to one, disallows same-tick break/place, disables wall interactions, and enforces one extra tick of delay. |
| Place / Break crystals | On / On | Independently enable each action. |
| Place / Break range | 4 / 4 blocks | Placement also respects the controller's reach. Breaking also checks the server's visible/occluded entity-origin distance limits. |
| Place / Break walls range | 0 / 0 | Off by default; only available in HvH. Occluded breaking is capped below the server's three-block limit. |
| Target range / Targets to evaluate | 12 / 4 | Evaluate the nearest eligible players, then score their damage opportunities. |
| Place / Break / Extra action delay | 0 / 0 / 0 ticks | Zero permits an action on the next tick. A delay of N inserts N additional ticks. Extra delay applies to both pipelines. |
| Break and place together | On | HvH may do both on one tick when collision and aggregate damage checks allow it. |
| Breaks per tick | 1 | HvH permits 1–4 distinct crystals; cumulative predicted self/friend damage must fit the limits. |
| Break retry | 2 ticks | Prevents repeatedly attacking the same entity every tick. |
| Retry attempts before backoff | 3 | After repeated unconfirmed attempts, back off for at least the acknowledgement window plus ten ticks. |
| Spawn timeout minimum | 4 ticks | Actual wait is the greater of this setting and measured ping rounded up to ticks plus three, capped at 40. |
| Pending placements | 2 | Maximum simultaneous pending attempts in HvH; confirmed spawns free capacity. |
| Minimum crystal age | 0 ticks | Newly observed crystals are eligible immediately on the next module tick. |
| Aim-only break | Off | Restrict breaking to the crosshair's crystal. |
| Rotate | 1 / Silent | 0 off; 1 send action rotation and restore view rotation; 2 rotate the camera. |
| Swap | 2 / Restore | 0 held items only; 1 select hotbar item; 2 restore the previous slot after the action. Offhand crystals are preferred. |
| Anti weakness | On | Select a hotbar item with positive attack damage under current Weakness/Strength effects. Does not move inventory items into the hotbar. |
| Pause while using / mining | On / On | Pause combat during item use and block mining. Opening a screen also pauses actions without disabling the module. |
| Target / Self prediction | 2 / 1 ticks | Bounded linear extrapolation, stopped by swept collision checks or unloaded terrain; teleport-sized motion is discarded. |
| Minimum / Maximum self damage | 4 / 8 health points | Normal target minimum and maximum predicted self damage. Two health points equal one heart. |
| Health reserve | 2 health points | Reject actions that leave this amount or less, even if under the maximum-self setting. |
| Damage / self ratio | 0 percent | 0 disables ratio filtering; 200 requires twice as much enemy damage, except for a lethal target hit. |
| Faceplace / Health / Armor / Minimum | On / 8 / 22% / 2 | Lower enemy minimum for low health, missing armor, or low armor durability. |
| Ignore / Protect friends | On / On | Exclude friends as targets and check nearby friends against the self-damage/reserve limits. |
| Render plan and damage | On | Highlight selected targets/crystals and display estimated damage. |
| Debug logging | Off | Every 40 active planning ticks, log attempted placements/breaks, observed spawn confirmations, pending attempts, candidate count, and planning time. |

## Interaction and cleanup

- Placement requires loaded obsidian/bedrock, two air blocks above it, and an
  empty 1 × 2 × 1 placement column. Faces are ray traced before damage sampling.
- Controller `SUCCESS` means an interaction was attempted. Only a live crystal
  observed at that base clears its pending placement record as confirmed.
- Missing spawns and still-present crystals become retryable after their own
  timers; repeated failures enter backoff. No speculative entity IDs or local
  deletion of unconfirmed crystals are used.
- Slot changes use `PlayerControllerMP.updateController()` to synchronize the
  controller's held-slot cache. Restore mode restores slots in `finally` blocks.
- Death, world changes, disconnection, and disabling clear pending records and
  render plans. The module holds no input keys and changes no global game settings.

## Validation and practical limits

Build and run the deterministic checks with Java 8:

```powershell
.\gradlew.bat build crystalAuraHarness --no-daemon
```

The harness exercises production damage math, selection rules, ping windows,
duplicate suppression, retries/backoff, confirmation, stale-record pruning,
and cancellation. It does not simulate a Minecraft server, network ordering,
inventory reconciliation, or anticheat plugins.

Combat runs on client ticks, normally 20 per second. Prediction is bounded
linear motion, not a full physics/network simulator. Damage uses currently
observed terrain; chained explosions and future block destruction can change
exposure. Same-tick damage budgeting is conservative and cannot guarantee
survival against additional enemy attacks or delayed server updates. Server
rules and latency can reduce accepted action rates. No live HvH benchmark or
performance superiority over another client has been established.

For an in-game comparison, keep ping, server rules, armor, positions, and target
movement identical. Record server-observed spawns/removals and accepted damage,
not just sent packet counts. Include rejected placements, Weakness, offhand and
restore swaps, friend proximity, terrain occlusion, disconnect/reconnect, and
both moving and stationary targets.

## Research references

Public feature documentation informed the settings and pipeline design:

- [LiquidBounce CrystalAura documentation](https://liquidbounce.net/docs/modules/combat/crystalaura)
  describes separate place/destroy controls, prediction, wall ranges, and
  1.12.2 placement clearance.
- [RusherHack AutoCrystal documentation](https://rusherhack.net/features/modules/AutoCrystal.html)
  documents timing, rotation, switching, and damage-related controls.
- [Forge 1.12.2 World API](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.12.2/net/minecraft/world/World.html)
  documents the world collision, density, and ray-trace APIs used here.

Implementation signatures and placement rules were checked against this
project's mapped 1.12.2 Forge classes. This is an original implementation;
other clients' feature descriptions do not establish equivalent behavior or
comparative speed.
