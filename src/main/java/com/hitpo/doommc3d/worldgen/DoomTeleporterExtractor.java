package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.convert.DoomOrigin;
import com.hitpo.doommc3d.convert.DoomToMCScale;
import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Linedef;
import com.hitpo.doommc3d.doommap.Sector;
import com.hitpo.doommc3d.doommap.Sidedef;
import com.hitpo.doommc3d.doommap.Thing;
import com.hitpo.doommc3d.doommap.Vertex;
import com.hitpo.doommc3d.interact.DoomTeleporterTrigger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class DoomTeleporterExtractor {
    // Classic Doom linedef teleport specials (Doom format).
    private static final Set<Integer> TELEPORT_SPECIALS = Set.of(39, 97);

    private DoomTeleporterExtractor() {
    }

    public static List<DoomTeleporterTrigger> extract(DoomMap map, DoomOrigin origin, BlockPos buildOrigin) {
        Map<Integer, TeleportDest> destinations = computeDestinationsByTag(map, origin, buildOrigin);
        if (destinations.isEmpty()) {
            return List.of();
        }

        List<DoomTeleporterTrigger> triggers = new ArrayList<>();
        Vertex[] vertices = map.vertices();
        Sidedef[] sidedefs = map.sidedefs();
        Sector[] sectors = map.sectors();

        int id = 0;
        for (Linedef linedef : map.linedefs()) {
            if (!TELEPORT_SPECIALS.contains(linedef.specialType())) {
                continue;
            }
            int tag = linedef.sectorTag();
            if (tag == 0) {
                continue;
            }
            TeleportDest dest = destinations.get(tag);
            if (dest == null) {
                continue;
            }

            Vertex a = vertices[linedef.startVertex()];
            Vertex b = vertices[linedef.endVertex()];

            int x1 = DoomToMCScale.toBlock(a.x()) - origin.originBlockX();
            int z1 = origin.originBlockZ() - DoomToMCScale.toBlock(a.y());
            int x2 = DoomToMCScale.toBlock(b.x()) - origin.originBlockX();
            int z2 = origin.originBlockZ() - DoomToMCScale.toBlock(b.y());

            int rightSector = getSectorFromSide(sidedefs, linedef.rightSidedef());
            int leftSector = getSectorFromSide(sidedefs, linedef.leftSidedef());
            int floorRight = rightSector >= 0 ? DoomToMCScale.toBlock(sectors[rightSector].floorHeight()) : 0;
            int floorLeft = leftSector >= 0 ? DoomToMCScale.toBlock(sectors[leftSector].floorHeight()) : 0;
            int ceilRight = rightSector >= 0 ? DoomToMCScale.toBlock(sectors[rightSector].ceilingHeight()) : (floorRight + 4);
            int ceilLeft = leftSector >= 0 ? DoomToMCScale.toBlock(sectors[leftSector].ceilingHeight()) : (floorLeft + 4);
            int floorY = Math.min(floorRight, floorLeft);
            int ceilY = Math.max(ceilRight, ceilLeft);
            if (ceilY <= floorY) {
                ceilY = floorY + 4;
            }

            double minX = Math.min(x1, x2) + buildOrigin.getX() + 0.1;
            double maxX = Math.max(x1, x2) + buildOrigin.getX() + 0.9;
            double minZ = Math.min(z1, z2) + buildOrigin.getZ() + 0.1;
            double maxZ = Math.max(z1, z2) + buildOrigin.getZ() + 0.9;
            double yMin = buildOrigin.getY() + floorY;
            double yMax = buildOrigin.getY() + ceilY + 2;

            Box box = new Box(minX, yMin, minZ, maxX, yMax, maxZ).expand(0.4, 0.0, 0.4);
            triggers.add(new DoomTeleporterTrigger(id++, box, dest.pos, dest.yaw));
        }
        return triggers;
    }

    private static Map<Integer, TeleportDest> computeDestinationsByTag(DoomMap map, DoomOrigin origin, BlockPos buildOrigin) {
        Map<Integer, TeleportDest> byTag = new HashMap<>();
        for (Thing thing : map.things()) {
            if (thing.type() != 14) { // Doom teleport destination
                continue;
            }
            int sectorIndex = findSectorForThing(map, thing);
            if (sectorIndex < 0) {
                continue;
            }
            Sector sector = map.sectors()[sectorIndex];
            int tag = sector.tag();
            if (tag == 0) {
                continue;
            }

            int x = DoomToMCScale.toBlock(thing.x()) - origin.originBlockX();
            int z = origin.originBlockZ() - DoomToMCScale.toBlock(thing.y());
            int y = DoomToMCScale.toBlock(sector.floorHeight()) + 1;

            Vec3d pos = new Vec3d(buildOrigin.getX() + x + 0.5, buildOrigin.getY() + y, buildOrigin.getZ() + z + 0.5);
            float yaw = doomAngleToMinecraftYaw(thing.angle());
            byTag.putIfAbsent(tag, new TeleportDest(pos, yaw));
        }
        return byTag;
    }

    private static float doomAngleToMinecraftYaw(int doomAngleDeg) {
        // Doom: 0=East,90=North,180=West,270=South
        // MC: -90=East,180=North,90=West,0=South
        return (float) (-doomAngleDeg - 90.0);
    }

    private static int getSectorFromSide(Sidedef[] sidedefs, int sideIndex) {
        if (sideIndex < 0 || sideIndex >= sidedefs.length) {
            return -1;
        }
        return sidedefs[sideIndex].sector();
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

    private record Edge(int start, int end) {
    }

    private record TeleportDest(Vec3d pos, float yaw) {
    }
}

