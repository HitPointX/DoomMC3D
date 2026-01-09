package com.hitpo.doommc3d.mixin;

import com.hitpo.doommc3d.gib.DoomLimbAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityDoomLimbNbtMixin {
    @Unique private static final String NBT_ROOT = "doommc3d_limbs";
    @Unique private static final String NBT_HEAD = "head";
    @Unique private static final String NBT_LARM = "larm";
    @Unique private static final String NBT_RARM = "rarm";
    @Unique private static final String NBT_LLEG = "lleg";
    @Unique private static final String NBT_RLEG = "rleg";

    @Inject(method = "writeData", at = @At("TAIL"))
    private void doommc3d$writeData(WriteView view, CallbackInfo ci) {
        if (!((Object)this instanceof LivingEntity living)) return;
        if (!(living instanceof DoomLimbAccess limbs)) return;

        WriteView root = view.get(NBT_ROOT);
        root.putInt(NBT_HEAD, limbs.getHeadHp());
        root.putInt(NBT_LARM, limbs.getLeftArmHp());
        root.putInt(NBT_RARM, limbs.getRightArmHp());
        root.putInt(NBT_LLEG, limbs.getLeftLegHp());
        root.putInt(NBT_RLEG, limbs.getRightLegHp());
    }

    @Inject(method = "readData", at = @At("TAIL"))
    private void doommc3d$readData(ReadView view, CallbackInfo ci) {
        if (!((Object)this instanceof LivingEntity living)) return;
        if (!(living instanceof DoomLimbAccess limbs)) return;

        var opt = view.getOptionalReadView(NBT_ROOT);
        if (opt.isEmpty()) return;
        ReadView root = opt.get();
        limbs.setHeadHp(root.getInt(NBT_HEAD, limbs.getHeadHp()));
        limbs.setLeftArmHp(root.getInt(NBT_LARM, limbs.getLeftArmHp()));
        limbs.setRightArmHp(root.getInt(NBT_RARM, limbs.getRightArmHp()));
        limbs.setLeftLegHp(root.getInt(NBT_LLEG, limbs.getLeftLegHp()));
        limbs.setRightLegHp(root.getInt(NBT_RLEG, limbs.getRightLegHp()));
    }
}
