# Local NCP diagnostics lab

This directory contains a loopback-only launcher and a log-to-CSV inspector for a private Minecraft 1.12.2 Paper/Spigot server with NoCheatPlus installed. The launcher binds to `127.0.0.1` on port `25566`, disables RCON/query, and stores server output under `logs/`.

## Setup

1. Run `powershell -ExecutionPolicy Bypass -File .\tools\ncp-lab\Get-NcpLabArtifacts.ps1` to fetch Paper 1.12.2 and the latest successful Updated-NoCheatPlus CI artifact. The script verifies Paper's published SHA-256 and checks that the NCP jar contains a matching `plugin.yml`. It writes artifact/version details to `server/ARTIFACTS.txt`. For a pinned Paper build, pass `-PaperBuild <number>`; for an older known NCP build, pass `-NcpBuild <Jenkins-build-number>`.
2. Alternatively, place a compatible Paper/Spigot server jar at `server/server.jar` and NCP plugin jar at `server/plugins/NoCheatPlus.jar` (or pass `-NcpPluginJar`).
3. Review Minecraft's EULA and, if you accept it, set `eula=true` in `server/eula.txt`. The launcher intentionally does not accept it automatically.
4. Run `powershell -ExecutionPolicy Bypass -File .\tools\ncp-lab\Start-NcpLab.ps1` from the project root.
5. Connect the 1.12.2 client to `127.0.0.1:25566`; `PacketFlyLab` records ordinary client movement, corrections, and NCP alerts. It is a recorder, not an active flight module.
6. Run `powershell -ExecutionPolicy Bypass -File .\tools\ncp-lab\Inspect-NcpLabLogs.ps1` to inspect the NCP check lines and export `logs/ncp-movement-events.csv`.

The log exporter retains each raw line and its source line number so its parsed check/VL fields can be audited. NCP console formats vary by build/configuration; unmatched lines stay in the raw server log. This tooling collects observations and does not alter movement packets or modify NCP checks.

## Current workspace status

The current lab artifacts are Paper 1.12.2 build 1620 and NoCheatPlus `3.17.1-SNAPSHOT-Updated-b279` (CI build 279); exact download hash and artifact path are recorded in `server/ARTIFACTS.txt`. The server is currently configured for loopback port 25566. `Start-NcpLab.ps1` prefers the bundled 64-bit Java 8 because this project's PATH resolves to a 32-bit JVM. The EULA acceptance is recorded in `server/eula.txt` per the user's instruction. The existing `ncpPassableHarness` is still a reduced swept-AABB model, not the actual NCP implementation.
