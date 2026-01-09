package com.hitpo.doommc3d.client.weapon;

import com.hitpo.doommc3d.item.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;

/**
 * Client-only HUD animation driver for Doom weapon sprites and muzzle flashes.
 *
 * This is intentionally small and deterministic per-shot: server remains authoritative for damage.
 */
public final class DoomWeaponClientAnim {
    private static final WeaponState[] PISTOL_STATES = new WeaponState[] {
        // Vanilla Doom: S_PISTOL2 (frame 1, 6 tics) -> S_PISTOL3 (frame 2, 4 tics) -> S_PISTOL4 (frame 1, 5 tics)
        new WeaponState("PISGB0", doomTicsToMcTicks(6), 1),
        new WeaponState("PISGC0", doomTicsToMcTicks(4), 2),
        new WeaponState("PISGB0", doomTicsToMcTicks(5), 3),
        new WeaponState("PISGA0", 0, 3)
    };

    private static final WeaponState[] SHOTGUN_STATES = new WeaponState[] {
        // Vanilla Doom: 3,7,5,5,4,5,5,3 (see S_SGUN1..S_SGUN8)
        new WeaponState("SHTGA0", doomTicsToMcTicks(3), 1),
        new WeaponState("SHTGA0", doomTicsToMcTicks(7), 2),
        new WeaponState("SHTGB0", doomTicsToMcTicks(5), 3),
        new WeaponState("SHTGC0", doomTicsToMcTicks(5), 4),
        new WeaponState("SHTGD0", doomTicsToMcTicks(4), 5),
        new WeaponState("SHTGC0", doomTicsToMcTicks(5), 6),
        new WeaponState("SHTGB0", doomTicsToMcTicks(5), 7),
        new WeaponState("SHTGA0", doomTicsToMcTicks(3), 8),
        new WeaponState("SHTGA0", 0, 8)
    };

    private static WeaponState[] weaponStates = null;
    private static int weaponIndex = 0;
    private static int weaponTicksLeft = 0;

    private static WeaponState[] flashStates = null;
    private static int flashIndex = 0;
    private static int flashTicksLeft = 0;

    private static boolean chaingunToggle = false;
    private static boolean plasmaToggle = false;
    private static boolean bfgToggle = false;

    private static Item lastHeldWeapon = null;

    private DoomWeaponClientAnim() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DoomWeaponClientAnim::tick);
    }

    public static void onLocalFire(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        Item item = client.player.getMainHandStack().getItem();

        if (item == ModItems.DOOM_SHOTGUN) {
            weaponStates = SHOTGUN_STATES;
            weaponIndex = 0;
            weaponTicksLeft = weaponStates[0].tics();

            flashStates = new WeaponState[] {
                new WeaponState("SHTFA0", doomTicsToMcTicks(4), 1),
                new WeaponState("SHTFB0", doomTicsToMcTicks(3), 2),
                new WeaponState(null, 0, 2)
            };
            flashIndex = 0;
            flashTicksLeft = flashStates[0].tics();
            return;
        }

        // Chaingun uses per-shot alternation like vanilla Doom.
        if (item == ModItems.DOOM_CHAINGUN) {
            chaingunToggle = !chaingunToggle;
            weaponStates = null;
            flashStates = null;
            flashTicksLeft = doomTicsToMcTicks(5);
            return;
        }

        if (item == ModItems.DOOM_PISTOL) {
            weaponStates = PISTOL_STATES;
            weaponIndex = 0;
            weaponTicksLeft = weaponStates[0].tics();

            flashStates = new WeaponState[] {
                new WeaponState("PISFA0", doomTicsToMcTicks(7), 1),
                new WeaponState(null, 0, 1)
            };
            flashIndex = 0;
            flashTicksLeft = flashStates[0].tics();
            return;
        }

        if (item == ModItems.DOOM_PLASMA_RIFLE) {
            // Doom flash randomly alternates between 2 frames.
            plasmaToggle = !plasmaToggle;
            weaponStates = null;
            flashStates = null;
            flashTicksLeft = doomTicsToMcTicks(4);
            return;
        }

        if (item == ModItems.DOOM_ROCKET_LAUNCHER) {
            weaponStates = null;
            flashStates = new WeaponState[] {
                new WeaponState("MISFA0", doomTicsToMcTicks(3), 1),
                new WeaponState("MISFB0", doomTicsToMcTicks(4), 2),
                new WeaponState("MISFC0", doomTicsToMcTicks(4), 3),
                new WeaponState("MISFD0", doomTicsToMcTicks(4), 4),
                new WeaponState(null, 0, 4)
            };
            flashIndex = 0;
            flashTicksLeft = flashStates[0].tics();
            return;
        }

        if (item == ModItems.DOOM_BFG) {
            bfgToggle = !bfgToggle;
            weaponStates = null;
            flashStates = new WeaponState[] {
                new WeaponState("BFGFA0", doomTicsToMcTicks(11), 1),
                new WeaponState("BFGFB0", doomTicsToMcTicks(6), 2),
                new WeaponState(null, 0, 2)
            };
            flashIndex = 0;
            flashTicksLeft = flashStates[0].tics();
            return;
        }

        // Other weapons: keep a small kick frame + flash, but don't attempt full raise/lower tables yet.
        if (isDoomWeapon(item)) {
            weaponStates = null;
            flashStates = null;
            flashTicksLeft = doomTicsToMcTicks(4);
        }
    }

    public static String getWeaponLump(MinecraftClient client) {
        if (client.player == null) {
            return null;
        }
        Item item = client.player.getMainHandStack().getItem();

        if (item == ModItems.DOOM_PISTOL) {
            if (weaponStates != null) {
                return weaponStates[weaponIndex].sprite();
            }
            return "PISGA0";
        }
        if (item == ModItems.DOOM_SHOTGUN) {
            if (weaponStates != null) {
                return weaponStates[weaponIndex].sprite();
            }
            return "SHTGA0";
        }
        if (item == ModItems.DOOM_CHAINGUN) {
            return chaingunToggle ? "CHGGB0" : "CHGGA0";
        }
        if (item == ModItems.DOOM_ROCKET_LAUNCHER) {
            return "MISGA0";
        }
        if (item == ModItems.DOOM_PLASMA_RIFLE) {
            return plasmaToggle ? "PLSGB0" : "PLSGA0";
        }
        if (item == ModItems.DOOM_BFG) {
            return bfgToggle ? "BFGGB0" : "BFGGA0";
        }
        return null;
    }

    public static String getFlashLump(MinecraftClient client) {
        if (flashTicksLeft <= 0 || client.player == null) {
            return null;
        }
        if (flashStates != null) {
            return flashStates[flashIndex].sprite();
        }
        Item item = client.player.getMainHandStack().getItem();
        if (item == ModItems.DOOM_CHAINGUN) return chaingunToggle ? "CHGFB0" : "CHGFA0";
        if (item == ModItems.DOOM_PLASMA_RIFLE) return plasmaToggle ? "PLSFB0" : "PLSFA0";
        return null;
    }

    private static boolean isDoomWeapon(Item item) {
        return item == ModItems.DOOM_PISTOL
            || item == ModItems.DOOM_SHOTGUN
            || item == ModItems.DOOM_CHAINGUN
            || item == ModItems.DOOM_ROCKET_LAUNCHER
            || item == ModItems.DOOM_PLASMA_RIFLE
            || item == ModItems.DOOM_BFG;
    }

    private static void tick(MinecraftClient client) {
        if (client.player != null) {
            Item held = client.player.getMainHandStack().getItem();
            if (held != lastHeldWeapon) {
                lastHeldWeapon = held;
                weaponStates = null;
                weaponIndex = 0;
                weaponTicksLeft = 0;
                flashStates = null;
                flashIndex = 0;
                flashTicksLeft = 0;
            }
        }

        if (flashTicksLeft > 0) {
            flashTicksLeft--;
        }
        if (flashStates != null && flashTicksLeft <= 0) {
            WeaponState cur = flashStates[flashIndex];
            int next = cur.next();
            flashIndex = Math.max(0, Math.min(next, flashStates.length - 1));
            flashTicksLeft = flashStates[flashIndex].tics();
        }

        if (weaponStates == null) {
            return;
        }
        if (weaponTicksLeft > 0) {
            weaponTicksLeft--;
            return;
        }

        WeaponState cur = weaponStates[weaponIndex];
        int next = cur.next();
        weaponIndex = Math.max(0, Math.min(next, weaponStates.length - 1));
        weaponTicksLeft = weaponStates[weaponIndex].tics();
    }

    private record WeaponState(String sprite, int tics, int next) {
    }

    private static int doomTicsToMcTicks(int doomTics) {
        // Doom runs at 35 tics/sec, Minecraft at 20 ticks/sec.
        int mc = Math.round(doomTics * (20.0f / 35.0f));
        return Math.max(1, mc);
    }
}
