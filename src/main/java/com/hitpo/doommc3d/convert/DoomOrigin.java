package com.hitpo.doommc3d.convert;

import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Thing;
import com.hitpo.doommc3d.doommap.Vertex;

public record DoomOrigin(int originBlockX, int originBlockZ) {
    public static DoomOrigin fromMap(DoomMap map) {
        Thing playerStart = findPlayerStart(map);
        if (playerStart != null) {
            return new DoomOrigin(
                DoomToMCScale.toBlock(playerStart.x()),
                DoomToMCScale.toBlock(playerStart.y())
            );
        }

        Vertex[] vertices = map.vertices();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Vertex vertex : vertices) {
            minX = Math.min(minX, vertex.x());
            maxX = Math.max(maxX, vertex.x());
            minZ = Math.min(minZ, vertex.y());
            maxZ = Math.max(maxZ, vertex.y());
        }
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        return new DoomOrigin(
            DoomToMCScale.toBlock(centerX),
            DoomToMCScale.toBlock(centerZ)
        );
    }

    private static Thing findPlayerStart(DoomMap map) {
        for (Thing thing : map.things()) {
            int type = thing.type();
            if (type >= 1 && type <= 4) {
                return thing;
            }
        }
        return map.things().length > 0 ? map.things()[0] : null;
    }
}

