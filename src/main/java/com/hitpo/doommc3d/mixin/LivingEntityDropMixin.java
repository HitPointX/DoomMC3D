package com.hitpo.doommc3d.mixin;

import com.hitpo.doommc3d.worldgen.DoomMobDrops;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDropMixin {
    @Inject(method = "drop(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;)V", at = @At("HEAD"), cancellable = true)
    private void doommc3d_overrideDoomMobDrops(ServerWorld world, DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof MobEntity mob)) {
            return;
        }
        if (!DoomMobDrops.isDoomMob(mob)) {
            return;
        }

        DoomMobDrops.spawnDrops(world, mob);
        ci.cancel();
    }
}
