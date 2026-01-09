package com.hitpo.doommc3d.mixin;

import com.hitpo.doommc3d.interact.DisplayCorpseAccess;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(DisplayEntity.ItemDisplayEntity.class)
public abstract class DisplayEntityCorpseMixin implements DisplayCorpseAccess {
    @Unique private double doommc3d_slide_vx = 0.0;
    @Unique private double doommc3d_slide_vy = 0.0;
    @Unique private double doommc3d_slide_vz = 0.0;
    @Unique private int doommc3d_slide_remaining = 0;
    @Unique private double doommc3d_slide_drag = 0.92;

    @Override
    public void setSlideVx(double vx) { this.doommc3d_slide_vx = vx; }
    @Override
    public double getSlideVx() { return this.doommc3d_slide_vx; }
    @Override
    public void setSlideVy(double vy) { this.doommc3d_slide_vy = vy; }
    @Override
    public double getSlideVy() { return this.doommc3d_slide_vy; }
    @Override
    public void setSlideVz(double vz) { this.doommc3d_slide_vz = vz; }
    @Override
    public double getSlideVz() { return this.doommc3d_slide_vz; }

    @Override
    public void setSlideRemaining(int ticks) { this.doommc3d_slide_remaining = ticks; }
    @Override
    public int getSlideRemaining() { return this.doommc3d_slide_remaining; }

    @Override
    public void setSlideDrag(double drag) { this.doommc3d_slide_drag = drag; }
    @Override
    public double getSlideDrag() { return this.doommc3d_slide_drag; }
}
