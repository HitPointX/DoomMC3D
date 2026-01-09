package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.convert.DoomOrigin;
import com.hitpo.doommc3d.convert.DoomToMCScale;
import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Linedef;
import com.hitpo.doommc3d.doommap.Sector;
import com.hitpo.doommc3d.doommap.Sidedef;
import com.hitpo.doommc3d.doommap.Vertex;
import com.hitpo.doommc3d.interact.DoomDoorInfo;
import com.hitpo.doommc3d.interact.DoomDoorRegistry;
import com.hitpo.doommc3d.interact.DoomTriggerAction;
import com.hitpo.doommc3d.interact.DoomTriggerInfo;
import com.hitpo.doommc3d.interact.DoomWalkTriggerRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Classic Doom "event" triggers: walk-over linedefs that fire actions targeting sector tags.
 *
 * Doom doesn't have scripts; it has linedef specials.
 * The canonical behavior for these comes from the original engine (see Chocolate Doom's
 * p_spec.c / P_CrossSpecialLine table for which specials are walk-once vs retrigger).
 *
 * For now we implement the subset needed for Doom 1 "monster closet" moments:
 * - W1 Open Door (special 2)
 * - W1 Raise Door (special 4)
 * - WR Open Door (special 86)
 * - WR Raise Door (special 90)
 *
 * Mapping note:
 * In vanilla Doom these door actions operate on *sectors* selected by a tag.
 * Our Minecraft approximation uses iron doors placed at entrances of tagged sectors.
 */
public final class DoomEventTriggerPlacer {
    private static final Set<Integer> WALK_OPEN_DOOR = Set.of(2, 86);
    private static final Set<Integer> WALK_RAISE_DOOR = Set.of(4, 90);

    private static final AtomicInteger WALK_GROUP_ID = new AtomicInteger(100000); // keep separate from lifts

    private DoomEventTriggerPlacer() {
    }

    public static void place(ServerWorld world, DoomMap map, DoomOrigin origin, BlockPos buildOrigin) {
        Set<Integer> tagsReferencedByDoorTriggers = new HashSet<>();
        for (Linedef line : map.linedefs()) {
            int special = line.specialType();
            if (!WALK_OPEN_DOOR.contains(special) && !WALK_RAISE_DOOR.contains(special)) {
                continue;
            }
            int tag = line.sectorTag();
            if (tag != 0) {
                tagsReferencedByDoorTriggers.add(tag);
            }
        }

        if (tagsReferencedByDoorTriggers.isEmpty()) {
            return;
        }

        // Ensure we have door blocks that represent the tagged "door sectors".
        // Doom maps often have door sectors with tag != 0 that are opened from a remote trigger line.
        placeDoorsForTaggedSectors(world, map, origin, buildOrigin, tagsReferencedByDoorTriggers);

        // Register the walk-over trigger lines.
        Vertex[] vertices = map.vertices();
        Sidedef[] sidedefs = map.sidedefs();
        Sector[] sectors = map.sectors();
        for (Linedef line : map.linedefs()) {
            int special = line.specialType();
            if (!WALK_OPEN_DOOR.contains(special) && !WALK_RAISE_DOOR.contains(special)) {
                continue;
            }
            int tag = line.sectorTag();
            if (tag == 0) {
                continue;
            }

            boolean once = (special == 2 || special == 4);

            // Doom has slightly different semantics between "open" and "raise", but in our
            // Minecraft-door approximation they both mean: open and stay open.
            DoomTriggerInfo info = new DoomTriggerInfo(new DoomTriggerAction.OpenDoorsByTag(tag), once, 10);

            registerWalkLineTrigger(world, origin, buildOrigin, line, vertices, sidedefs, sectors, info, once);
        }
    }

    private static void registerWalkLineTrigger(
        ServerWorld world,
        DoomOrigin origin,
        BlockPos buildOrigin,
        Linedef line,
        Vertex[] vertices,
        Sidedef[] sidedefs,
        Sector[] sectors,
        DoomTriggerInfo info,
        boolean once
    ) {
        Vertex a = vertices[line.startVertex()];
        Vertex b = vertices[line.endVertex()];
        int ax = DoomToMCScale.toBlock(a.x()) - origin.originBlockX();
        int az = origin.originBlockZ() - DoomToMCScale.toBlock(a.y());
        int bx = DoomToMCScale.toBlock(b.x()) - origin.originBlockX();
        int bz = origin.originBlockZ() - DoomToMCScale.toBlock(b.y());

        int rightSector = getSectorFromSide(sidedefs, line.rightSidedef());
        int leftSector = getSectorFromSide(sidedefs, line.leftSidedef());
        int floorRight = rightSector >= 0 ? DoomToMCScale.toBlock(sectors[rightSector].floorHeight()) : 0;
        int floorLeft = leftSector >= 0 ? DoomToMCScale.toBlock(sectors[leftSector].floorHeight()) : 0;
        int floorY = Math.min(floorRight, floorLeft);

        int groupId = WALK_GROUP_ID.getAndIncrement();
        for (int[] p : bresenham(ax, az, bx, bz)) {
            BlockPos pos = buildOrigin.add(p[0], floorY, p[1]);
            DoomWalkTriggerRegistry.register(world, pos, new DoomWalkTriggerRegistry.WalkTrigger(groupId, info, false));
        }

        if (once) {
            // Once-only semantics are handled in DoomWalkTriggerSystem by activating the group.
        }
    }

    private static void placeDoorsForTaggedSectors(ServerWorld world, DoomMap map, DoomOrigin origin, BlockPos buildOrigin, Set<Integer> tags) {
        Map<Integer, List<Integer>> tagToSectors = new HashMap<>();
        Sector[] sectors = map.sectors();
        for (int i = 0; i < sectors.length; i++) {
            int tag = sectors[i].tag();
            if (tag != 0 && tags.contains(tag)) {
                tagToSectors.computeIfAbsent(tag, k -> new ArrayList<>()).add(i);
            }
        }

        if (tagToSectors.isEmpty()) {
            return;
        }

        // For each tagged sector, place door blocks on the shortest 1-2 boundary lines.
        // This is a heuristic to find the "doorway" of a door sector.
        Vertex[] vertices = map.vertices();
        Sidedef[] sidedefs = map.sidedefs();

        for (Map.Entry<Integer, List<Integer>> entry : tagToSectors.entrySet()) {
            int tag = entry.getKey();
            for (int sectorIndex : entry.getValue()) {
                List<DoorCandidate> candidates = new ArrayList<>();
                for (Linedef line : map.linedefs()) {
                    int rightSector = getSectorFromSide(sidedefs, line.rightSidedef());
                    int leftSector = getSectorFromSide(sidedefs, line.leftSidedef());

                    if (rightSector == sectorIndex && leftSector >= 0 && leftSector != sectorIndex) {
                        candidates.add(new DoorCandidate(line, lengthSquared(vertices, line)));
                    } else if (leftSector == sectorIndex && rightSector >= 0 && rightSector != sectorIndex) {
                        candidates.add(new DoorCandidate(line, lengthSquared(vertices, line)));
                    }
                }

                candidates.sort(Comparator.comparingInt(DoorCandidate::lenSq));
                int placed = 0;
                for (DoorCandidate c : candidates) {
                    if (placed >= 2) {
                        break;
                    }

                    Linedef line = c.line;
                    Vertex a = vertices[line.startVertex()];
                    Vertex b = vertices[line.endVertex()];

                    int midDoomX = (a.x() + b.x()) / 2;
                    int midDoomZ = (a.y() + b.y()) / 2;
                    int x = DoomToMCScale.toBlock(midDoomX) - origin.originBlockX();
                    int z = origin.originBlockZ() - DoomToMCScale.toBlock(midDoomZ);

                    // Use the min adjacent floor as the "walking level", door sits at +1.
                    int rightSector = getSectorFromSide(sidedefs, line.rightSidedef());
                    int leftSector = getSectorFromSide(sidedefs, line.leftSidedef());
                    int floorRight = rightSector >= 0 ? DoomToMCScale.toBlock(sectors[rightSector].floorHeight()) : 0;
                    int floorLeft = leftSector >= 0 ? DoomToMCScale.toBlock(sectors[leftSector].floorHeight()) : 0;
                    int baseY = Math.min(floorRight, floorLeft) + 1;

                    BlockPos lower = buildOrigin.add(x, baseY, z);

                    // Don't stomp an existing placed door.
                    BlockState state = world.getBlockState(lower);
                    if (state.getBlock() instanceof DoorBlock) {
                        // Ensure it's registered with the right tag if possible.
                        DoomDoorInfo existing = DoomDoorRegistry.get(world, lower);
                        if (existing == null || existing.tag() == 0) {
                            DoomDoorRegistry.register(world, lower, new DoomDoorInfo(null, tag, 0));
                        }
                        placed++;
                        continue;
                    }

                    // Place a simple iron door as the door sector "front".
                    DoorPlacer.placeIronDoor(world, lower, DoorPlacer.doorFacing(a, b));
                    DoomDoorRegistry.register(world, lower, new DoomDoorInfo(null, tag, 0));
                    placed++;
                }
            }
        }
    }

    private static int lengthSquared(Vertex[] vertices, Linedef line) {
        Vertex a = vertices[line.startVertex()];
        Vertex b = vertices[line.endVertex()];
        int dx = b.x() - a.x();
        int dz = b.y() - a.y();
        return dx * dx + dz * dz;
    }

    private record DoorCandidate(Linedef line, int lenSq) {
    }

    private static int getSectorFromSide(Sidedef[] sidedefs, int sideIndex) {
        if (sideIndex < 0 || sideIndex >= sidedefs.length) {
            return -1;
        }
        return sidedefs[sideIndex].sector();
    }

    private static List<int[]> bresenham(int x0, int y0, int x1, int y1) {
        List<int[]> points = new ArrayList<>();
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int x = x0;
        int y = y0;
        while (true) {
            points.add(new int[] {x, y});
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
        return points;
    }
}
