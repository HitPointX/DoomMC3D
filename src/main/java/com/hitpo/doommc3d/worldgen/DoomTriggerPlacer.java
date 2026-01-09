package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.convert.DoomOrigin;
import com.hitpo.doommc3d.convert.DoomToMCScale;
import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Linedef;
import com.hitpo.doommc3d.doommap.Sector;
import com.hitpo.doommc3d.doommap.Sidedef;
import com.hitpo.doommc3d.doommap.Vertex;
import com.hitpo.doommc3d.interact.DoomTriggerAction;
import com.hitpo.doommc3d.interact.DoomTriggerInfo;
import com.hitpo.doommc3d.interact.DoomTriggerRegistry;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class DoomTriggerPlacer {
    // Classic Doom exits (including repeatable switch/walk variants):
    // 11 = S1 Exit, 52 = W1 Exit, 51 = S1 Secret Exit, 197 = SR Exit, 198 = WR Exit.
    private static final Set<Integer> EXIT_SPECIALS = Set.of(11, 52, 51, 197, 198);

    private DoomTriggerPlacer() {
    }

    public static void place(ServerWorld world, DoomMap map, DoomOrigin origin, BlockPos buildOrigin) {
        DoomTriggerRegistry.clear(world);

        Vertex[] vertices = map.vertices();
        Sidedef[] sidedefs = map.sidedefs();
        Sector[] sectors = map.sectors();
        Set<BlockPos> placed = new HashSet<>();

        for (Linedef linedef : map.linedefs()) {
            int special = linedef.specialType();
            if (!EXIT_SPECIALS.contains(special)) {
                continue;
            }

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
            if (!placed.add(triggerBase.toImmutable())) {
                continue;
            }

            // Place a "button" (lever) on both sides so it's usable regardless of approach.
            placeExitSwitch(world, triggerBase, facing, facing.rotateYCounterclockwise());
            placeExitSwitch(world, triggerBase, facing, facing.rotateYClockwise());
        }
    }

    private static void placeExitSwitch(ServerWorld world, BlockPos base, Direction facing, Direction side) {
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

        DoomTriggerRegistry.registerUse(world, leverPos, new DoomTriggerInfo(new DoomTriggerAction.ExitNextMap(), true, 10));
        com.hitpo.doommc3d.util.DebugLogger.debug("DoomTriggerPlacer", () -> "[DoomMC3D] Placed exit lever at " + leverPos + " facing " + facing);
    }

    private static int getSectorFromSide(Sidedef[] sidedefs, int sideIndex) {
        if (sideIndex < 0 || sideIndex >= sidedefs.length) {
            return -1;
        }
        return sidedefs[sideIndex].sector();
    }

    private static Direction doorFacing(Vertex a, Vertex b) {
        int dx = Math.abs(b.x() - a.x());
        int dz = Math.abs(b.y() - a.y());
        return dx >= dz ? Direction.SOUTH : Direction.EAST;
    }
}
