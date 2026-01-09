package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.item.ModItems;
import com.hitpo.doommc3d.player.DoomAmmo;
import com.hitpo.doommc3d.player.DoomAmmoAccess;
import com.hitpo.doommc3d.player.DoomAmmoType;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

public final class DoomWeaponPickupSystem {
    private DoomWeaponPickupSystem() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DoomWeaponPickupSystem::tickWorld);
    }

    private static void tickWorld(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            tryPickup(world, player);
        }
    }

    private static void tryPickup(ServerWorld world, ServerPlayerEntity player) {
        Box box = player.getBoundingBox().expand(1.2, 1.0, 1.2);
        var pickups = world.getEntitiesByType(EntityType.ITEM_DISPLAY, box, entity -> entity.getCommandTags().contains("doommc3d_weapon_pickup"));
        if (pickups.isEmpty()) {
            return;
        }
        for (DisplayEntity.ItemDisplayEntity display : pickups) {
            if (!display.isAlive()) {
                continue;
            }
            if (display.squaredDistanceTo(player) > (1.2 * 1.2)) {
                continue;
            }
            ItemStack stack = display.getItemStack();
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            if (!isDoomWeapon(item)) {
                continue;
            }

            boolean dropped = display.getCommandTags().contains("doommc3d_dropped");
            giveWeapon(player, item, dropped);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.9f, 1.0f);
            display.discard();
            break;
        }
    }

    private static boolean isDoomWeapon(Item item) {
        return item == ModItems.DOOM_PISTOL
            || item == ModItems.DOOM_SHOTGUN
            || item == ModItems.DOOM_CHAINGUN
            || item == ModItems.DOOM_ROCKET_LAUNCHER
            || item == ModItems.DOOM_PLASMA_RIFLE
            || item == ModItems.DOOM_BFG;
    }

    private static void giveWeapon(ServerPlayerEntity player, Item weaponItem, boolean dropped) {
        String tag = weaponTag(weaponItem);
        boolean newlyOwned = tag != null && !player.getCommandTags().contains(tag);
        if (tag != null) {
            player.addCommandTag(tag);
        }

        // Doom gives ammo even if you already own the weapon.
        if (weaponItem != ModItems.DOOM_PISTOL && player instanceof DoomAmmoAccess ammo) {
            DoomAmmoType ammoType = DoomAmmo.ammoTypeForWeapon(new ItemStack(weaponItem));
            if (ammoType != null) {
                int clips = dropped ? 1 : 2;
                ammo.addDoomAmmo(ammoType, clips * DoomAmmo.getClipAmount(ammoType));
            }
        }

        if (!containsWeapon(player, weaponItem)) {
            ItemStack stack = new ItemStack(weaponItem);
            putInHotbarOrGive(player, stack);
        }

        selectHotbarWeapon(player, weaponItem);
        if (newlyOwned) {
            player.sendMessage(Text.literal("[DoomMC3D] Picked up " + weaponName(weaponItem)), false);
        }
    }

    private static boolean containsWeapon(ServerPlayerEntity player, Item weaponItem) {
        return player.getInventory().contains(stack -> stack.isOf(weaponItem));
    }

    private static void putInHotbarOrGive(ServerPlayerEntity player, ItemStack stack) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getStack(slot).isEmpty()) {
                player.getInventory().setStack(slot, stack);
                return;
            }
        }
        player.giveItemStack(stack);
    }

    private static void selectHotbarWeapon(ServerPlayerEntity player, Item weaponItem) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getStack(slot).isOf(weaponItem)) {
                player.getInventory().setSelectedSlot(slot);
                return;
            }
        }
    }

    private static String weaponTag(Item weaponItem) {
        if (weaponItem == ModItems.DOOM_PISTOL) return "doommc3d_weapon_pistol";
        if (weaponItem == ModItems.DOOM_SHOTGUN) return "doommc3d_weapon_shotgun";
        if (weaponItem == ModItems.DOOM_CHAINGUN) return "doommc3d_weapon_chaingun";
        if (weaponItem == ModItems.DOOM_ROCKET_LAUNCHER) return "doommc3d_weapon_rocket_launcher";
        if (weaponItem == ModItems.DOOM_PLASMA_RIFLE) return "doommc3d_weapon_plasma_rifle";
        if (weaponItem == ModItems.DOOM_BFG) return "doommc3d_weapon_bfg";
        return null;
    }

    private static String weaponName(Item weaponItem) {
        if (weaponItem == ModItems.DOOM_PISTOL) return "Pistol";
        if (weaponItem == ModItems.DOOM_SHOTGUN) return "Shotgun";
        if (weaponItem == ModItems.DOOM_CHAINGUN) return "Chaingun";
        if (weaponItem == ModItems.DOOM_ROCKET_LAUNCHER) return "Rocket Launcher";
        if (weaponItem == ModItems.DOOM_PLASMA_RIFLE) return "Plasma Rifle";
        if (weaponItem == ModItems.DOOM_BFG) return "BFG9000";
        return "Weapon";
    }
}

