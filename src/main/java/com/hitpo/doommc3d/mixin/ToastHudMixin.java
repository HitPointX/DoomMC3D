package com.hitpo.doommc3d.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.toast.ToastHud")
public abstract class ToastHudMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void doommc3d$suppressTutorialToasts(Object toast, CallbackInfoReturnable cir) {
        // Avoid direct compile-time dependency on client toast classes by checking the
        // runtime simple class name for the TutorialToast produced by vanilla.
        if (toast != null && "TutorialToast".equals(toast.getClass().getSimpleName())) {
            cir.cancel();
        }
    }
}
