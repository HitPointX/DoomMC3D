package com.hitpo.doommc3d.interact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public final class DoomTeleporterRegistry {
    private static final Map<RegistryKey<World>, List<DoomTeleporterTrigger>> TELEPORTERS = new HashMap<>();

    private DoomTeleporterRegistry() {
    }

    public static void clear(ServerWorld world) {
        TELEPORTERS.remove(world.getRegistryKey());
    }

    public static void set(ServerWorld world, List<DoomTeleporterTrigger> triggers) {
        TELEPORTERS.put(world.getRegistryKey(), new ArrayList<>(triggers));
    }

    public static List<DoomTeleporterTrigger> get(ServerWorld world) {
        return TELEPORTERS.getOrDefault(world.getRegistryKey(), List.of());
    }
}

