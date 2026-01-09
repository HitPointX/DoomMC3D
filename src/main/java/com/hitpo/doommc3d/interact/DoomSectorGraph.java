package com.hitpo.doommc3d.interact;

import com.hitpo.doommc3d.DoomConstants;
import com.hitpo.doommc3d.doommap.Vertex;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Minimal sector graph for Doom-like sound propagation.
 *
 * In vanilla Doom, sound propagates sector-to-sector and is blocked by lines with
 * the sound-block flag (ML_SOUNDBLOCK). This registry keeps enough data to implement
 * that behavior in a Minecraft world built from a WAD.
 */
public final class DoomSectorGraph {
    private final BlockPos buildOrigin;
    private final int originBlockX;
    private final int originBlockZ;

    private final SectorNode[] sectors;

    public DoomSectorGraph(BlockPos buildOrigin, int originBlockX, int originBlockZ, SectorNode[] sectors) {
        this.buildOrigin = buildOrigin.toImmutable();
        this.originBlockX = originBlockX;
        this.originBlockZ = originBlockZ;
        this.sectors = sectors;
    }

    public int findSectorIndex(Vec3d worldPos) {
        double doomX = toDoomX(worldPos.x);
        double doomZ = toDoomZ(worldPos.z);
        for (int i = 0; i < sectors.length; i++) {
            SectorNode s = sectors[i];
            if (s == null || s.polygon.isEmpty()) {
                continue;
            }
            if (doomX < s.minX || doomX > s.maxX || doomZ < s.minZ || doomZ > s.maxZ) {
                continue;
            }
            if (containsPoint(s.polygon, doomX, doomZ)) {
                return i;
            }
        }
        return -1;
    }

    public Set<Integer> floodSoundReachable(int startSector) {
        if (startSector < 0 || startSector >= sectors.length) {
            return Set.of();
        }
        Set<Integer> visited = new HashSet<>();
        ArrayDeque<Integer> q = new ArrayDeque<>();
        visited.add(startSector);
        q.add(startSector);

        while (!q.isEmpty()) {
            int s = q.removeFirst();
            SectorNode node = sectors[s];
            if (node == null) {
                continue;
            }
            for (int n : node.soundNeighbors) {
                if (n < 0 || n >= sectors.length) {
                    continue;
                }
                if (visited.add(n)) {
                    q.addLast(n);
                }
            }
        }
        return visited;
    }

    private double toDoomX(double worldX) {
        double relBlock = worldX - buildOrigin.getX();
        return (relBlock + originBlockX) * (double) DoomConstants.DOOM_TO_MC_SCALE + DoomConstants.DOOM_TO_MC_SCALE / 2.0;
    }

    private double toDoomZ(double worldZ) {
        double relBlock = worldZ - buildOrigin.getZ();
        return (originBlockZ - relBlock) * (double) DoomConstants.DOOM_TO_MC_SCALE + DoomConstants.DOOM_TO_MC_SCALE / 2.0;
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

    public static final class SectorNode {
        public final int tag;
        public final List<Vertex> polygon;
        public final double minX;
        public final double maxX;
        public final double minZ;
        public final double maxZ;
        public final int[] soundNeighbors;

        public SectorNode(int tag, List<Vertex> polygon, int[] soundNeighbors) {
            this.tag = tag;
            this.polygon = polygon;
            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (Vertex v : polygon) {
                minX = Math.min(minX, v.x());
                maxX = Math.max(maxX, v.x());
                minZ = Math.min(minZ, v.y());
                maxZ = Math.max(maxZ, v.y());
            }
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.soundNeighbors = soundNeighbors;
        }
    }
}
