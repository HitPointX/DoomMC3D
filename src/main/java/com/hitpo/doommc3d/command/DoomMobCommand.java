package com.hitpo.doommc3d.command;

import com.hitpo.doommc3d.doomai.DoomMobSystem;
import com.hitpo.doommc3d.doomai.DoomMobType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class DoomMobCommand {
    private DoomMobCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("doomspawn")
                .then(CommandManager.argument("type", StringArgumentType.word())
                    .executes(DoomMobCommand::run))
            );
        });
    }

    private static int run(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String typeArg = StringArgumentType.getString(ctx, "type");
        ServerPlayerEntity player = ctx.getSource().getPlayer();

        DoomMobType type = parse(typeArg);
        if (type == null) {
            player.sendMessage(Text.literal("[DoomMC3D] Types: zombieman, shotgun, chaingunner, imp, demon, spectre, lostsoul, caco, baron"), false);
            return 0;
        }

        MobEntity mob = spawnBody(ctx.getSource(), type);
        if (mob == null) {
            player.sendMessage(Text.literal("[DoomMC3D] Failed to spawn mob body"), false);
            return 0;
        }

        mob.refreshPositionAndAngles(player.getX() + 2, player.getY(), player.getZ() + 2, player.getYaw(), 0);
        mob.setCustomName(Text.literal(type.name()));
        mob.setCustomNameVisible(false);
        ctx.getSource().getWorld().spawnEntity(mob);

        DoomMobSystem.attach(mob, type);
        player.sendMessage(Text.literal("[DoomMC3D] Spawned " + type), false);
        return 1;
    }

    private static DoomMobType parse(String s) {
        return switch (s.toLowerCase()) {
            case "zombieman", "zombie", "husk" -> DoomMobType.ZOMBIEMAN;
            case "shotgun", "sguy", "shotgungguy" -> DoomMobType.SHOTGUN_GUY;
            case "chaingunner", "chaingun", "cguy" -> DoomMobType.CHAINGUNNER;
            case "imp" -> DoomMobType.IMP;
            case "demon", "pinky" -> DoomMobType.DEMON;
            case "spectre" -> DoomMobType.SPECTRE;
            case "lostsoul", "lost_soul", "soul" -> DoomMobType.LOST_SOUL;
            case "caco", "cacodemon" -> DoomMobType.CACODEMON;
            case "baron", "hellknight" -> DoomMobType.BARON;
            default -> null;
        };
    }

    private static MobEntity spawnBody(ServerCommandSource source, DoomMobType type) {
        return switch (type) {
            case ZOMBIEMAN -> EntityType.HUSK.create(source.getWorld(), SpawnReason.COMMAND);
            case SHOTGUN_GUY, CHAINGUNNER -> EntityType.PILLAGER.create(source.getWorld(), SpawnReason.COMMAND);
            case IMP -> EntityType.BLAZE.create(source.getWorld(), SpawnReason.COMMAND);
            case DEMON, SPECTRE -> EntityType.HOGLIN.create(source.getWorld(), SpawnReason.COMMAND);
            case LOST_SOUL -> EntityType.VEX.create(source.getWorld(), SpawnReason.COMMAND);
            case CACODEMON -> EntityType.GHAST.create(source.getWorld(), SpawnReason.COMMAND);
            case BARON -> EntityType.WITHER_SKELETON.create(source.getWorld(), SpawnReason.COMMAND);
        };
    }
}
