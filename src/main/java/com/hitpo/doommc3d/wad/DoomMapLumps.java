package com.hitpo.doommc3d.wad;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

public class DoomMapLumps {
    public final ByteBuffer things;
    public final ByteBuffer linedefs;
    public final ByteBuffer sidedefs;
    public final ByteBuffer vertexes;
    public final ByteBuffer sectors;

    private DoomMapLumps(ByteBuffer things, ByteBuffer linedefs, ByteBuffer sidedefs, ByteBuffer vertexes, ByteBuffer sectors) {
        this.things = things;
        this.linedefs = linedefs;
        this.sidedefs = sidedefs;
        this.vertexes = vertexes;
        this.sectors = sectors;
    }

    public static DoomMapLumps read(WadFile wad, String mapName) {
        List<WadDirectoryEntry> entries = wad.getDirectory();
        String upperName = mapName.toUpperCase(Locale.ROOT);
        int mapIndex = findMapIndex(entries, upperName);
        int base = mapIndex + 1;
        WadDirectoryEntry things = requireEntry(entries, base, "THINGS");
        WadDirectoryEntry linedefs = requireEntry(entries, base + 1, "LINEDEFS");
        WadDirectoryEntry sidedefs = requireEntry(entries, base + 2, "SIDEDEFS");
        WadDirectoryEntry vertexes = requireEntry(entries, base + 3, "VERTEXES");
        WadDirectoryEntry sectors = requireEntry(entries, base + 7, "SECTORS");
        return new DoomMapLumps(
            wad.readLump(things),
            wad.readLump(linedefs),
            wad.readLump(sidedefs),
            wad.readLump(vertexes),
            wad.readLump(sectors)
        );
    }

    private static int findMapIndex(List<WadDirectoryEntry> entries, String target) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getName().equals(target)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Map not found: " + target);
    }

    private static WadDirectoryEntry requireEntry(List<WadDirectoryEntry> entries, int index, String expectedName) {
        if (index < 0 || index >= entries.size()) {
            throw new IllegalArgumentException("Expected " + expectedName + " after map but directory truncated");
        }
        WadDirectoryEntry entry = entries.get(index);
        if (!entry.getName().equals(expectedName)) {
            throw new IllegalArgumentException("Expected " + expectedName + " but found " + entry.getName());
        }
        return entry;
    }
}
