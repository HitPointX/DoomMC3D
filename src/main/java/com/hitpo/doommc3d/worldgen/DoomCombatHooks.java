package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.item.ModItems;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

public final class DoomCombatHooks {
    private DoomCombatHooks() {
    }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity) || world.isClient()) {
                return ActionResult.PASS;
            }
            if (player.getCommandTags().contains("doommc3d_active") && player.getMainHandStack().isOf(ModItems.DOOM_PISTOL)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!(player instanceof ServerPlayerEntity) || world.isClient()) {
                return ActionResult.PASS;
            }
            if (player.getCommandTags().contains("doommc3d_active") && player.getMainHandStack().isOf(ModItems.DOOM_PISTOL)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }
}

