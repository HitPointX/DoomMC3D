package com.hitpo.doommc3d.interact;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public final class DoomSwitchInteractions {
    private DoomSwitchInteractions() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);
            if (!state.isOf(Blocks.LEVER)) {
                return ActionResult.PASS;
            }
            DoomSwitchInfo info = DoomSwitchRegistry.get((ServerWorld) world, pos);
            if (info == null) {
                return ActionResult.PASS;
            }
            DoomDoorInfo doorInfo = DoomDoorRegistry.get((ServerWorld) world, info.doorPos());
            if (!DoomDoorLogic.canOpen(player, doorInfo)) {
                player.sendMessage(Text.literal("[DoomMC3D] Door locked."), false);
                return ActionResult.FAIL;
            }
            DoomDoorLogic.toggleDoor((ServerWorld) world, info.doorPos());
            return ActionResult.PASS;
        });
    }
}

