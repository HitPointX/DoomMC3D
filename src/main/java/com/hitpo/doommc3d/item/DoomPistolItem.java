package com.hitpo.doommc3d.item;

import com.hitpo.doommc3d.interact.DoomDoorInfo;
import com.hitpo.doommc3d.interact.DoomDoorLogic;
import com.hitpo.doommc3d.interact.DoomDoorRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public class DoomPistolItem extends Item {
    public DoomPistolItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient()) {
            return ActionResult.PASS;
        }
        BlockPos pos = context.getBlockPos();
        BlockState state = context.getWorld().getBlockState(pos);
        if (!state.isOf(Blocks.IRON_DOOR)) {
            return ActionResult.PASS;
        }
        BlockPos lower = state.get(DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER ? pos : pos.down();
        DoomDoorInfo info = DoomDoorRegistry.get((ServerWorld) context.getWorld(), lower);
        if (info == null) {
            return ActionResult.PASS;
        }
        if (!DoomDoorLogic.canOpen(context.getPlayer(), info)) {
            if (context.getPlayer() != null) {
                context.getPlayer().sendMessage(Text.literal("[DoomMC3D] Door locked."), false);
            }
            return ActionResult.FAIL;
        }
        DoomDoorLogic.toggleDoor((ServerWorld) context.getWorld(), lower);
        return ActionResult.SUCCESS;
    }
}
