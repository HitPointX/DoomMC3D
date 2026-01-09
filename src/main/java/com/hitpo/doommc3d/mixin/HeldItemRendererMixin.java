package com.hitpo.doommc3d.mixin;

import com.hitpo.doommc3d.item.ModItems;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    private static boolean isDoomWeapon(ItemStack stack) {
        return stack.isOf(ModItems.DOOM_PISTOL)
            || stack.isOf(ModItems.DOOM_SHOTGUN)
            || stack.isOf(ModItems.DOOM_CHAINGUN)
            || stack.isOf(ModItems.DOOM_ROCKET_LAUNCHER)
            || stack.isOf(ModItems.DOOM_PLASMA_RIFLE)
            || stack.isOf(ModItems.DOOM_BFG);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void doommc3d$hideHandAndItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                                         ItemStack stack, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                         CallbackInfo ci) {
        if (isDoomWeapon(player.getMainHandStack())) {
            ci.cancel();
        }
    }
}
