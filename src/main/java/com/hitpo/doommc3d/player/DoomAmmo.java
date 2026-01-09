package com.hitpo.doommc3d.player;

import com.hitpo.doommc3d.item.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public final class DoomAmmo {
    private DoomAmmo() {
    }

    public static int getMax(DoomAmmoType type) {
        return switch (type) {
            case BULLET -> 200;
            case SHELL -> 50;
            case ROCKET -> 50;
            case CELL -> 300;
        };
    }

    public static int getMax(DoomAmmoType type, boolean hasBackpack) {
        int base = getMax(type);
        return hasBackpack ? base * 2 : base;
    }

    /**
     * Doom "clipammo" amounts (Chocolate Doom p_inter.c):
     * bullets=10, shells=4, cells=20, rockets=1.
     */
    public static int getClipAmount(DoomAmmoType type) {
        return switch (type) {
            case BULLET -> 10;
            case SHELL -> 4;
            case CELL -> 20;
            case ROCKET -> 1;
        };
    }

    public static DoomAmmoType ammoTypeForWeapon(ItemStack weaponStack) {
        if (weaponStack == null || weaponStack.isEmpty()) {
            return null;
        }
        if (weaponStack.isOf(ModItems.DOOM_PISTOL) || weaponStack.isOf(ModItems.DOOM_CHAINGUN)) {
            return DoomAmmoType.BULLET;
        }
        if (weaponStack.isOf(ModItems.DOOM_SHOTGUN)) {
            return DoomAmmoType.SHELL;
        }
        if (weaponStack.isOf(ModItems.DOOM_ROCKET_LAUNCHER)) {
            return DoomAmmoType.ROCKET;
        }
        if (weaponStack.isOf(ModItems.DOOM_PLASMA_RIFLE) || weaponStack.isOf(ModItems.DOOM_BFG)) {
            return DoomAmmoType.CELL;
        }
        return null;
    }

    public static int getAmmoForHeldWeapon(PlayerEntity player) {
        if (!(player instanceof DoomAmmoAccess ammo)) {
            return 0;
        }
        DoomAmmoType type = ammoTypeForWeapon(player.getMainHandStack());
        if (type == null) {
            return 0;
        }
        return ammo.getDoomAmmo(type);
    }
}
