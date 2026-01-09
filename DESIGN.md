# DoomMC3D Design Overview

This project follows a staged pipeline:

1. WAD Parsing Layer
   - Raw binary reading of WAD files
   - Directory table processing
   - Lump extraction

2. Doom Map Model
   - Strongly typed structures for Vertex, Linedef, Sidedef, Sector, Thing
   - No rendering assumptions

3. Conversion Layer
   - Doom units -> Minecraft blocks (default scale: 32 units = 1 block)
   - Sector polygon rasterization
   - Wall extrusion between floor/ceiling heights

4. World Builder
   - Chunk-aware block placement
   - Batched placement per tick to avoid TPS death

5. Gameplay Layer (future)
   - Doors
   - Triggers
   - Monster spawns
   - Item pickups

VR is supported automatically through Vivecraft since this is real world geometry.
