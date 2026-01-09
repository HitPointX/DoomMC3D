package com.hitpo.doommc3d.wad;

public class WadDirectoryEntry {
    private final int offset;
    private final int size;
    private final String name;

    public WadDirectoryEntry(int offset, int size, String name) {
        this.offset = offset;
        this.size = size;
        this.name = name;
    }

    public int getOffset() {
        return offset;
    }

    public int getSize() {
        return size;
    }

    public String getName() {
        return name;
    }
}
