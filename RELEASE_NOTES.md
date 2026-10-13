# Backspace dev-3.5-beta

Beta 3.5 adds four combat awareness modules to Backspace for Minecraft Forge 1.12.2.

## New modules

- **BreakESP** highlights blocks being mined using server-reported break progress. It can show the miner's name and omit your own mining.
- **PopChams** freezes a yellow translucent player silhouette at a confirmed totem pop, then fades it out.
- **LogoutSpots** marks a player's last observed location when the server removes them from its player list. Spots clear when the player rejoins or the world changes.
- **PearlTrace** tracks an opponent's active ender pearl, simulates its motion against loaded blocks and entities, and marks the likely landing point. The prediction updates each tick; it is an estimate, especially under lag or unseen world changes.

All four modules have range and display settings in the existing ClickGUI.

## Install

1. Download `backspace-dev-3.5-beta.jar` and `OneConfig-1.12.2-forge.jar` from this release.
2. Put both files in the same Minecraft 1.12.2 Forge `mods` folder.
3. Launch Forge 14.23.5.2859 with Java 8.
4. Press Right Shift or run `/backspace` in-game.

## SHA-256

```text
64fa74a4eba740872f26653b02d97a6ba0277a270f0ea7f8df21fbcfe3c5acec  backspace-dev-3.5-beta.jar
e8da29639341204da12b5f7d78c2b8434eac97a751f1808906114af44bf204af  OneConfig-1.12.2-forge.jar
```
