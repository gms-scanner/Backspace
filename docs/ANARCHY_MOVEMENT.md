# Backspace 1.12.2 movement status

This development build targets Forge 1.12.2-14.23.5.2859 and Java 8. All movement modes below are **experimental until tested on a named server with a second observing client**. A local position change or missing correction does not prove the server accepted movement.

## Packet mechanics

- Riding movement uses `CPacketVehicleMove`; the server can reply with `SPacketMoveVehicle`. BoatFly and EntityFly retain the requested location separately from the latest correction and pause briefly after a mismatch. They do not suppress vanilla synchronization.
- Player movement uses `CPacketPlayer` variants; teleport corrections arrive as `SPacketPlayerPosLook`, and vanilla sends `CPacketConfirmTeleport`. PacketFly backs off on a server position packet. ReplicationDebug counts these packets without treating them as proof of acceptance.
- Phase mode 5 throws a real ender pearl and lets the server own the teleport. It is the existing PearlPhase equivalent; no separate duplicate module was created.
- Blink queues only player movement packets, with a bounded queue. Turning it off flushes the queue in order by default. Interactions and keepalives are not queued.

The [Forge 1.12.2 NetHandlerPlayServer API](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.12.2/net/minecraft/network/NetHandlerPlayServer.html) exposes separate player and vehicle handlers. The [SPacketMoveVehicle API](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.12.2/net/minecraft/network/play/server/SPacketMoveVehicle.html) exposes the server's vehicle position and rotation. Server movement validation and plugins can still reject these modes.

## Compatibility matrix

| Feature | Implementation | Status |
| --- | --- | --- |
| BoatFly | Vanilla, Motion, Packet, Glide, stepped packet; correction backoff | Implemented, experimentally unverified |
| EntityFly | Shared controller for horses, donkeys, mules, llamas, pigs | Implemented, experimentally unverified |
| PacketFly | Direct, Motion, Pulse; rate limit and correction pause | Implemented, experimentally unverified |
| Phase | Existing movement probes and pearl mode | Existing, experimentally unverified |
| BoatPhase | Existing local boat collision flag | Client-side only; server replication unsupported |
| PearlPhase | Existing Phase mode 5 | Implemented through existing module; server result unverified |
| Burrow | One-shot obsidian packet placement; observes block updates and position corrections | Implemented, experimentally unverified |
| EntityDesync | Local detach, server passenger packet observation, remount request | Implemented, experimentally unverified |
| NoFall | Existing outgoing onGround rewrite | Existing, server damage outcome unverified |
| ElytraFly | Existing control and packet modes | Existing, server acceptance unverified |
| AntiVoid | Support scan, Elytra start request, optional low-Y disconnect | Implemented; server recovery unverified |
| Blink | Bounded queue; flush on disable | Implemented, experimentally unverified |
| PacketMine | Single-target start/stop packets with bounded retries and block-update observation | Implemented, experimentally unverified |
| FastBreak | Existing controller progress boost | Existing, server break result unverified |
| FastUse | Existing client right-click repeat reduction | Existing, server item-use outcome unverified |
| XCarry | Existing CraftCarry window-zero behavior | Existing limited equivalent; server dependent |
| MultiTask | Bounded use action while mining, with block use opt-in | Implemented, experimentally unverified |
| NoRotate | Rewrites only server rotation fields; vanilla still confirms teleport | Implemented, experimentally unverified |
| AntiHunger | Switches from sprinting to walking below a configurable food level | Implemented as ordinary movement; exhaustion reduction unmeasured |
| Movement conflict manager | Priority by active riding/elytra state | Implemented; disables competing controllers |
| ReplicationDebug | Local/requested/inbound position HUD | Implemented; observer-client verification still needed |
| Surround | Footprint ring, floor repair, safe inventory selection | Implemented; live placement test still needed |

No specific server has been tested for unsupported or accepted modes. The server may correct, kick, or ignore any experimental movement.

## Verification procedure

1. Enable ReplicationDebug and one movement module. Leave other movement controllers disabled.
2. Record the server identity and module settings.
3. Move in a known pattern while a second connected client records your position.
4. Compare local position, requested packet position, last inbound server position packet, and the observer's actual player/vehicle position at each timestamp.
5. Mark a mode server accepted only if the observer sees sustained movement and the server retains it after normal synchronization and reconnect.

The current build was compiled and the CrystalAura, NCP passability, and LavaCast harnesses passed. These harnesses do not simulate a multiplayer server or observing client.

## Using the added modules

- **Burrow:** Put obsidian in the hotbar or inventory and stand on a full block with two blocks of headroom. Enable it to make one placement attempt. Toggle off and on to retry. Its HUD separates a block update from a player-position correction; neither alone proves the final combined state.
- **EntityDesync:** Mount a vehicle, then enable it. The HUD initially says the server passenger state is unknown until a passenger packet arrives. Disable to request remount. This mode intentionally changes local mount state and is not a server flight mode.
- **AntiVoid:** Select warning, Elytra, or Elytra plus disconnect. Elytra recovery requires a worn Elytra and remains subject to server acceptance. Disconnect is a last resort and cannot guarantee survival.
- **PacketMine:** Aim at a block without holding attack. PacketMine pauses if manual attack, AutoMine, or AutoCity is active. It waits for a block update or a local air state and retries within configured limits.
- **MultiTask:** Hold use while the normal controller is mining. Food and potions are enabled by default; block use is opt-in.
- **NoRotate:** Preserves the local camera angle on `SPacketPlayerPosLook` while leaving position and teleport ID intact.
- **AntiHunger:** Walks instead of sprinting below the configured food level. It does not suppress jumping or other exhaustion sources.
