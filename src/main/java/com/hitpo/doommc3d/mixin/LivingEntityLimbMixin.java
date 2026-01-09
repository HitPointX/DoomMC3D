package com.hitpo.doommc3d.mixin;

import com.hitpo.doommc3d.gib.DoomLimbAccess;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntity.class)
public abstract class LivingEntityLimbMixin implements DoomLimbAccess {
    @Unique private int doommc3d_headHp = 0;
    @Unique private int doommc3d_leftArmHp = 0;
    @Unique private int doommc3d_rightArmHp = 0;
    @Unique private int doommc3d_leftLegHp = 0;
    @Unique private int doommc3d_rightLegHp = 0;

    @Override public int getHeadHp() { return doommc3d_headHp; }
    @Override public void setHeadHp(int v) { doommc3d_headHp = Math.max(0, v); }
    @Override public int getLeftArmHp() { return doommc3d_leftArmHp; }
    @Override public void setLeftArmHp(int v) { doommc3d_leftArmHp = Math.max(0, v); }
    @Override public int getRightArmHp() { return doommc3d_rightArmHp; }
    @Override public void setRightArmHp(int v) { doommc3d_rightArmHp = Math.max(0, v); }
    @Override public int getLeftLegHp() { return doommc3d_leftLegHp; }
    @Override public void setLeftLegHp(int v) { doommc3d_leftLegHp = Math.max(0, v); }
    @Override public int getRightLegHp() { return doommc3d_rightLegHp; }
    @Override public void setRightLegHp(int v) { doommc3d_rightLegHp = Math.max(0, v); }
}
