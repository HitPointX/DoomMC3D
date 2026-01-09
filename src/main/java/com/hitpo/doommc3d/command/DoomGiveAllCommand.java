package com.hitpo.doommc3d.command;

import com.hitpo.doommc3d.item.ModItems;
import com.hitpo.doommc3d.player.DoomAmmo;
import com.hitpo.doommc3d.player.DoomAmmoAccess;
import com.hitpo.doommc3d.player.DoomAmmoType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class DoomGiveAllCommand {
    private DoomGiveAllCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("doomgiveall")
                .executes(ctx -> run(ctx.getSource()))
            );
        });
    }

    private static int run(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayer();
        } catch (Exception e) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }

        giveOnce(player, ModItems.DOOM_PISTOL);
        giveOnce(player, ModItems.DOOM_SHOTGUN);
        giveOnce(player, ModItems.DOOM_CHAINGUN);
        giveOnce(player, ModItems.DOOM_ROCKET_LAUNCHER);
        giveOnce(player, ModItems.DOOM_PLASMA_RIFLE);
        giveOnce(player, ModItems.DOOM_BFG);

        player.addCommandTag("doommc3d_active");
        player.addCommandTag("doommc3d_weapon_pistol");
        player.addCommandTag("doommc3d_weapon_shotgun");
        player.addCommandTag("doommc3d_weapon_chaingun");
        player.addCommandTag("doommc3d_weapon_rocket_launcher");
        player.addCommandTag("doommc3d_weapon_plasma_rifle");
        player.addCommandTag("doommc3d_weapon_bfg");

        if (player instanceof DoomAmmoAccess ammo) {
            ammo.setDoomBackpack(true);
            ammo.setDoomAmmo(DoomAmmoType.BULLET, DoomAmmo.getMax(DoomAmmoType.BULLET, true));
            ammo.setDoomAmmo(DoomAmmoType.SHELL, DoomAmmo.getMax(DoomAmmoType.SHELL, true));
            ammo.setDoomAmmo(DoomAmmoType.ROCKET, DoomAmmo.getMax(DoomAmmoType.ROCKET, true));
            ammo.setDoomAmmo(DoomAmmoType.CELL, DoomAmmo.getMax(DoomAmmoType.CELL, true));
        }

        source.sendFeedback(() -> Text.literal("Gave all Doom weapons + ammo."), false);
        return 1;
    }

    private static void giveOnce(ServerPlayerEntity player, Item item) {
        if (item == null) {
            return;
        }
        if (player.getInventory().contains(new ItemStack(item))) {
            return;
        }
        player.giveItemStack(new ItemStack(item));
    }
}
