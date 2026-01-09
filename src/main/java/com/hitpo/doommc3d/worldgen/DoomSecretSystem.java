package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.interact.DoomSecretRegistry;
import com.hitpo.doommc3d.interact.DoomSecretTrigger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

public final class DoomSecretSystem {
    private static final Map<UUID, Set<Integer>> FOUND = new HashMap<>();

    private DoomSecretSystem() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DoomSecretSystem::tickWorld);
    }

    public static void clearPlayerCache() {
        FOUND.clear();
    }

    private static void tickWorld(ServerWorld world) {
        var secrets = DoomSecretRegistry.get(world);
        if (secrets.isEmpty()) {
            return;
        }
        int total = secrets.size();
        for (ServerPlayerEntity player : world.getPlayers()) {
            tickPlayer(player, secrets, total);
        }
    }

    private static void tickPlayer(ServerPlayerEntity player, java.util.List<DoomSecretTrigger> secrets, int total) {
        Set<Integer> found = FOUND.computeIfAbsent(player.getUuid(), k -> new HashSet<>());
        Box p = player.getBoundingBox().expand(0.15, 0.2, 0.15);
        for (DoomSecretTrigger trigger : secrets) {
            if (found.contains(trigger.id())) {
                continue;
            }
            if (!p.intersects(trigger.box())) {
                continue;
            }
            found.add(trigger.id());
            player.sendMessage(Text.literal("[DoomMC3D] SECRET FOUND! (" + found.size() + "/" + total + ")"), false);
            break;
        }
    }
}

