package com.hitpo.doommc3d.command;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class DoomDebugCommand {
    private DoomDebugCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("doommc3d_list_triggers")
                .executes(ctx -> run(ctx))
            );
        });
    }

    private static int run(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player;
        try {
            player = ctx.getSource().getPlayer();
        } catch (Exception e) {
            return 0;
        }

        var world = ctx.getSource().getWorld();
        int px = player.getBlockX();
        int py = player.getBlockY();
        int pz = player.getBlockZ();
        int found = 0;
        int radius = 12;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    var pos = player.getBlockPos().add(dx, dy, dz);
                    var info = com.hitpo.doommc3d.interact.DoomTriggerRegistry.getUse((net.minecraft.server.world.ServerWorld) world, pos);
                    if (info != null) {
                        found++;
                        player.sendMessage(Text.literal("Trigger at " + pos + " -> " + info.action() + " cooldown=" + info.cooldownTicks()), false);
                    }
                }
            }
        }
        player.sendMessage(Text.literal("Found " + found + " triggers within " + radius + " blocks"), false);
        return 1;
    }
}
