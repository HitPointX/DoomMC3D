package com.hitpo.doommc3d.mixin;

import com.hitpo.doommc3d.interact.DisplayCorpseAccess;
import com.hitpo.doommc3d.interact.DoomCorpsePhysics;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityDoomCorpseNbtMixin {
    @Unique private static final String NBT_ROOT = "doommc3d_corpse";
    @Unique private static final String NBT_VX = "vx";
    @Unique private static final String NBT_VY = "vy";
    @Unique private static final String NBT_VZ = "vz";
    @Unique private static final String NBT_REMAIN = "remaining";
    @Unique private static final String NBT_DRAG = "drag";

    @Inject(method = "writeData", at = @At("TAIL"))
    private void doommc3d$writeCorpse(WriteView view, CallbackInfo ci) {
        if (!((Object)this instanceof DisplayEntity.ItemDisplayEntity disp)) return;
        if (!disp.getCommandTags().contains("doommc3d_corpse")) return;
        if (!(disp instanceof DisplayCorpseAccess access)) return;

        WriteView root = view.get(NBT_ROOT);
        root.putDouble(NBT_VX, access.getSlideVx());
        root.putDouble(NBT_VY, access.getSlideVy());
        root.putDouble(NBT_VZ, access.getSlideVz());
        root.putInt(NBT_REMAIN, access.getSlideRemaining());
        root.putDouble(NBT_DRAG, access.getSlideDrag());
    }

    @Inject(method = "readData", at = @At("TAIL"))
    private void doommc3d$readCorpse(ReadView view, CallbackInfo ci) {
        if (!((Object)this instanceof DisplayEntity.ItemDisplayEntity disp)) return;
        if (!disp.getCommandTags().contains("doommc3d_corpse")) return;
        if (!(disp instanceof DisplayCorpseAccess access)) return;

        var opt = view.getOptionalReadView(NBT_ROOT);
        if (opt.isEmpty()) return;
        ReadView root = opt.get();
        double vx = root.getDouble(NBT_VX, 0.0);
        double vy = root.getDouble(NBT_VY, 0.0);
        double vz = root.getDouble(NBT_VZ, 0.0);
        int remaining = root.getInt(NBT_REMAIN, 0);
        double drag = root.getDouble(NBT_DRAG, 0.92);

        access.setSlideVx(vx);
        access.setSlideVy(vy);
        access.setSlideVz(vz);
        access.setSlideRemaining(remaining);
        access.setSlideDrag(drag);

        if (remaining > 0) {
            if (disp.getEntityWorld() instanceof ServerWorld world) {
                DoomCorpsePhysics.startSliding(world, disp, new Vec3d(vx, vy, vz), remaining, drag);
            }
        }
    }
}
