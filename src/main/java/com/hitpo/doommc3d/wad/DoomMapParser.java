package com.hitpo.doommc3d.wad;

import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Linedef;
import com.hitpo.doommc3d.doommap.Sector;
import com.hitpo.doommc3d.doommap.Sidedef;
import com.hitpo.doommc3d.doommap.Thing;
import com.hitpo.doommc3d.doommap.Vertex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class DoomMapParser {
    private DoomMapParser() {
    }

    public static DoomMap parse(WadFile wad, String mapName) {
        DoomMapLumps lumps = DoomMapLumps.read(wad, mapName);
        return new DoomMap(
            mapName,
            parseVertices(lumps.vertexes),
            parseLinedefs(lumps.linedefs),
            parseSidedefs(lumps.sidedefs),
            parseSectors(lumps.sectors),
            parseThings(lumps.things)
        );
    }

    private static Vertex[] parseVertices(ByteBuffer buffer) {
        ByteBuffer view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int count = view.remaining() / 4;
        Vertex[] vertices = new Vertex[count];
        for (int i = 0; i < count; i++) {
            int x = view.getShort();
            int y = view.getShort();
            vertices[i] = new Vertex(x, y);
        }
        return vertices;
    }

    private static Linedef[] parseLinedefs(ByteBuffer buffer) {
        ByteBuffer view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int count = view.remaining() / 14;
        Linedef[] linedefs = new Linedef[count];
        for (int i = 0; i < count; i++) {
            int start = view.getShort();
            int end = view.getShort();
            int flags = view.getShort();
            int specialType = view.getShort();
            int sectorTag = view.getShort();
            int right = view.getShort();
            int left = view.getShort();
            linedefs[i] = new Linedef(start, end, flags, specialType, sectorTag, right, left);
        }
        return linedefs;
    }

    private static Sidedef[] parseSidedefs(ByteBuffer buffer) {
        ByteBuffer view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int count = view.remaining() / 30;
        Sidedef[] sidedefs = new Sidedef[count];
        for (int i = 0; i < count; i++) {
            int xOffset = view.getShort();
            int yOffset = view.getShort();
            String upper = readString(view, 8);
            String lower = readString(view, 8);
            String middle = readString(view, 8);
            int sector = view.getShort();
            sidedefs[i] = new Sidedef(xOffset, yOffset, upper, lower, middle, sector);
        }
        return sidedefs;
    }

    private static Sector[] parseSectors(ByteBuffer buffer) {
        ByteBuffer view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int count = view.remaining() / 26;
        Sector[] sectors = new Sector[count];
        for (int i = 0; i < count; i++) {
            int floor = view.getShort();
            int ceiling = view.getShort();
            String floorTex = readString(view, 8);
            String ceilingTex = readString(view, 8);
            int lightLevel = view.getShort();
            int type = view.getShort();
            int tag = view.getShort();
            sectors[i] = new Sector(floor, ceiling, floorTex, ceilingTex, lightLevel, type, tag);
        }
        return sectors;
    }

    private static Thing[] parseThings(ByteBuffer buffer) {
        ByteBuffer view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int count = view.remaining() / 10;
        Thing[] things = new Thing[count];
        for (int i = 0; i < count; i++) {
            int x = view.getShort();
            int y = view.getShort();
            int angle = view.getShort();
            int type = view.getShort();
            int flags = view.getShort();
            things[i] = new Thing(x, y, angle, type, flags);
        }
        return things;
    }

    private static String readString(ByteBuffer view, int length) {
        byte[] bytes = new byte[length];
        view.get(bytes);
        return new String(bytes, StandardCharsets.US_ASCII).trim();
    }
}
