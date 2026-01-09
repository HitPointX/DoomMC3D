package com.hitpo.doommc3d.doommap;

public record Sector(int floorHeight, int ceilingHeight, String floorTexture, String ceilingTexture, int lightLevel, int type, int tag) {
}
