package com.hitpo.doommc3d.client.weapon;

import com.hitpo.doommc3d.item.ModItems;
import com.hitpo.doommc3d.net.FireWeaponPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public final class DoomWeaponClientInput {
    private static int recoilTicks = 0;

    // Doom runs at 35 tics/sec; Minecraft is 20 ticks/sec.
    private static final double DOOM_TIC_TO_MC_TICK = 20.0 / 35.0;
    private static final int DOOM_PISTOL_REFIRE_TICS = 14;
    private static final int DOOM_CHAINGUN_REFIRE_TICS = 4;
    private static final int DOOM_SHOTGUN_REFIRE_TICS = 37;
    private static final int DOOM_ROCKET_REFIRE_TICS = 20;
    private static final int DOOM_PLASMA_REFIRE_TICS = 3;
    private static final int DOOM_BFG_REFIRE_TICS = 40;

    private static boolean queuedClickFire = false;
    private static double nextAllowedFireTick = 0.0;

    private DoomWeaponClientInput() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DoomWeaponClientInput::tick);
    }

    public static int getRecoilTicks() {
        return recoilTicks;
    }

    public static void triggerRecoil() {
        recoilTicks = 4;
    }

    public static void queueFireRequest() {
        queuedClickFire = true;
    }

    private static boolean isDoomWeapon(PlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        return stack.isOf(ModItems.DOOM_PISTOL)
            || stack.isOf(ModItems.DOOM_SHOTGUN)
            || stack.isOf(ModItems.DOOM_CHAINGUN)
            || stack.isOf(ModItems.DOOM_ROCKET_LAUNCHER)
            || stack.isOf(ModItems.DOOM_PLASMA_RIFLE)
            || stack.isOf(ModItems.DOOM_BFG);
    }

    private static int getRefireTics(PlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (stack.isOf(ModItems.DOOM_PISTOL)) return DOOM_PISTOL_REFIRE_TICS;
        if (stack.isOf(ModItems.DOOM_SHOTGUN)) return DOOM_SHOTGUN_REFIRE_TICS;
        if (stack.isOf(ModItems.DOOM_CHAINGUN)) return DOOM_CHAINGUN_REFIRE_TICS;
        if (stack.isOf(ModItems.DOOM_ROCKET_LAUNCHER)) return DOOM_ROCKET_REFIRE_TICS;
        if (stack.isOf(ModItems.DOOM_PLASMA_RIFLE)) return DOOM_PLASMA_REFIRE_TICS;
        if (stack.isOf(ModItems.DOOM_BFG)) return DOOM_BFG_REFIRE_TICS;
        return DOOM_PISTOL_REFIRE_TICS;
    }

    private static void tick(MinecraftClient client) {
        if (recoilTicks > 0) {
            recoilTicks--;
        }
        var player = client.player;
        if (player == null || client.currentScreen != null) {
            return;
        }
        if (!isDoomWeapon(player)) {
            return;
        }

        boolean wantsFire = queuedClickFire || (client.options != null && client.options.attackKey.isPressed());
        queuedClickFire = false;

        // If trigger is released, allow immediate refire on next press.
        if (!wantsFire) {
            nextAllowedFireTick = 0.0;
            return;
        }

        if (client.world == null) {
            return;
        }

        double now = client.world.getTime();
        if (now + 1e-6 < nextAllowedFireTick) {
            return;
        }

        int refireTics = getRefireTics(player);
        double interval = Math.max(0.05, refireTics * DOOM_TIC_TO_MC_TICK);
        nextAllowedFireTick = now + interval;

        // Only send fire request; animation will be triggered by server confirmation.
        ClientPlayNetworking.send(new FireWeaponPayload());
    }
}
