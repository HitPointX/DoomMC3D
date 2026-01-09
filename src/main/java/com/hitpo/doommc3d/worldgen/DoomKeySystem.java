package com.hitpo.doommc3d.worldgen;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

public final class DoomKeySystem {
    private DoomKeySystem() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DoomKeySystem::tickWorld);
    }

    private static void tickWorld(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            tryPickupKeys(world, player);
        }
    }

    private static void tryPickupKeys(ServerWorld world, ServerPlayerEntity player) {
        Box box = player.getBoundingBox().expand(1.2, 1.0, 1.2);
        var keys = world.getEntitiesByType(EntityType.ITEM_DISPLAY, box, entity -> entity.getCommandTags().contains("doommc3d_key"));
        if (keys.isEmpty()) {
            return;
        }
        for (DisplayEntity.ItemDisplayEntity key : keys) {
            if (!key.isAlive()) {
                continue;
            }
            if (key.squaredDistanceTo(player) > (1.2 * 1.2)) {
                continue;
            }
            if (key.getCommandTags().contains("doommc3d_key_red")) {
                grant(player, "doommc3d_key_red", "[DoomMC3D] Picked up Red Keycard");
            } else if (key.getCommandTags().contains("doommc3d_key_yellow")) {
                grant(player, "doommc3d_key_yellow", "[DoomMC3D] Picked up Yellow Keycard");
            } else if (key.getCommandTags().contains("doommc3d_key_blue")) {
                grant(player, "doommc3d_key_blue", "[DoomMC3D] Picked up Blue Keycard");
            } else {
                continue;
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.0f);
            key.discard();
        }
    }

    private static void grant(ServerPlayerEntity player, String tag, String message) {
        if (!player.getCommandTags().contains(tag)) {
            player.addCommandTag(tag);
            player.sendMessage(Text.literal(message), false);
        }
    }
}

