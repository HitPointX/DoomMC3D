package com.hitpo.doommc3d.player;

public interface DoomAmmoAccess {
    int getDoomAmmo(DoomAmmoType type);

    boolean hasDoomBackpack();

    void setDoomBackpack(boolean hasBackpack);

    /**
     * Sets ammo, clamped to $[0, max]$ for the type.
     */
    void setDoomAmmo(DoomAmmoType type, int amount);

    /**
     * Adds ammo (or subtracts if negative), clamped to $[0, max]$.
     * @return the new ammo amount.
     */
    int addDoomAmmo(DoomAmmoType type, int delta);

    /**
     * @return true if ammo was consumed.
     */
    boolean consumeDoomAmmo(DoomAmmoType type, int amount);
}
