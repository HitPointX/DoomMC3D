package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.convert.DoomOrigin;
import com.hitpo.doommc3d.convert.DoomToMCScale;
import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Linedef;
import com.hitpo.doommc3d.doommap.Sidedef;
import com.hitpo.doommc3d.doommap.Thing;
import com.hitpo.doommc3d.doommap.Vertex;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class DoomTeleporterPadPlacer {
    private DoomTeleporterPadPlacer() {
    }

    public static int placePads(ServerWorld world, DoomMap map, DoomOrigin origin, BlockPos buildOrigin) {
        int placed = 0;
        for (Thing thing : map.things()) {
            if (thing.type() != 14) { // Doom teleport destination
                continue;
            }

            int sectorIndex = findSectorForThing(map, thing);
            int floorY = sectorIndex >= 0 ? DoomToMCScale.toBlock(map.sectors()[sectorIndex].floorHeight()) : 0;

            int x = DoomToMCScale.toBlock(thing.x()) - origin.originBlockX();
            int z = origin.originBlockZ() - DoomToMCScale.toBlock(thing.y());

            BlockPos center = buildOrigin.add(x, floorY, z);
            if (!world.isChunkLoaded(center)) {
                continue;
            }

            // Simple Doom1-ish telepad: dark border + cyan center + hidden light.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = center.add(dx, 0, dz);
                    if (dx == 0 && dz == 0) {
                        world.setBlockState(p, Blocks.CYAN_STAINED_GLASS.getDefaultState(), 3);
                    } else {
                        world.setBlockState(p, Blocks.POLISHED_BLACKSTONE.getDefaultState(), 3);
                    }
                }
            }

            if (center.getY() - 1 >= world.getBottomY()) {
                world.setBlockState(center.down(), Blocks.SEA_LANTERN.getDefaultState(), 3);
            }
            placed++;
        }
        return placed;
    }

    private static int findSectorForThing(DoomMap map, Thing thing) {
        double x = thing.x();
        double y = thing.y();
        for (int sectorIndex = 0; sectorIndex < map.sectors().length; sectorIndex++) {
            List<Vertex> polygon = buildSectorPolygon(map, sectorIndex);
            if (polygon.isEmpty()) {
                continue;
            }
            if (containsPoint(polygon, x, y)) {
                return sectorIndex;
            }
        }
        return -1;
    }

    private static boolean containsPoint(List<Vertex> polygon, double x, double y) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            Vertex vi = polygon.get(i);
            Vertex vj = polygon.get(j);
            boolean intersect = ((vi.y() > y) != (vj.y() > y))
                && (x < (vj.x() - vi.x()) * (y - vi.y()) / (double) (vj.y() - vi.y()) + vi.x());
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static List<Vertex> buildSectorPolygon(DoomMap map, int sectorIndex) {
        List<Edge> edges = collectSectorEdges(map, sectorIndex);
        if (edges.isEmpty()) {
            return List.of();
        }
        List<Edge> ordered = orderEdges(edges);
        Vertex[] vertices = map.vertices();
        List<Vertex> polygon = new ArrayList<>();
        for (Edge edge : ordered) {
            polygon.add(vertices[edge.start]);
        }
        return polygon;
    }

    private static List<Edge> collectSectorEdges(DoomMap map, int sectorIndex) {
        List<Edge> edges = new ArrayList<>();
        Linedef[] linedefs = map.linedefs();
        Sidedef[] sidedefs = map.sidedefs();
        for (Linedef linedef : linedefs) {
            int rightSector = getSectorFromSide(sidedefs, linedef.rightSidedef());
            int leftSector = getSectorFromSide(sidedefs, linedef.leftSidedef());
            if (rightSector == sectorIndex) {
                edges.add(new Edge(linedef.startVertex(), linedef.endVertex()));
            } else if (leftSector == sectorIndex) {
                edges.add(new Edge(linedef.endVertex(), linedef.startVertex()));
            }
        }
        return edges;
    }

    private static List<Edge> orderEdges(List<Edge> edges) {
        List<Edge> ordered = new ArrayList<>();
        Set<Edge> used = new HashSet<>();
        ordered.add(edges.get(0));
        used.add(edges.get(0));
        while (ordered.size() < edges.size()) {
            Edge current = ordered.get(ordered.size() - 1);
            Edge next = findNextEdge(edges, used, current.end);
            if (next == null) {
                break;
            }
            ordered.add(next);
            used.add(next);
        }
        return ordered;
    }

    private static Edge findNextEdge(List<Edge> edges, Set<Edge> used, int startVertex) {
        for (Edge edge : edges) {
            if (!used.contains(edge) && edge.start == startVertex) {
                return edge;
            }
        }
        return null;
    }

    private static int getSectorFromSide(Sidedef[] sidedefs, int sideIndex) {
        if (sideIndex < 0 || sideIndex >= sidedefs.length) {
            return -1;
        }
        return sidedefs[sideIndex].sector();
    }

    private record Edge(int start, int end) {
    }
}
