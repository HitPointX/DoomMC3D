package com.hitpo.doommc3d.interact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public final class DoomSecretRegistry {
    private static final Map<RegistryKey<World>, List<DoomSecretTrigger>> SECRETS = new HashMap<>();

    private DoomSecretRegistry() {
    }

    public static void clear(ServerWorld world) {
        SECRETS.remove(world.getRegistryKey());
    }

    public static void set(ServerWorld world, List<DoomSecretTrigger> triggers) {
        SECRETS.put(world.getRegistryKey(), new ArrayList<>(triggers));
    }

    public static List<DoomSecretTrigger> get(ServerWorld world) {
        return SECRETS.getOrDefault(world.getRegistryKey(), List.of());
    }
}

