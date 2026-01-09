package com.hitpo.doommc3d.interact;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public final class DoomSectorGraphRegistry {
    private static final Map<RegistryKey<World>, DoomSectorGraph> GRAPHS = new ConcurrentHashMap<>();

    private DoomSectorGraphRegistry() {
    }

    public static void clear(ServerWorld world) {
        GRAPHS.remove(world.getRegistryKey());
    }

    public static void set(ServerWorld world, DoomSectorGraph graph) {
        GRAPHS.put(world.getRegistryKey(), graph);
    }

    public static DoomSectorGraph get(ServerWorld world) {
        return GRAPHS.get(world.getRegistryKey());
    }
}
