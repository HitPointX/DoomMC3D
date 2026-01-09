package com.hitpo.doommc3d.mixin;

import net.minecraft.block.LeverBlock;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LeverBlock.class)
public class LeverUseMixin {
    @Inject(method = "use", at = @At("HEAD"))
    private void onUse(BlockState state, net.minecraft.world.World world, BlockPos pos, net.minecraft.entity.player.PlayerEntity player, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient()) return;
        if (!(world instanceof ServerWorld sw)) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;
        try {
            var info = com.hitpo.doommc3d.interact.DoomTriggerRegistry.getUse(sw, pos);
            com.hitpo.doommc3d.util.DebugLogger.debug("LeverUseMixin", () -> "[DoomMC3D] Lever use intercepted at " + pos + " registered=" + (info != null));
        } catch (Exception e) {
            com.hitpo.doommc3d.util.DebugLogger.debug("LeverUseMixin.error", () -> {
                e.printStackTrace();
                return "[DoomMC3D] LeverUseMixin exception";
            });
        }
    }
}
