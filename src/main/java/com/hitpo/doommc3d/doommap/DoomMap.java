package com.hitpo.doommc3d.doommap;

public record DoomMap(
    String name,
    Vertex[] vertices,
    Linedef[] linedefs,
    Sidedef[] sidedefs,
    Sector[] sectors,
    Thing[] things
) {
}
