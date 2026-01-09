package com.hitpo.doommc3d.interact;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public final class DoomLevelBoundsRegistry {
    private static final Map<RegistryKey<World>, Box> BOUNDS = new HashMap<>();

    private DoomLevelBoundsRegistry() {
    }

    public static void clear(ServerWorld world) {
        BOUNDS.remove(world.getRegistryKey());
    }

    public static void set(ServerWorld world, Box box) {
        BOUNDS.put(world.getRegistryKey(), box);
    }

    public static Box get(ServerWorld world) {
        return BOUNDS.get(world.getRegistryKey());
    }
}

