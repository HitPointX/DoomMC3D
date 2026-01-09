# DoomMC3D

DoomMC3D is a Fabric mod targeting Minecraft 1.21.11 (with Vivecraft compatibility) that converts classic DOOM (1993) WAD levels into fully playable 3D Minecraft worlds. This is **not** a screen overlay or emulator window. The DOOM levels are parsed and reconstructed as actual Minecraft geometry.

You spawn inside E1M1. You walk the level. In VR. Like a deranged architect.

Goals:
- Parse WAD geometry (VERTEXES, LINEDEFS, SIDEDEFS, SECTORS, THINGS)
- Convert 2.5D DOOM sectors into Minecraft block volumes
- Map textures to Minecraft block palettes or resource pack textures
- Spawn player and entities at correct positions
- Eventually support doors, lifts, triggers, and secrets

This project is intentionally insane.

Build:
- JDK 21+
- Fabric Loom
- Minecraft 1.21.11

Run:
./gradlew runClient

Command:
/doomgen e1m1

This will generate E1M1 at your current position.
