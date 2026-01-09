package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.convert.DoomOrigin;
import com.hitpo.doommc3d.convert.DoomToMCScale;
import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Linedef;
import com.hitpo.doommc3d.doommap.Sector;
import com.hitpo.doommc3d.doommap.Sidedef;
import com.hitpo.doommc3d.doommap.Vertex;
import com.hitpo.doommc3d.interact.DoomLiftSystem;
import com.hitpo.doommc3d.interact.DoomTriggerAction;
import com.hitpo.doommc3d.interact.DoomTriggerInfo;
import com.hitpo.doommc3d.interact.DoomTriggerRegistry;
import com.hitpo.doommc3d.interact.DoomWalkTriggerRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Places Doom lift triggers and registers lift sector movers.
 *
 * Supported classic Doom specials:
 * - Walk-over: 10 (W1), 88 (WR), 120 (WR blazing), 121 (W1 blazing)
 * - Switch/button use: 21 (S1), 62 (SR), 122 (S1 blazing), 123 (SR blazing)
 */
public final class DoomLiftPlacer {
    private static final Set<Integer> WALK_LIFT_SPECIALS = Set.of(10, 88, 120, 121);
    private static final Set<Integer> USE_LIFT_SPECIALS = Set.of(21, 62, 122, 123);

    private static final AtomicInteger WALK_GROUP_ID = new AtomicInteger(1);

    private DoomLiftPlacer() {
    }

    public static void place(ServerWorld world, DoomMap map, DoomOrigin origin, BlockPos buildOrigin) {
        DoomLiftSystem.clear(world);
        DoomWalkTriggerRegistry.clear(world);

        Map<Integer, List<Integer>> tagToSectors = buildTagToSectors(map.sectors());
        Vertex[] vertices = map.vertices();
        Sidedef[] sidedefs = map.sidedefs();
        Sector[] sectors = map.sectors();

        // 1) Register lifts for any tag referenced by a lift special.
        Set<Integer> tagsUsedByLifts = new HashSet<>();
        for (Linedef line : map.linedefs()) {
            int special = line.specialType();
            if (!WALK_LIFT_SPECIALS.contains(special) && !USE_LIFT_SPECIALS.contains(special)) {
                continue;
            }
            int tag = line.sectorTag();
            if (tag != 0) {
                tagsUsedByLifts.add(tag);
            }
        }

        for (int tag : tagsUsedByLifts) {
            List<Integer> sectorIndices = tagToSectors.get(tag);
            if (sectorIndices == null || sectorIndices.isEmpty()) {
                continue;
            }
            for (int sectorIndex : sectorIndices) {
                DoomLiftSystem.Lift lift = buildLiftForSector(map, sectorIndex, origin, buildOrigin);
                if (lift != null) {
                    DoomLiftSystem.registerLift(world, tag, lift);
                }
            }
        }

        // 2) Place triggers (walk lines + use switches).
        for (Linedef line : map.linedefs()) {
            int special = line.specialType();
            int tag = line.sectorTag();
            if (tag == 0) {
                continue;
            }

            if (WALK_LIFT_SPECIALS.contains(special)) {
                boolean once = (special == 10 || special == 121);
                int groupId = WALK_GROUP_ID.getAndIncrement();
                registerWalkLineTrigger(world, map, origin, buildOrigin, line, vertices, sidedefs, sectors, groupId, tag, once);
                continue;
            }

            if (USE_LIFT_SPECIALS.contains(special)) {
                boolean once = (special == 21 || special == 122);
                int cooldown = 10;
                placeUseSwitchTrigger(world, origin, buildOrigin, line, vertices, sidedefs, sectors, tag, once, cooldown);
            }
        }
    }

    private static DoomLiftSystem.Lift buildLiftForSector(DoomMap map, int sectorIndex, DoomOrigin origin, BlockPos buildOrigin) {
        Sector sector = map.sectors()[sectorIndex];
        List<Vertex> polygon = ThingPlacerSectorGeometry.buildSectorPolygon(map, sectorIndex);
        if (polygon.isEmpty()) {
            return null;
        }

        int minX = polygon.stream().mapToInt(Vertex::x).min().orElse(0);
        int maxX = polygon.stream().mapToInt(Vertex::x).max().orElse(0);
        int minZ = polygon.stream().mapToInt(Vertex::y).min().orElse(0);
        int maxZ = polygon.stream().mapToInt(Vertex::y).max().orElse(0);

        int originBlockX = origin.originBlockX();
        int originBlockZ = origin.originBlockZ();

        int startX = DoomToMCScale.toBlock(minX) - originBlockX;
        int endX = DoomToMCScale.toBlock(maxX) - originBlockX;
        int startZ = originBlockZ - DoomToMCScale.toBlock(maxZ);
        int endZ = originBlockZ - DoomToMCScale.toBlock(minZ);

        int topY = DoomToMCScale.toBlock(sector.floorHeight());
        int ceilingY = DoomToMCScale.toBlock(sector.ceilingHeight());

        // Determine bottomY = lowest neighbor floor around this sector.
        int bottomY = computeLowestNeighborFloorY(map, sectorIndex);
        if (bottomY < 0) {
            bottomY = topY;
        }

        List<DoomLiftSystem.FloorCell> floorCells = new ArrayList<>();
        for (int blockX = startX; blockX <= endX; blockX++) {
            for (int blockZ = startZ; blockZ <= endZ; blockZ++) {
                double doomX = toDoomX(blockX, originBlockX);
                double doomZ = toDoomZ(blockZ, originBlockZ);
                if (ThingPlacerSectorGeometry.containsPoint(polygon, doomX, doomZ)) {
                    floorCells.add(new DoomLiftSystem.FloorCell(blockX, blockZ));
                }
            }
        }

        if (floorCells.isEmpty()) {
            return null;
        }

        List<DoomLiftSystem.BoundaryColumn> boundaryColumns = buildBoundaryColumns(map, sectorIndex, origin);

        BlockState floorState = DoomLiftSystem.Lift.floorStateFromFlat(sector.floorTexture());
        
        // Use lift metal texture theme if not explicitly mapped
        if (floorState == null || floorState.getBlock() == Blocks.DEEPSLATE_TILES) {
            floorState = selectLiftFloorTexture(sector.floorTexture());
        }

        // Blazing variants exist; we treat them as faster speed if the map uses the blazing plat specials.
        DoomLiftSystem.LiftSpeed speed = DoomLiftSystem.LiftSpeed.NORMAL;

        return new DoomLiftSystem.Lift(
            floorCells,
            boundaryColumns,
            polygon,
            originBlockX,
            originBlockZ,
            buildOrigin,
            floorState,
            ceilingY,
            topY,
            bottomY,
            speed
        );
    }

    /**
     * Select a lift-appropriate floor texture with industrial metal theme.
     * Doom uses various metal flats for platforms: LIFTTECH, METALFLR, etc.
     */
    private static BlockState selectLiftFloorTexture(String flat) {
        if (flat == null) {
            return Blocks.IRON_BLOCK.getDefaultState();
        }
        
        String upper = flat.toUpperCase();
        
        // Prefer iron/metal blocks for lift theme
        if (upper.contains("LIFT") || upper.contains("METAL") || upper.contains("TECH")) {
            return Blocks.IRON_BLOCK.getDefaultState();
        }
        if (upper.contains("BROWN") || upper.contains("BRICK")) {
            return Blocks.DARK_OAK_WOOD.getDefaultState(); // Brownstone variant
        }
        if (upper.contains("GRAY") || upper.contains("CONCRETE")) {
            return Blocks.POLISHED_DEEPSLATE.getDefaultState();
        }
        
        // Default to industrial iron for lifts
        return Blocks.IRON_BLOCK.getDefaultState();
    }

    private static List<DoomLiftSystem.BoundaryColumn> buildBoundaryColumns(DoomMap map, int sectorIndex, DoomOrigin origin) {
        List<DoomLiftSystem.BoundaryColumn> out = new ArrayList<>();
        Linedef[] linedefs = map.linedefs();
        Sidedef[] sidedefs = map.sidedefs();
        Sector[] sectors = map.sectors();
        Vertex[] vertices = map.vertices();

        int originBlockX = origin.originBlockX();
        int originBlockZ = origin.originBlockZ();

        for (Linedef linedef : linedefs) {
            WallSlice slice = buildWallSliceForSector(linedef, sidedefs, sectorIndex);
            if (slice == null) {
                continue;
            }

            Vertex start = vertices[slice.startVertex];
            Vertex end = vertices[slice.endVertex];
            int startX = DoomToMCScale.toBlock(start.x()) - originBlockX;
            int startZ = originBlockZ - DoomToMCScale.toBlock(start.y());
            int endX = DoomToMCScale.toBlock(end.x()) - originBlockX;
            int endZ = originBlockZ - DoomToMCScale.toBlock(end.y());

            int neighborFloorY = 0;
            if (slice.neighborSector >= 0) {
                neighborFloorY = DoomToMCScale.toBlock(sectors[slice.neighborSector].floorHeight());
            }

            int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
            for (int step = 0; step <= steps; step++) {
                int x = steps == 0 ? startX : startX + Math.round((endX - startX) * (step / (float) steps));
                int z = steps == 0 ? startZ : startZ + Math.round((endZ - startZ) * (step / (float) steps));
                out.add(new DoomLiftSystem.BoundaryColumn(x, z, neighborFloorY, slice.wallState));
            }
        }

        return out;
    }

    private static WallSlice buildWallSliceForSector(Linedef linedef, Sidedef[] sidedefs, int sectorIndex) {
        Sidedef right = safeSidedef(sidedefs, linedef.rightSidedef());
        if (right != null && right.sector() == sectorIndex) {
            int neighbor = getSectorFromSide(sidedefs, linedef.leftSidedef());
            return new WallSlice(linedef.startVertex(), linedef.endVertex(), neighbor, DoomLiftSystem.Lift.wallStateFromTexture(right.middleTexture()));
        }
        Sidedef left = safeSidedef(sidedefs, linedef.leftSidedef());
        if (left != null && left.sector() == sectorIndex) {
            int neighbor = getSectorFromSide(sidedefs, linedef.rightSidedef());
            return new WallSlice(linedef.endVertex(), linedef.startVertex(), neighbor, DoomLiftSystem.Lift.wallStateFromTexture(left.middleTexture()));
        }
        return null;
    }

    private static Sidedef safeSidedef(Sidedef[] sidedefs, int index) {
        if (index < 0 || index >= sidedefs.length) {
            return null;
        }
        return sidedefs[index];
    }

    private static int getSectorFromSide(Sidedef[] sidedefs, int sideIndex) {
        if (sideIndex < 0 || sideIndex >= sidedefs.length) {
            return -1;
        }
        return sidedefs[sideIndex].sector();
    }

    private record WallSlice(int startVertex, int endVertex, int neighborSector, BlockState wallState) {
    }

    private static int computeLowestNeighborFloorY(DoomMap map, int sectorIndex) {
        Linedef[] linedefs = map.linedefs();
        Sidedef[] sidedefs = map.sidedefs();
        Sector[] sectors = map.sectors();

        int lowest = Integer.MAX_VALUE;
        boolean found = false;

        for (Linedef linedef : linedefs) {
            int rightSector = getSectorFromSide(sidedefs, linedef.rightSidedef());
            int leftSector = getSectorFromSide(sidedefs, linedef.leftSidedef());

            if (rightSector == sectorIndex && leftSector >= 0) {
                int y = DoomToMCScale.toBlock(sectors[leftSector].floorHeight());
                lowest = Math.min(lowest, y);
                found = true;
            } else if (leftSector == sectorIndex && rightSector >= 0) {
                int y = DoomToMCScale.toBlock(sectors[rightSector].floorHeight());
                lowest = Math.min(lowest, y);
                found = true;
            }
        }

        return found ? lowest : -1;
    }

    private static Map<Integer, List<Integer>> buildTagToSectors(Sector[] sectors) {
        Map<Integer, List<Integer>> out = new HashMap<>();
        for (int i = 0; i < sectors.length; i++) {
            int tag = sectors[i].tag();
            if (tag == 0) {
                continue;
            }
            out.computeIfAbsent(tag, k -> new ArrayList<>()).add(i);
        }
        return out;
    }

    private static void registerWalkLineTrigger(
        ServerWorld world,
        DoomMap map,
        DoomOrigin origin,
        BlockPos buildOrigin,
        Linedef line,
        Vertex[] vertices,
        Sidedef[] sidedefs,
        Sector[] sectors,
        int groupId,
        int tag,
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

        DoomTriggerInfo info = new DoomTriggerInfo(new DoomTriggerAction.ActivateLiftByTag(tag), once, 10);

        int cellCount = 0;
        for (int[] p : bresenham(ax, az, bx, bz)) {
            BlockPos pos = buildOrigin.add(p[0], floorY, p[1]);
            DoomWalkTriggerRegistry.register(world, pos, new DoomWalkTriggerRegistry.WalkTrigger(groupId, info, false));
            cellCount++;
        }
        final String regMsg = "[DoomMC3D] Registered walk lift trigger: tag=" + tag + " groupId=" + groupId + " once=" + once + " cells=" + cellCount + " floorY=" + floorY;
        com.hitpo.doommc3d.util.DebugLogger.debug("DoomLiftPlacer", () -> regMsg);
    }

    private static void placeUseSwitchTrigger(
        ServerWorld world,
        DoomOrigin origin,
        BlockPos buildOrigin,
        Linedef linedef,
        Vertex[] vertices,
        Sidedef[] sidedefs,
        Sector[] sectors,
        int tag,
        boolean once,
        int cooldown
    ) {
        Vertex a = vertices[linedef.startVertex()];
        Vertex b = vertices[linedef.endVertex()];
        int midDoomX = (a.x() + b.x()) / 2;
        int midDoomZ = (a.y() + b.y()) / 2;
        int x = DoomToMCScale.toBlock(midDoomX) - origin.originBlockX();
        int z = origin.originBlockZ() - DoomToMCScale.toBlock(midDoomZ);

        int rightSector = getSectorFromSide(sidedefs, linedef.rightSidedef());
        int leftSector = getSectorFromSide(sidedefs, linedef.leftSidedef());
        int floorRight = rightSector >= 0 ? DoomToMCScale.toBlock(sectors[rightSector].floorHeight()) : 0;
        int floorLeft = leftSector >= 0 ? DoomToMCScale.toBlock(sectors[leftSector].floorHeight()) : 0;
        int baseY = Math.min(floorRight, floorLeft) + 1;

        Direction facing = doorFacing(a, b);
        BlockPos triggerBase = buildOrigin.add(x, baseY, z);

        placeSwitch(world, triggerBase, facing, facing.rotateYCounterclockwise(), tag, once, cooldown);
        placeSwitch(world, triggerBase, facing, facing.rotateYClockwise(), tag, once, cooldown);
    }

    private static void placeSwitch(ServerWorld world, BlockPos base, Direction facing, Direction side, int tag, boolean once, int cooldown) {
        BlockPos framePos = base.offset(side);
        world.setBlockState(framePos, Blocks.STONE.getDefaultState(), 3);
        world.setBlockState(framePos.up(), Blocks.STONE.getDefaultState(), 3);

        BlockPos leverPos = framePos.up().offset(facing);
        if (!world.getBlockState(leverPos).isAir()) {
            return;
        }

        BlockState leverState = Blocks.LEVER.getDefaultState()
            .with(HorizontalFacingBlock.FACING, facing)
            .with(WallMountedBlock.FACE, BlockFace.WALL)
            .with(LeverBlock.POWERED, false);
        world.setBlockState(leverPos, leverState, 3);

        DoomTriggerRegistry.registerUse(world, leverPos, new DoomTriggerInfo(new DoomTriggerAction.ActivateLiftByTag(tag), once, cooldown));
    }

    private static Direction doorFacing(Vertex a, Vertex b) {
        int dx = Math.abs(b.x() - a.x());
        int dz = Math.abs(b.y() - a.y());
        return dx >= dz ? Direction.SOUTH : Direction.EAST;
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

    private static double toDoomX(int blockX, int originBlockX) {
        return (blockX + originBlockX) * (double) com.hitpo.doommc3d.DoomConstants.DOOM_TO_MC_SCALE
            + com.hitpo.doommc3d.DoomConstants.DOOM_TO_MC_SCALE / 2.0;
    }

    private static double toDoomZ(int blockZ, int originBlockZ) {
        return (originBlockZ - blockZ) * (double) com.hitpo.doommc3d.DoomConstants.DOOM_TO_MC_SCALE
            + com.hitpo.doommc3d.DoomConstants.DOOM_TO_MC_SCALE / 2.0;
    }
}
