package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Linedef;
import com.hitpo.doommc3d.doommap.Sidedef;
import com.hitpo.doommc3d.doommap.Vertex;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ThingPlacerSectorGeometry {
    private ThingPlacerSectorGeometry() {
    }

    static boolean containsPoint(List<Vertex> polygon, double x, double y) {
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

    static List<Vertex> buildSectorPolygon(DoomMap map, int sectorIndex) {
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

