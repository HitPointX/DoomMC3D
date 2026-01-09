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
            if (info != null) {
                DoomDoorInfo doorInfo = DoomDoorRegistry.get((ServerWorld) world, info.doorPos());
                if (!DoomDoorLogic.canOpen(player, doorInfo)) {
                    player.sendMessage(Text.literal("[DoomMC3D] Door locked."), false);
                    return ActionResult.FAIL;
                }
                DoomDoorLogic.toggleDoor((ServerWorld) world, info.doorPos());
                return ActionResult.SUCCESS;
            }

            // Fallback: some switches are registered as Doom triggers (use switches).
            DoomTriggerInfo trig = DoomTriggerRegistry.getUse((ServerWorld) world, pos);
            if (trig == null) {
                return ActionResult.PASS;
            }

            // Delegate to the generic trigger executor which handles door/tag/lift/exit actions.
            DoomTriggerInteractions.executeAction((ServerWorld) world, (net.minecraft.server.network.ServerPlayerEntity) player, trig.action());
            return ActionResult.SUCCESS;
        });
    }
}

