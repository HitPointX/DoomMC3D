package com.hitpo.doommc3d.interact;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public final class DoomLevelStateRegistry {
    private static final Map<RegistryKey<World>, DoomLevelState> STATE_BY_WORLD = new ConcurrentHashMap<>();

    private DoomLevelStateRegistry() {
    }

    public static void clear(ServerWorld world) {
        STATE_BY_WORLD.remove(world.getRegistryKey());
    }

    public static void set(ServerWorld world, DoomLevelState state) {
        STATE_BY_WORLD.put(world.getRegistryKey(), state);
    }

    public static DoomLevelState get(ServerWorld world) {
        return STATE_BY_WORLD.get(world.getRegistryKey());
    }
}
