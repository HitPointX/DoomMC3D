package com.hitpo.doommc3d.command;

import com.hitpo.doommc3d.doomai.DoomBossSystem;
import com.hitpo.doommc3d.doomai.DoomBossType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class DoomBossCommand {
    private DoomBossCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("doomboss")
                .then(CommandManager.argument("type", StringArgumentType.word())
                    .executes(DoomBossCommand::run))
            );
        });
    }

    private static int run(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String typeArg = StringArgumentType.getString(ctx, "type");
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        DoomBossType type = switch (typeArg.toLowerCase()) {
            case "cyber", "cyberdemon" -> DoomBossType.CYBERDEMON;
            case "spider", "mastermind", "spidermastermind" -> DoomBossType.SPIDER_MASTERMIND;
            default -> null;
        };
        if (type == null) {
            player.sendMessage(Text.literal("[DoomMC3D] Usage: /doomboss <cyber|spider>"), false);
            return 0;
        }

        MobEntity mob = (MobEntity) (type == DoomBossType.CYBERDEMON
            ? EntityType.IRON_GOLEM.create(ctx.getSource().getWorld(), SpawnReason.COMMAND)
            : EntityType.SPIDER.create(ctx.getSource().getWorld(), SpawnReason.COMMAND));
        if (mob == null) {
            player.sendMessage(Text.literal("[DoomMC3D] Failed to spawn boss mob"), false);
            return 0;
        }
        mob.refreshPositionAndAngles(player.getX() + 2, player.getY(), player.getZ() + 2, player.getYaw(), 0);
        mob.setCustomName(Text.literal(type == DoomBossType.CYBERDEMON ? "Cyberdemon" : "Spider Mastermind"));
        mob.setCustomNameVisible(true);
        ctx.getSource().getWorld().spawnEntity(mob);

        DoomBossSystem.attach(mob, type);
        player.sendMessage(Text.literal("[DoomMC3D] Spawned " + mob.getName().getString()), false);
        return 1;
    }
}
