package com.hitpo.doommc3d.client.audio;

import com.hitpo.doommc3d.item.ModItems;
import net.minecraft.item.ItemStack;

public final class DoomWeaponSfx {
    private DoomWeaponSfx() {
    }

    public static String lumpForMainhand(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        if (stack.isOf(ModItems.DOOM_PISTOL)) {
            return "DSPISTOL";
        }
        if (stack.isOf(ModItems.DOOM_SHOTGUN)) {
            return "DSSHOTGN";
        }
        if (stack.isOf(ModItems.DOOM_CHAINGUN)) {
            // Doom uses a pistol-like shot sound for chaingun bursts in many WADs.
            return "DSPISTOL";
        }
        if (stack.isOf(ModItems.DOOM_ROCKET_LAUNCHER)) {
            return "DSRLAUNC";
        }
        if (stack.isOf(ModItems.DOOM_PLASMA_RIFLE)) {
            return "DSPLASMA";
        }
        if (stack.isOf(ModItems.DOOM_BFG)) {
            return "DSBFG";
        }
        return null;
    }
}
