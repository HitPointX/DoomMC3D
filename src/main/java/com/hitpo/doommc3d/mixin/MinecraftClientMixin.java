package com.hitpo.doommc3d.mixin;

import com.hitpo.doommc3d.client.weapon.DoomWeaponClientInput;
import com.hitpo.doommc3d.item.ModItems;
import com.hitpo.doommc3d.net.UseDoomDoorPayload;
import com.hitpo.doommc3d.net.UseDoomLinePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    private static boolean doommc3d$isDoomWeapon(PlayerEntity player) {
        var stack = player.getMainHandStack();
        return stack.isOf(ModItems.DOOM_PISTOL)
            || stack.isOf(ModItems.DOOM_SHOTGUN)
            || stack.isOf(ModItems.DOOM_CHAINGUN)
            || stack.isOf(ModItems.DOOM_ROCKET_LAUNCHER)
            || stack.isOf(ModItems.DOOM_PLASMA_RIFLE)
            || stack.isOf(ModItems.DOOM_BFG);
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("resource")
    private void doommc3d$doAttack(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        PlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        if (!doommc3d$isDoomWeapon(player)) {
            return;
        }

        // Cancel vanilla attack; Doom firing is handled on the client tick so holding works.
        DoomWeaponClientInput.queueFireRequest();
        cir.setReturnValue(true);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("resource")
    private void doommc3d$doItemUse(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        PlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        if (!doommc3d$isDoomWeapon(player)) {
            return;
        }

        // Doom UX: right-click should be able to "use" doors even while a weapon is equipped.
        HitResult hit = client.crosshairTarget;
        if (hit instanceof BlockHitResult bhr) {
            if (client.world != null && client.world.getBlockState(bhr.getBlockPos()).isOf(Blocks.IRON_DOOR)) {
                ClientPlayNetworking.send(new UseDoomDoorPayload(bhr.getBlockPos()));
                ci.cancel();
                return;
            }
        }

        // If not looking at a placed lever/door block, fall back to using special lines.
        ClientPlayNetworking.send(new UseDoomLinePayload());
        ci.cancel();
    }
}

