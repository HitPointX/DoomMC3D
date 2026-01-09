package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.convert.DoomOrigin;
import com.hitpo.doommc3d.convert.DoomToMCScale;
import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Linedef;
import com.hitpo.doommc3d.doommap.Sector;
import com.hitpo.doommc3d.doommap.Sidedef;
import com.hitpo.doommc3d.doommap.Vertex;
import com.hitpo.doommc3d.interact.DoomSecretTrigger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class DoomSecretExtractor {
    private DoomSecretExtractor() {
    }

    public static List<DoomSecretTrigger> extract(DoomMap map, DoomOrigin origin, BlockPos buildOrigin) {
        List<DoomSecretTrigger> out = new ArrayList<>();
        for (int sectorIndex = 0; sectorIndex < map.sectors().length; sectorIndex++) {
            Sector sector = map.sectors()[sectorIndex];
            if (sector.type() != 9) { // Doom "secret"
                continue;
            }
            List<Vertex> polygon = buildSectorPolygon(map, sectorIndex);
            if (polygon.isEmpty()) {
                continue;
            }
            int minX = polygon.stream().mapToInt(Vertex::x).min().orElse(0);
            int maxX = polygon.stream().mapToInt(Vertex::x).max().orElse(0);
            int minY = polygon.stream().mapToInt(Vertex::y).min().orElse(0);
            int maxY = polygon.stream().mapToInt(Vertex::y).max().orElse(0);

            int startX = DoomToMCScale.toBlock(minX) - origin.originBlockX();
            int endX = DoomToMCScale.toBlock(maxX) - origin.originBlockX();
            int startZ = origin.originBlockZ() - DoomToMCScale.toBlock(maxY);
            int endZ = origin.originBlockZ() - DoomToMCScale.toBlock(minY);

            int floorY = DoomToMCScale.toBlock(sector.floorHeight());
            int ceilY = DoomToMCScale.toBlock(sector.ceilingHeight());
            if (ceilY <= floorY) {
                ceilY = floorY + 3;
            }

            BlockPos a = buildOrigin.add(startX, floorY, startZ);
            BlockPos b = buildOrigin.add(endX + 1, ceilY + 2, endZ + 1);
            Box box = new Box(
                a.getX(), a.getY(), a.getZ(),
                b.getX(), b.getY(), b.getZ()
            );
            out.add(new DoomSecretTrigger(sectorIndex, box));
        }
        return out;
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
