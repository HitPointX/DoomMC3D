package com.hitpo.doommc3d.interact;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class DoomTriggerRegistry {
    private static final Map<RegistryKey<World>, Map<BlockPos, DoomTriggerInfo>> USE_TRIGGERS = new HashMap<>();

    private DoomTriggerRegistry() {
    }

    public static void clear(ServerWorld world) {
        USE_TRIGGERS.remove(world.getRegistryKey());
    }

    public static void registerUse(ServerWorld world, BlockPos pos, DoomTriggerInfo info) {
        USE_TRIGGERS.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>()).put(pos.toImmutable(), info);
    }

    public static DoomTriggerInfo getUse(ServerWorld world, BlockPos pos) {
        Map<BlockPos, DoomTriggerInfo> map = USE_TRIGGERS.get(world.getRegistryKey());
        if (map == null) {
            return null;
        }
        return map.get(pos);
    }
}
