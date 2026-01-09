package com.hitpo.doommc3d.command;

import com.hitpo.doommc3d.worldgen.DoomWorldBuilder;
import com.hitpo.doommc3d.state.DoomWorldState;
import com.hitpo.doommc3d.item.ModItems;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class DoomGenCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("doomgen")
                .then(CommandManager.argument("map", StringArgumentType.word())
                    .executes(ctx -> run(ctx, null))
                    .then(CommandManager.argument("wad", StringArgumentType.word())
                        .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "wad")))
                    )
                )
            );
        });
    }

    private static int run(CommandContext<ServerCommandSource> ctx, String wadOverride) throws CommandSyntaxException {
        String map = StringArgumentType.getString(ctx, "map");
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        DoomWorldBuilder.build(ctx.getSource().getWorld(), player, map, wadOverride);
        giveStartingPistol(player);
        
        // Save this as the last map for auto-loading on rejoin
        DoomWorldState state = DoomWorldState.get(ctx.getSource().getServer().getOverworld());
        state.setLastMap(map);
        state.setCurrentLoadedMap(map);
        
        return 1;
    }

    private static void giveStartingPistol(ServerPlayerEntity player) {
        ItemStack pistol = new ItemStack(ModItems.DOOM_PISTOL);
        if (!player.getInventory().contains(pistol)) {
            player.giveItemStack(pistol);
            com.hitpo.doommc3d.util.DebugLogger.debug("DoomGenCommand", () -> "[DoomMC3D] giveStartingPistol: gave pistol to " + player.getName().getString());
        }
        setDoomStartingHealth(player);
        player.addCommandTag("doommc3d_active");
        player.addCommandTag("doommc3d_weapon_pistol");
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getStack(slot).isOf(ModItems.DOOM_PISTOL)) {
                player.getInventory().setSelectedSlot(slot);
                final String slotMsg = "[DoomMC3D] giveStartingPistol: selected hotbar slot " + slot + " for " + player.getName().getString();
                com.hitpo.doommc3d.util.DebugLogger.debug("DoomGenCommand", () -> slotMsg);
                break;
            }
        }
    }

    private static void setDoomStartingHealth(ServerPlayerEntity player) {
        var maxHealth = player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(100.0);
            player.setHealth(100.0f);
        }
    }
}
