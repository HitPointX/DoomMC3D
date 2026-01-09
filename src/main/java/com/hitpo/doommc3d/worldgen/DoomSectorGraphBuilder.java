package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.convert.DoomOrigin;
import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Linedef;
import com.hitpo.doommc3d.doommap.Sector;
import com.hitpo.doommc3d.doommap.Sidedef;
import com.hitpo.doommc3d.doommap.Vertex;
import com.hitpo.doommc3d.interact.DoomSectorGraph;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

/**
 * Builds the sector graph used for Doom-like sound propagation.
 */
public final class DoomSectorGraphBuilder {
    // Vanilla Doom line flag (p_spec.h): blocks sound propagation.
    private static final int ML_SOUNDBLOCK = 64;

    private DoomSectorGraphBuilder() {
    }

    public static DoomSectorGraph build(DoomMap map, DoomOrigin origin, BlockPos buildOrigin) {
        Sector[] sectors = map.sectors();
        DoomSectorGraph.SectorNode[] nodes = new DoomSectorGraph.SectorNode[sectors.length];

        // Precompute sector polygons in Doom coordinates.
        List<List<Vertex>> polygons = new ArrayList<>(sectors.length);
        for (int i = 0; i < sectors.length; i++) {
            polygons.add(ThingPlacerSectorGeometry.buildSectorPolygon(map, i));
        }

        // Build sound adjacency: two-sided lines connect sectors unless ML_SOUNDBLOCK is set.
        @SuppressWarnings("unchecked")
        Set<Integer>[] neighborSets = new Set[sectors.length];
        for (int i = 0; i < sectors.length; i++) {
            neighborSets[i] = new HashSet<>();
        }

        Sidedef[] sidedefs = map.sidedefs();
        for (Linedef line : map.linedefs()) {
            int rightSector = sectorFromSide(sidedefs, line.rightSidedef());
            int leftSector = sectorFromSide(sidedefs, line.leftSidedef());
            if (rightSector < 0 || leftSector < 0 || rightSector == leftSector) {
                continue;
            }
            if ((line.flags() & ML_SOUNDBLOCK) != 0) {
                continue;
            }
            neighborSets[rightSector].add(leftSector);
            neighborSets[leftSector].add(rightSector);
        }

        for (int i = 0; i < sectors.length; i++) {
            List<Vertex> poly = polygons.get(i);
            int[] neighbors = neighborSets[i].stream().mapToInt(Integer::intValue).toArray();
            nodes[i] = new DoomSectorGraph.SectorNode(sectors[i].tag(), poly, neighbors);
        }

        return new DoomSectorGraph(buildOrigin, origin.originBlockX(), origin.originBlockZ(), nodes);
    }

    private static int sectorFromSide(Sidedef[] sidedefs, int sideIndex) {
        if (sideIndex < 0 || sideIndex >= sidedefs.length) {
            return -1;
        }
        return sidedefs[sideIndex].sector();
    }
}
