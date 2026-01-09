package com.hitpo.doommc3d.interact;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.PlayerEntity;

public final class DoomDoorInteractions {
    private DoomDoorInteractions() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);
            if (!state.isOf(Blocks.IRON_DOOR)) {
                return ActionResult.PASS;
            }
            BlockPos lower = state.get(DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER ? pos : pos.down();
            DoomDoorInfo info = DoomDoorRegistry.get((ServerWorld) world, lower);
            if (info == null) {
                return ActionResult.PASS;
            }
            if (!DoomDoorLogic.canOpen(player, info)) {
                player.sendMessage(Text.literal("[DoomMC3D] Door locked."), false);
                return ActionResult.FAIL;
            }
            DoomDoorLogic.toggleDoor((ServerWorld) world, lower);
            return ActionResult.SUCCESS;
        });
    }
}
