# Architecture

WAD File
  -> WadFile
    -> LumpReader
      -> DoomMapLumps
        -> DoomMapModel (Vertex, Linedef, Sector, etc)
          -> SectorRasterizer
            -> DoomWorldBuilder
              -> Minecraft World

The renderer is Minecraft itself. DOOM is only a geometry source.
