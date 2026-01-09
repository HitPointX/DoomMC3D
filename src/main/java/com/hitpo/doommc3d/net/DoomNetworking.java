package com.hitpo.doommc3d.net;

import com.hitpo.doommc3d.worldgen.DoomHitscan;
import com.hitpo.doommc3d.interact.DoomLineTriggerSystem;
import com.hitpo.doommc3d.interact.DoomDoorInfo;
import com.hitpo.doommc3d.interact.DoomDoorLogic;
import com.hitpo.doommc3d.interact.DoomDoorRegistry;
import com.hitpo.doommc3d.interact.DoomTriggerInfo;
import com.hitpo.doommc3d.interact.DoomTriggerRegistry;
import com.hitpo.doommc3d.interact.DoomTriggerInteractions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class DoomNetworking {
    private DoomNetworking() {
    }

    public static void init() {
        PayloadTypeRegistry.playC2S().register(FireWeaponPayload.ID, FireWeaponPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UseDoomDoorPayload.ID, UseDoomDoorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UseDoomLinePayload.ID, UseDoomLinePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UseDoomTriggerPayload.ID, UseDoomTriggerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayMusicPayload.ID, PlayMusicPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayDoomSfxPayload.ID, PlayDoomSfxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PickupPayload.ID, PickupPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WeaponFiredPayload.ID, WeaponFiredPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(FireWeaponPayload.ID, (payload, context) -> DoomHitscan.firePlayerWeapon(context.player()));

        ServerPlayNetworking.registerGlobalReceiver(UseDoomLinePayload.ID, (payload, context) -> {
            ServerWorld world = context.player().getEntityWorld();
            DoomLineTriggerSystem.tryUseLine(world, context.player());
        });

        ServerPlayNetworking.registerGlobalReceiver(UseDoomTriggerPayload.ID, (payload, context) -> {
            ServerWorld world = context.player().getEntityWorld();
            var pos = payload.pos();
            DoomTriggerInfo info = DoomTriggerRegistry.getUse(world, pos);
            if (info == null) return;
            DoomTriggerInteractions.executeAction(world, context.player(), info.action());
        });

        ServerPlayNetworking.registerGlobalReceiver(UseDoomDoorPayload.ID, (payload, context) -> {
            ServerWorld world = context.player().getEntityWorld();
            BlockPos pos = payload.pos();
            BlockState state = world.getBlockState(pos);
            if (!state.isOf(Blocks.IRON_DOOR)) {
                return;
            }
            BlockPos lower = state.get(DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER ? pos : pos.down();
            DoomDoorInfo info = DoomDoorRegistry.get(world, lower);
            if (info == null) {
                return;
            }
            if (!DoomDoorLogic.canOpen(context.player(), info)) {
                context.player().sendMessage(Text.literal("[DoomMC3D] Door locked."), false);
                return;
            }
            DoomDoorLogic.toggleDoor(world, lower);
        });
    }
}
