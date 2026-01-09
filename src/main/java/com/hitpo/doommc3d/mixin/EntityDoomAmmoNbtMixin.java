package com.hitpo.doommc3d.mixin;

import com.hitpo.doommc3d.player.DoomAmmoAccess;
import com.hitpo.doommc3d.player.DoomAmmoType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Persists Doom ammo/backpack data for players.
 *
 * In 1.21.x Yarn, PlayerEntity does not declare the NBT hooks directly, so we inject into Entity
 * and gate on instanceof PlayerEntity.
 */
@Mixin(Entity.class)
public abstract class EntityDoomAmmoNbtMixin {
    @Unique private static final String NBT_ROOT = "doommc3d_ammo";
    @Unique private static final String NBT_BULLET = "bullet";
    @Unique private static final String NBT_SHELL = "shell";
    @Unique private static final String NBT_ROCKET = "rocket";
    @Unique private static final String NBT_CELL = "cell";
    @Unique private static final String NBT_BACKPACK = "backpack";

    @Inject(method = "writeData", at = @At("TAIL"))
    private void doommc3d$writeData(WriteView view, CallbackInfo ci) {
        if (!((Object) this instanceof PlayerEntity player)) {
            return;
        }
        if (!(player instanceof DoomAmmoAccess ammo)) {
            return;
        }

        WriteView doom = view.get(NBT_ROOT);
        doom.putInt(NBT_BULLET, ammo.getDoomAmmo(DoomAmmoType.BULLET));
        doom.putInt(NBT_SHELL, ammo.getDoomAmmo(DoomAmmoType.SHELL));
        doom.putInt(NBT_ROCKET, ammo.getDoomAmmo(DoomAmmoType.ROCKET));
        doom.putInt(NBT_CELL, ammo.getDoomAmmo(DoomAmmoType.CELL));
        doom.putBoolean(NBT_BACKPACK, ammo.hasDoomBackpack());
    }

    @Inject(method = "readData", at = @At("TAIL"))
    private void doommc3d$readData(ReadView view, CallbackInfo ci) {
        if (!((Object) this instanceof PlayerEntity player)) {
            return;
        }
        if (!(player instanceof DoomAmmoAccess ammo)) {
            return;
        }

        var doomOpt = view.getOptionalReadView(NBT_ROOT);
        if (doomOpt.isEmpty()) {
            return;
        }

        ReadView doom = doomOpt.get();

        // Order matters: backpack affects max caps.
        ammo.setDoomBackpack(doom.getBoolean(NBT_BACKPACK, false));
        ammo.setDoomAmmo(DoomAmmoType.BULLET, doom.getInt(NBT_BULLET, 0));
        ammo.setDoomAmmo(DoomAmmoType.SHELL, doom.getInt(NBT_SHELL, 0));
        ammo.setDoomAmmo(DoomAmmoType.ROCKET, doom.getInt(NBT_ROCKET, 0));
        ammo.setDoomAmmo(DoomAmmoType.CELL, doom.getInt(NBT_CELL, 0));
    }
}
