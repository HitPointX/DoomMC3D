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
import com.hitpo.doommc3d.interact.DoomTriggerRegistry;
import com.hitpo.doommc3d.interact.DoorKeyColor;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashSet;
import java.util.Set;

public final class DoorPlacer {
    private DoorPlacer() {
    }

    public static void placeDoors(ServerWorld world, DoomMap map, DoomOrigin origin, BlockPos buildOrigin) {
        DoomDoorRegistry.clear(world);
        Set<BlockPos> placedDoors = new HashSet<>();
        Vertex[] vertices = map.vertices();
        Sidedef[] sidedefs = map.sidedefs();
        Sector[] sectors = map.sectors();
        for (Linedef linedef : map.linedefs()) {
            DoorKeyColor key = keyForSpecial(linedef.specialType());
            if (key == null && !isDoorSpecial(linedef.specialType())) {
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
            BlockPos doorPos = buildOrigin.add(x, baseY, z);

            // Some maps/ports can mark multiple lines that quantize to the same MC block.
            // Avoid stacking two doors/switches in the same position.
            if (!placedDoors.add(doorPos.toImmutable())) {
                continue;
            }

            placeIronDoor(world, doorPos, facing);

            // Doom door specials are often "open-wait-close"; keep it modest so doors don't feel annoying.
            int autoCloseTicks = defaultAutoCloseTicks(linedef.specialType());
            DoomDoorRegistry.register(world, doorPos, new DoomDoorInfo(key, linedef.sectorTag(), autoCloseTicks));
            if (key != null) {
                placeKeyFrame(world, doorPos, facing, key);
                placeSwitchesBothSides(world, doorPos, facing, key);
            }
            // Non-key doors are opened by right-clicking the door itself.
        }
    }

    private static int defaultAutoCloseTicks(int specialType) {
        // Conservative approximation: classic door specials are frequently timed.
        // We keep it short; doors can always be re-opened from either side.
        return switch (specialType) {
            case 1, 26, 27, 28 -> 80; // ~4s
            default -> 0;
        };
    }

    private static boolean isDoorSpecial(int specialType) {
        return specialType == 1 || (specialType >= 31 && specialType <= 34);
    }

    private static DoorKeyColor keyForSpecial(int specialType) {
        return switch (specialType) {
            case 26 -> DoorKeyColor.BLUE;
            case 27 -> DoorKeyColor.YELLOW;
            case 28 -> DoorKeyColor.RED;
            default -> null;
        };
    }

    private static int getSectorFromSide(Sidedef[] sidedefs, int sideIndex) {
        if (sideIndex < 0 || sideIndex >= sidedefs.length) {
            return -1;
        }
        return sidedefs[sideIndex].sector();
    }

    static Direction doorFacing(Vertex a, Vertex b) {
        int dx = Math.abs(b.x() - a.x());
        int dz = Math.abs(b.y() - a.y());
        return dx >= dz ? Direction.SOUTH : Direction.EAST;
    }

    static void placeIronDoor(ServerWorld world, BlockPos lower, Direction facing) {
        BlockPos upper = lower.up();
        world.setBlockState(lower, Blocks.AIR.getDefaultState(), 3);
        world.setBlockState(upper, Blocks.AIR.getDefaultState(), 3);

        BlockState lowerState = Blocks.IRON_DOOR.getDefaultState()
            .with(DoorBlock.FACING, facing)
            .with(DoorBlock.HINGE, DoorHinge.LEFT)
            .with(DoorBlock.HALF, DoubleBlockHalf.LOWER)
            .with(DoorBlock.OPEN, false);
        BlockState upperState = lowerState.with(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        world.setBlockState(lower, lowerState, 3);
        world.setBlockState(upper, upperState, 3);
    }

    private static void placeKeyFrame(ServerWorld world, BlockPos doorLower, Direction facing, DoorKeyColor key) {
        BlockState wool = switch (key) {
            case RED -> Blocks.RED_WOOL.getDefaultState();
            case YELLOW -> Blocks.YELLOW_WOOL.getDefaultState();
            case BLUE -> Blocks.BLUE_WOOL.getDefaultState();
        };
        Direction left = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();
        BlockPos upper = doorLower.up();
        world.setBlockState(doorLower.offset(left), wool, 3);
        world.setBlockState(doorLower.offset(right), wool, 3);
        world.setBlockState(upper.offset(left), wool, 3);
        world.setBlockState(upper.offset(right), wool, 3);
    }


    private static void placeSwitchesBothSides(ServerWorld world, BlockPos doorPos, Direction facing, DoorKeyColor key) {
        // Key doors keep switches for "keycard door" UX; place on both sides so the door is usable both ways.
        placeSwitchOnSide(world, doorPos, facing, facing.rotateYCounterclockwise(), key);
        placeSwitchOnSide(world, doorPos, facing, facing.rotateYClockwise(), key);
    }

    private static void placeSwitchOnSide(ServerWorld world, BlockPos doorPos, Direction facing, Direction side, DoorKeyColor key) {
        BlockPos framePos = doorPos.offset(side);
        BlockState base = switch (key) {
            case RED -> Blocks.RED_WOOL.getDefaultState();
            case YELLOW -> Blocks.YELLOW_WOOL.getDefaultState();
            case BLUE -> Blocks.BLUE_WOOL.getDefaultState();
        };
        world.setBlockState(framePos, base, 3);
        world.setBlockState(framePos.up(), base, 3);

        BlockPos leverPos = framePos.up().offset(facing);
        if (!world.getBlockState(leverPos).isAir()) {
            return;
        }
        BlockState leverState = Blocks.LEVER.getDefaultState()
            .with(HorizontalFacingBlock.FACING, facing)
            .with(WallMountedBlock.FACE, BlockFace.WALL)
            .with(LeverBlock.POWERED, false);
        world.setBlockState(leverPos, leverState, 3);

        // Key door switches are triggers; action targets the specific door block.
        DoomTriggerRegistry.registerUse(world, leverPos, new DoomTriggerInfo(new DoomTriggerAction.ToggleDoorAt(doorPos), false, 6));
    }
}
