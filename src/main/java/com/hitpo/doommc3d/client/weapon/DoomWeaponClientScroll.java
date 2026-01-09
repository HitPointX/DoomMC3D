package com.hitpo.doommc3d.client.weapon;

import com.hitpo.doommc3d.item.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

public final class DoomWeaponClientScroll {
    private static final Item[] ORDER = {
        ModItems.DOOM_PISTOL,
        ModItems.DOOM_SHOTGUN,
        ModItems.DOOM_CHAINGUN,
        ModItems.DOOM_ROCKET_LAUNCHER,
        ModItems.DOOM_PLASMA_RIFLE,
        ModItems.DOOM_BFG
    };

    private DoomWeaponClientScroll() {
    }

    public static boolean handleScroll(MinecraftClient client, double verticalScroll) {
        if (client.player == null || client.currentScreen != null) {
            return false;
        }
        if (verticalScroll == 0.0) {
            return false;
        }
        Item current = client.player.getMainHandStack().getItem();
        int currentIndex = indexOf(current);
        if (currentIndex < 0) {
            return false;
        }

        int dir = verticalScroll > 0 ? -1 : 1;
        int nextSlot = findNextWeaponHotbarSlot(client, currentIndex, dir);
        if (nextSlot < 0) {
            return true; // consume scroll to avoid switching to non-doom items while in weapon mode
        }

        if (client.player.getInventory().getSelectedSlot() != nextSlot) {
            client.player.getInventory().setSelectedSlot(nextSlot);
            if (client.getNetworkHandler() != null) {
                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(nextSlot));
            }
        }
        return true;
    }

    private static int findNextWeaponHotbarSlot(MinecraftClient client, int currentIndex, int dir) {
        for (int step = 1; step <= ORDER.length; step++) {
            int nextIndex = Math.floorMod(currentIndex + dir * step, ORDER.length);
            Item next = ORDER[nextIndex];
            for (int slot = 0; slot < 9; slot++) {
                if (client.player.getInventory().getStack(slot).isOf(next)) {
                    return slot;
                }
            }
        }
        return -1;
    }

    private static int indexOf(Item item) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] == item) {
                return i;
            }
        }
        return -1;
    }
}

