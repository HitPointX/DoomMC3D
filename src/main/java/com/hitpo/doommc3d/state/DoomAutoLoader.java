package com.hitpo.doommc3d.state;

import com.hitpo.doommc3d.worldgen.DoomWorldBuilder;
import com.hitpo.doommc3d.item.ModItems;
import com.hitpo.doommc3d.player.DoomAmmoAccess;
import com.hitpo.doommc3d.player.DoomAmmoType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles auto-loading of Doom maps when players join the world.
 * - First join: loads E1M1
 * - Subsequent joins: loads the last played map
 */
public class DoomAutoLoader {
    // Track which worlds are currently generating to prevent race conditions
    private static final Map<ServerWorld, Boolean> generatingWorlds = new ConcurrentHashMap<>();

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            ServerWorld world = server.getOverworld(); // Use overworld for Doom

            // Prevent multiple simultaneous generations
            if (generatingWorlds.getOrDefault(world, false)) {
                com.hitpo.doommc3d.util.DebugLogger.debug("DoomAutoLoader", () -> "[DoomMC3D] Map generation already in progress, skipping auto-load for " + player.getName().getString());
                return;
            }

            DoomWorldState state = DoomWorldState.get(world);

            // Initialize on first join
            if (!state.isInitialized()) {
                state.setInitialized(true);
                state.setLastMap("e1m1");
                state.setCurrentLoadedMap("");
            }

            String desiredMap = state.getLastMap();
            String currentMap = state.getCurrentLoadedMap();

            // Only rebuild if needed
            if (!desiredMap.equalsIgnoreCase(currentMap)) {
                com.hitpo.doommc3d.util.DebugLogger.debug("DoomAutoLoader", () -> "[DoomMC3D] Auto-loading map: " + desiredMap + " for " + player.getName().getString());
                
                generatingWorlds.put(world, true);
                try {
                    // Run the same logic as /doomgen command
                    DoomWorldBuilder.build(world, player, desiredMap, null);
                    giveStartingGear(player);
                    
                    state.setCurrentLoadedMap(desiredMap);
                } catch (Exception e) {
                    com.hitpo.doommc3d.util.DebugLogger.debug("DoomAutoLoader.error", () -> {
                        e.printStackTrace();
                        return "[DoomMC3D] Failed to auto-load map: " + e.getMessage();
                    });
                    // On failure, reset to e1m1
                    state.setLastMap("e1m1");
                    state.setCurrentLoadedMap("");
                } finally {
                    generatingWorlds.put(world, false);
                }
            } else {
                com.hitpo.doommc3d.util.DebugLogger.debug("DoomAutoLoader", () -> "[DoomMC3D] Map " + desiredMap + " already loaded for " + player.getName().getString());
                // Map already loaded, just give starting gear
                giveStartingGear(player);
            }
        });
    }

    /**
     * Gives the player starting Doom gear (same as /doomgen command)
     */
    private static void giveStartingGear(ServerPlayerEntity player) {
        ItemStack pistol = new ItemStack(ModItems.DOOM_PISTOL);
            if (!player.getInventory().contains(pistol)) {
            player.giveItemStack(pistol);
            com.hitpo.doommc3d.util.DebugLogger.debug("DoomAutoLoader", () -> "[DoomMC3D] giveStartingGear: gave pistol to " + player.getName().getString());
        }
        
        // Set Doom health (100 HP)
        var maxHealth = player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(100.0);
            player.setHealth(100.0f);
        }
        
        // Add Doom tags
        player.addCommandTag("doommc3d_active");
        player.addCommandTag("doommc3d_weapon_pistol");

        // Doom starts with 50 bullets.
        if (player instanceof DoomAmmoAccess ammo) {
            ammo.setDoomAmmo(DoomAmmoType.BULLET, 50);
        }
        
        // Select pistol in hotbar
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getStack(slot).isOf(ModItems.DOOM_PISTOL)) {
                player.getInventory().setSelectedSlot(slot);
                final String slotMsg = "[DoomMC3D] giveStartingGear: selected hotbar slot " + slot + " for " + player.getName().getString();
                com.hitpo.doommc3d.util.DebugLogger.debug("DoomAutoLoader", () -> slotMsg);
                break;
            }
        }
    }
}
