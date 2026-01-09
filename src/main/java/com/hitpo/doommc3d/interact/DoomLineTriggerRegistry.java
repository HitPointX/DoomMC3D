package com.hitpo.doommc3d.interact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public final class DoomLineTriggerRegistry {
    private static final Map<RegistryKey<World>, List<DoomLineTrigger>> TRIGGERS = new ConcurrentHashMap<>();
    private static final Map<RegistryKey<World>, Map<Integer, Integer>> LAST_FIRED_TICK = new ConcurrentHashMap<>();
    private static final Map<RegistryKey<World>, Map<Integer, Boolean>> CONSUMED = new ConcurrentHashMap<>();

    private DoomLineTriggerRegistry() {
    }

    public static void clear(ServerWorld world) {
        TRIGGERS.remove(world.getRegistryKey());
        LAST_FIRED_TICK.remove(world.getRegistryKey());
        CONSUMED.remove(world.getRegistryKey());
    }

    public static void set(ServerWorld world, List<DoomLineTrigger> triggers) {
        TRIGGERS.put(world.getRegistryKey(), List.copyOf(triggers));
        LAST_FIRED_TICK.put(world.getRegistryKey(), new HashMap<>());
        CONSUMED.put(world.getRegistryKey(), new HashMap<>());
    }

    public static List<DoomLineTrigger> getAll(ServerWorld world) {
        return TRIGGERS.getOrDefault(world.getRegistryKey(), List.of());
    }

    public static boolean isConsumed(ServerWorld world, int triggerId) {
        return CONSUMED.getOrDefault(world.getRegistryKey(), Map.of()).getOrDefault(triggerId, false);
    }

    public static void markConsumed(ServerWorld world, int triggerId) {
        CONSUMED.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>()).put(triggerId, true);
    }

    public static int getLastFiredTick(ServerWorld world, int triggerId) {
        return LAST_FIRED_TICK.getOrDefault(world.getRegistryKey(), Map.of()).getOrDefault(triggerId, Integer.MIN_VALUE);
    }

    public static void setLastFiredTick(ServerWorld world, int triggerId, int tick) {
        LAST_FIRED_TICK.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>()).put(triggerId, tick);
    }
}
