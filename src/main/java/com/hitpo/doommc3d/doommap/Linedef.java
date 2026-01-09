package com.hitpo.doommc3d.doommap;

public record Linedef(int startVertex, int endVertex, int flags, int specialType, int sectorTag, int rightSidedef, int leftSidedef) {
}
