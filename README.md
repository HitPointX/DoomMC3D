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

Important legal note
-------------------

- This mod requires a legally acquired copy of the original DOOM WAD data (for example `DOOM.WAD` or `DOOM2.WAD`). We do not distribute WAD files. You must own a copy of DOOM to use those data files with this project.

Installation / where to place WAD files
--------------------------------------

1. Obtain a legal WAD file. If you do not own DOOM, consider purchasing it from an official retailer (e.g., Steam, GOG). The purchased copy will include the WAD files.

2. Copy the WAD file into the project's run directory. The mod looks for WAD files in `run/WADS/` by default. Create the folder if it does not exist.

Example (from a Unix-like shell):

```bash
# create the WADS folder if needed
mkdir -p run/WADS

# copy your legally obtained WAD into the project
cp /path/to/DOOM.WAD run/WADS/DOOM.WAD

# then run the client
./gradlew runClient
```

Notes:
- The loader will scan `run/WADS/` for recognized WAD files. If you prefer a different location you may copy the WAD into the `run/` root, but `run/WADS/` is the recommended convention.
- Keep in mind some operating systems treat file paths case-insensitively; use the `WADS` directory name consistently.

If you'd like, I can add an automated WAD detection log message (which WADs were found at startup) or a small script to copy a WAD into place.
