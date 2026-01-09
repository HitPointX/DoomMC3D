package com.hitpo.doommc3d.mixin;

import com.hitpo.doommc3d.client.weapon.DoomWeaponClientScroll;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void doommc3d$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (DoomWeaponClientScroll.handleScroll(client, vertical)) {
            ci.cancel();
        }
    }
}

