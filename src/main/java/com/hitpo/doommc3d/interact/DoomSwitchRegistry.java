package com.hitpo.doommc3d.interact;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class DoomSwitchRegistry {
    private static final Map<RegistryKey<World>, Map<BlockPos, DoomSwitchInfo>> SWITCHES = new HashMap<>();

    private DoomSwitchRegistry() {
    }

    public static void clear(ServerWorld world) {
        SWITCHES.remove(world.getRegistryKey());
    }

    public static void register(ServerWorld world, BlockPos switchPos, DoomSwitchInfo info) {
        SWITCHES.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>()).put(switchPos.toImmutable(), info);
    }

    public static DoomSwitchInfo get(ServerWorld world, BlockPos switchPos) {
        Map<BlockPos, DoomSwitchInfo> map = SWITCHES.get(world.getRegistryKey());
        if (map == null) {
            return null;
        }
        return map.get(switchPos);
    }
}

