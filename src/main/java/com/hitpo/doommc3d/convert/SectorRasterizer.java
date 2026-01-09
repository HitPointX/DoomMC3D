package com.hitpo.doommc3d.convert;

import com.hitpo.doommc3d.DoomConstants;
import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Linedef;
import com.hitpo.doommc3d.doommap.Sector;
import com.hitpo.doommc3d.doommap.Sidedef;
import com.hitpo.doommc3d.doommap.Thing;
import com.hitpo.doommc3d.doommap.Vertex;
import com.hitpo.doommc3d.worldgen.BlockPlacer;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SectorRasterizer {
    private static final BlockState DEFAULT_FLOOR_STATE = Blocks.DEEPSLATE_TILES.getDefaultState();
    private static final BlockState DEFAULT_CEILING_STATE = Blocks.SMOOTH_STONE.getDefaultState();
    private static final BlockState FALLBACK_WALL_STATE = Blocks.POLISHED_ANDESITE.getDefaultState();
    private static final BlockState BAND_TOP_BOTTOM_STATE = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
    private static final BlockState BAND_MIDDLE_STATE = Blocks.POLISHED_ANDESITE.getDefaultState();
    private static final BlockState PILLAR_MIDDLE_STATE = Blocks.CUT_SANDSTONE.getDefaultState();
    private static final BlockState PILLAR_TRIM_STATE = Blocks.SMOOTH_QUARTZ.getDefaultState();
    private static final BlockState AIR_STATE = Blocks.AIR.getDefaultState();
    private static final BlockState LIGHT_STATE = Blocks.GLOWSTONE.getDefaultState();
    private static final int MIN_INTERIOR_AIR_BLOCKS = 3;
    private static final int PILLAR_MAX_BOUNDS_BLOCKS = 6;
    private static final int PILLAR_MAX_AREA_BLOCKS = 28;

    public void rasterize(DoomMap map, BlockPlacer placer, DoomOrigin origin) {
        Vertex[] vertices = map.vertices();
        if (vertices.length == 0) {
            return;
        }
        int originBlockX = origin.originBlockX();
        int originBlockZ = origin.originBlockZ();
        for (int sector = 0; sector < map.sectors().length; sector++) {
            rasterizeSector(map, sector, placer, originBlockX, originBlockZ);
        }
    }

    private void rasterizeSector(DoomMap map, int sectorIndex, BlockPlacer placer, int originBlockX, int originBlockZ) {
        Sector sector = map.sectors()[sectorIndex];
        List<Vertex> polygon = buildSectorPolygon(map, sectorIndex);
        if (polygon.isEmpty()) {
            return;
        }
        int minX = polygon.stream().mapToInt(Vertex::x).min().orElse(0);
        int maxX = polygon.stream().mapToInt(Vertex::x).max().orElse(0);
        int minZ = polygon.stream().mapToInt(Vertex::y).min().orElse(0);
        int maxZ = polygon.stream().mapToInt(Vertex::y).max().orElse(0);

        int floorY = DoomToMCScale.toBlock(sector.floorHeight());
        int ceilingY = DoomToMCScale.toBlock(sector.ceilingHeight());
        if (ceilingY <= floorY) {
            ceilingY = floorY + 1;
        }
        if ((ceilingY - floorY - 1) < MIN_INTERIOR_AIR_BLOCKS) {
            ceilingY = floorY + MIN_INTERIOR_AIR_BLOCKS + 1;
        }

        int startX = DoomToMCScale.toBlock(minX) - originBlockX;
        int endX = DoomToMCScale.toBlock(maxX) - originBlockX;
        int startZ = originBlockZ - DoomToMCScale.toBlock(maxZ);
        int endZ = originBlockZ - DoomToMCScale.toBlock(minZ);

        BlockState floorState = PaletteMapper.mapFloor(sector.floorTexture());
        BlockState ceilingState = PaletteMapper.mapCeiling(sector.ceilingTexture());
        if (floorState == null) floorState = DEFAULT_FLOOR_STATE;
        if (ceilingState == null) ceilingState = DEFAULT_CEILING_STATE;

        int boundsX = (endX - startX + 1);
        int boundsZ = (endZ - startZ + 1);
        boolean pillarCandidate = boundsX <= PILLAR_MAX_BOUNDS_BLOCKS && boundsZ <= PILLAR_MAX_BOUNDS_BLOCKS
            && (boundsX * boundsZ) <= PILLAR_MAX_AREA_BLOCKS;

        int filled = 0;
        for (int blockX = startX; blockX <= endX; blockX++) {
            for (int blockZ = startZ; blockZ <= endZ; blockZ++) {
                double doomX = toDoomX(blockX, originBlockX);
                double doomZ = toDoomZ(blockZ, originBlockZ);
                if (containsPoint(polygon, doomX, doomZ)) {
                    filled++;
                    placer.placeBlock(blockX, floorY, blockZ, floorState);
                    placer.placeBlock(blockX, ceilingY, blockZ, ceilingState);
                    if (ceilingY - floorY > 1) {
                        placer.placeColumn(blockX, blockZ, floorY + 1, ceilingY - 1, AIR_STATE);
                    }
                    maybePlaceLight(sector, placer, blockX, blockZ, ceilingY);
                }
            }
        }

        boolean isPillar = pillarCandidate && filled > 0 && filled <= PILLAR_MAX_AREA_BLOCKS;
        extrudeWalls(map, sectorIndex, placer, floorY, ceilingY, originBlockX, originBlockZ, isPillar);
    }

    private void extrudeWalls(DoomMap map, int sectorIndex, BlockPlacer placer, int floorY, int ceilingY, int originBlockX, int originBlockZ, boolean isPillar) {
        Linedef[] linedefs = map.linedefs();
        Sidedef[] sidedefs = map.sidedefs();
        for (Linedef linedef : linedefs) {
            WallSlice slice = buildWallSlice(map, sectorIndex, linedef, sidedefs);
            if (slice == null) {
                continue;
            }

            Vertex[] vertices = map.vertices();
            Vertex start = vertices[slice.startVertex];
            Vertex end = vertices[slice.endVertex];
            int startX = toRelativeBlockX(start.x(), originBlockX);
            int startZ = toRelativeBlockZ(start.y(), originBlockZ);
            int endX = toRelativeBlockX(end.x(), originBlockX);
            int endZ = toRelativeBlockZ(end.y(), originBlockZ);
            int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
            for (int step = 0; step <= steps; step++) {
                int x = steps == 0 ? startX : startX + Math.round((endX - startX) * (step / (float) steps));
                int z = steps == 0 ? startZ : startZ + Math.round((endZ - startZ) * (step / (float) steps));
                placeWallColumn(map, sectorIndex, floorY, ceilingY, slice.neighborSector, x, z, slice.sidedef, placer, isPillar);
            }
        }
    }

    private WallSlice buildWallSlice(DoomMap map, int sectorIndex, Linedef linedef, Sidedef[] sidedefs) {
        Sidedef right = safeSidedef(sidedefs, linedef.rightSidedef());
        if (right != null && right.sector() == sectorIndex) {
            int neighbor = getSectorFromSide(sidedefs, linedef.leftSidedef());
            return new WallSlice(linedef.startVertex(), linedef.endVertex(), neighbor, right);
        }
        Sidedef left = safeSidedef(sidedefs, linedef.leftSidedef());
        if (left != null && left.sector() == sectorIndex) {
            int neighbor = getSectorFromSide(sidedefs, linedef.rightSidedef());
            return new WallSlice(linedef.endVertex(), linedef.startVertex(), neighbor, left);
        }
        return null;
    }

    private Sidedef safeSidedef(Sidedef[] sidedefs, int index) {
        if (index < 0 || index >= sidedefs.length) {
            return null;
        }
        return sidedefs[index];
    }

    private BlockState paletteOrFallback(Sidedef side) {
        BlockState state = PaletteMapper.mapWall(side.middleTexture());
        return state != null ? state : FALLBACK_WALL_STATE;
    }

    private void placeWallColumn(DoomMap map, int sectorIndex, int floorY, int ceilingY, int neighborIndex, int x, int z, Sidedef side, BlockPlacer placer, boolean isPillar) {
        if (neighborIndex < 0) {
            // One-sided: fill full height with middle texture
            BlockState state = PaletteMapper.mapWall(side.middleTexture());
            if (state == null) state = FALLBACK_WALL_STATE;
            placeStyledWallColumn(x, z, floorY, ceilingY, state, placer, isPillar);
            return;
        }

        Sector neighbor = map.sectors()[neighborIndex];
        int neighborFloor = DoomToMCScale.toBlock(neighbor.floorHeight());
        int neighborCeiling = DoomToMCScale.toBlock(neighbor.ceilingHeight());

        int floorMin = Math.min(floorY, neighborFloor);
        int floorMax = Math.max(floorY, neighborFloor);
        int ceilMin = Math.min(ceilingY, neighborCeiling);
        int ceilMax = Math.max(ceilingY, neighborCeiling);

        // Lower wall segment (floor mismatch) -> lowerTexture
        if (floorMax > floorMin) {
            BlockState lower = PaletteMapper.mapWall(side.lowerTexture());
            if (lower == null) lower = FALLBACK_WALL_STATE;
            placeStyledWallColumn(x, z, floorMin, floorMax - 1, lower, placer, isPillar);
        }

        // Upper wall segment (ceiling mismatch) -> upperTexture
        if (ceilMax > ceilMin) {
            BlockState upper = PaletteMapper.mapWall(side.upperTexture());
            if (upper == null) upper = FALLBACK_WALL_STATE;
            placeStyledWallColumn(x, z, ceilMin + 1, ceilMax, upper, placer, isPillar);
        }

        // Midtexture bars/windows on two-sided lines (no height mismatch)
        boolean heightsMatch = (floorY == neighborFloor) && (ceilingY == neighborCeiling);
        boolean hasMid = side.middleTexture() != null
            && !side.middleTexture().isBlank()
            && !side.middleTexture().equals("-");

        if (heightsMatch && hasMid) {
            // Put bars in the opening
            BlockState mid = PaletteMapper.mapWall(side.middleTexture());
            if (mid == null) mid = FALLBACK_WALL_STATE;
            placeStyledWallColumn(x, z, floorY, ceilingY - 1, mid, placer, isPillar);
        }
    }

    private void placeStyledWallColumn(int x, int z, int startY, int endY, BlockState state, BlockPlacer placer, boolean isPillar) {
        int bottom = Math.min(startY, endY);
        int top = Math.max(startY, endY);
        for (int y = bottom; y <= top; y++) {
            BlockState placed = state;
            if (isPillar) {
                boolean trim = y == bottom || y == bottom + 1 || y == top || y == top - 1;
                placed = trim ? PILLAR_TRIM_STATE : PILLAR_MIDDLE_STATE;
            } else {
                boolean band = y == bottom || y == bottom + 1 || y == top || y == top - 1;
                placed = band ? BAND_TOP_BOTTOM_STATE : BAND_MIDDLE_STATE;
            }
            placer.placeBlock(x, y, z, placed);
        }
    }

    private void maybePlaceLight(Sector sector, BlockPlacer placer, int x, int z, int ceilingY) {
        int lightLevel = sector.lightLevel();
        int mcLight = (int) Math.round((Math.max(0, Math.min(255, lightLevel)) / 255.0) * 15.0);
        int spacing = lightSpacing(mcLight);
        if (spacing <= 0) {
            return;
        }
        if (Math.floorMod(x, spacing) == 0 && Math.floorMod(z, spacing) == 0) {
            placer.placeBlock(x, ceilingY, z, LIGHT_STATE);
        }
    }

    private int lightSpacing(int mcLight) {
        if (mcLight >= 14) {
            return 10;
        }
        if (mcLight >= 12) {
            return 14;
        }
        if (mcLight >= 10) {
            return 18;
        }
        if (mcLight >= 8) {
            return 26;
        }
        return -1;
    }

    private int toRelativeBlockX(int doomX, int originBlockX) {
        return DoomToMCScale.toBlock(doomX) - originBlockX;
    }

    private int toRelativeBlockZ(int doomY, int originBlockZ) {
        return originBlockZ - DoomToMCScale.toBlock(doomY);
    }

    private double toDoomX(int blockX, int originBlockX) {
        return (blockX + originBlockX) * (double) DoomConstants.DOOM_TO_MC_SCALE + DoomConstants.DOOM_TO_MC_SCALE / 2.0;
    }

    private double toDoomZ(int blockZ, int originBlockZ) {
        return (originBlockZ - blockZ) * (double) DoomConstants.DOOM_TO_MC_SCALE + DoomConstants.DOOM_TO_MC_SCALE / 2.0;
    }

    @Deprecated(forRemoval = true)
    private int toRelativeBlock(int doomCoord, int originBlock) {
        return DoomToMCScale.toBlock(doomCoord) - originBlock;
    }

    @Deprecated(forRemoval = true)
    private double toDoomCoordinate(int blockCoord, int originBlock) {
        return (blockCoord + originBlock) * (double) DoomConstants.DOOM_TO_MC_SCALE + DoomConstants.DOOM_TO_MC_SCALE / 2.0;
    }

    private boolean containsPoint(List<Vertex> polygon, double x, double y) {
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

    private List<Vertex> buildSectorPolygon(DoomMap map, int sectorIndex) {
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

    private List<Edge> collectSectorEdges(DoomMap map, int sectorIndex) {
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

    private List<Edge> orderEdges(List<Edge> edges) {
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

    private Edge findNextEdge(List<Edge> edges, Set<Edge> used, int startVertex) {
        for (Edge edge : edges) {
            if (!used.contains(edge) && edge.start == startVertex) {
                return edge;
            }
        }
        return null;
    }

    private int getSectorFromSide(Sidedef[] sidedefs, int sideIndex) {
        if (sideIndex < 0 || sideIndex >= sidedefs.length) {
            return -1;
        }
        return sidedefs[sideIndex].sector();
    }

    private static final class Edge {
        private final int start;
        private final int end;

        private Edge(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private record WallSlice(int startVertex, int endVertex, int neighborSector, Sidedef sidedef) {
    }
}
