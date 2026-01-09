package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.convert.DoomOrigin;
import com.hitpo.doommc3d.convert.DoomToMCScale;
import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Vertex;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class DoomSpawnCleanup {
    private DoomSpawnCleanup() {
    }

    public static int clearSpawnedEntities(ServerWorld world, DoomMap map, DoomOrigin origin, BlockPos buildOrigin) {
        Box box = computeBounds(world, map, origin, buildOrigin);
        int removed = 0;
        for (Entity e : world.getEntitiesByClass(Entity.class, box, ent -> ent.getCommandTags().contains(DoomThingSpawner.TAG_SPAWNED))) {
            e.discard();
            removed++;
        }
        return removed;
    }

    public static Box computeBounds(ServerWorld world, DoomMap map, DoomOrigin origin, BlockPos buildOrigin) {
        Vertex[] vertices = map.vertices();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Vertex v : vertices) {
            int x = DoomToMCScale.toBlock(v.x()) - origin.originBlockX();
            int z = origin.originBlockZ() - DoomToMCScale.toBlock(v.y());
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        // A bit of slack so we catch strays and FX entities spawned near edges.
        int pad = 16;
        double x1 = buildOrigin.getX() + minX - pad;
        double z1 = buildOrigin.getZ() + minZ - pad;
        double x2 = buildOrigin.getX() + maxX + pad + 1;
        double z2 = buildOrigin.getZ() + maxZ + pad + 1;

        double y1 = worldBottomSafe(world);
        double y2 = worldTopSafe(world);
        return new Box(x1, y1, z1, x2, y2, z2);
    }

    private static double worldBottomSafe(ServerWorld world) {
        return world.getBottomY() - 8;
    }

    private static double worldTopSafe(ServerWorld world) {
        // Inclusive-ish; doesn't need to be exact for entity cleanup.
        return world.getBottomY() + 512;
    }
}
