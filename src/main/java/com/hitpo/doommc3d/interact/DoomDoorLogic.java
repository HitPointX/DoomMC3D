package com.hitpo.doommc3d.interact;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Direction;

public final class DoomDoorLogic {
    private DoomDoorLogic() {
    }

    /**
     * Opens a Doom-placed door and leaves it open.
     *
     * Doom's tag-driven door events ("open door") typically do not toggle shut.
     */
    public static void openDoor(ServerWorld world, BlockPos clickedPos) {
        BlockState clickedState = world.getBlockState(clickedPos);
        if (!(clickedState.getBlock() instanceof DoorBlock)) {
            return;
        }
        BlockPos lower = clickedState.get(DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER ? clickedPos : clickedPos.down();
        BlockPos upper = lower.up();
        BlockState lowerState = world.getBlockState(lower);
        BlockState upperState = world.getBlockState(upper);
        if (!(lowerState.getBlock() instanceof DoorBlock) || !(upperState.getBlock() instanceof DoorBlock)) {
            return;
        }
        if (lowerState.get(DoorBlock.OPEN)) {
            return;
        }
        world.setBlockState(lower, lowerState.with(DoorBlock.OPEN, true), 3);
        world.setBlockState(upper, upperState.with(DoorBlock.OPEN, true), 3);
    }

    public static boolean canOpen(PlayerEntity player, DoomDoorInfo info) {
        if (info == null || !info.requiresKey()) {
            return true;
        }
        String requiredTag = switch (info.keyColor()) {
            case RED -> "doommc3d_key_red";
            case YELLOW -> "doommc3d_key_yellow";
            case BLUE -> "doommc3d_key_blue";
        };
        return player.getCommandTags().contains(requiredTag);
    }

    public static void toggleDoor(ServerWorld world, BlockPos clickedPos) {
        BlockState clickedState = world.getBlockState(clickedPos);
        if (!(clickedState.getBlock() instanceof DoorBlock)) {
            return;
        }
        BlockPos lower = clickedState.get(DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER ? clickedPos : clickedPos.down();
        BlockPos upper = lower.up();
        BlockState lowerState = world.getBlockState(lower);
        BlockState upperState = world.getBlockState(upper);
        if (!(lowerState.getBlock() instanceof DoorBlock) || !(upperState.getBlock() instanceof DoorBlock)) {
            return;
        }

        boolean open = lowerState.get(DoorBlock.OPEN);
        boolean newOpen = !open;
        world.setBlockState(lower, lowerState.with(DoorBlock.OPEN, newOpen), 3);
        world.setBlockState(upper, upperState.with(DoorBlock.OPEN, newOpen), 3);

        DoomDoorInfo info = DoomDoorRegistry.get(world, lower);
        if (newOpen && info != null && info.autoCloseTicks() > 0) {
            int delay = info.autoCloseTicks();
            DoomScheduler.schedule(world, delay, () -> {
                BlockState ls = world.getBlockState(lower);
                BlockState us = world.getBlockState(lower.up());
                if (!(ls.getBlock() instanceof DoorBlock) || !(us.getBlock() instanceof DoorBlock)) {
                    return;
                }
                if (!ls.get(DoorBlock.OPEN)) {
                    return;
                }
                world.setBlockState(lower, ls.with(DoorBlock.OPEN, false), 3);
                world.setBlockState(lower.up(), us.with(DoorBlock.OPEN, false), 3);
            });
        }

        // If there are adjacent Doom-placed doors (double-doors side-by-side OR two doors
        // back-to-back in a 1-block corridor), toggle them together.
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborLower = lower.offset(dir);
            BlockState neighborState = world.getBlockState(neighborLower);
            if (!(neighborState.getBlock() instanceof DoorBlock)) {
                continue;
            }
            if (neighborState.get(DoorBlock.HALF) != net.minecraft.block.enums.DoubleBlockHalf.LOWER) {
                continue;
            }

            // Only consider Doom-registered doors.
            if (DoomDoorRegistry.get(world, neighborLower) == null) {
                continue;
            }

            // Only sync doors that are currently in the same state we just toggled from.
            if (neighborState.get(DoorBlock.OPEN) != open) {
                continue;
            }

            // Also require a plausible orientation match: same facing or opposite facing.
            Direction facing = lowerState.get(DoorBlock.FACING);
            Direction neighborFacing = neighborState.get(DoorBlock.FACING);
            if (neighborFacing != facing && neighborFacing != facing.getOpposite()) {
                continue;
            }

            BlockPos neighborUpper = neighborLower.up();
            BlockState neighborUpperState = world.getBlockState(neighborUpper);
            if (!(neighborUpperState.getBlock() instanceof DoorBlock)) {
                continue;
            }
            world.setBlockState(neighborLower, neighborState.with(DoorBlock.OPEN, newOpen), 3);
            world.setBlockState(neighborUpper, neighborUpperState.with(DoorBlock.OPEN, newOpen), 3);
        }
    }
}
