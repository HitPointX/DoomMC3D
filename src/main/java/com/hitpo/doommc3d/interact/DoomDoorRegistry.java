package com.hitpo.doommc3d.interact;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

public final class DoomDoorRegistry {
    private static final Map<RegistryKey<World>, Map<BlockPos, DoomDoorInfo>> DOORS_BY_WORLD = new ConcurrentHashMap<>();

    private DoomDoorRegistry() {
    }

    public static void register(ServerWorld world, BlockPos lowerPos, DoomDoorInfo info) {
        DOORS_BY_WORLD
            .computeIfAbsent(world.getRegistryKey(), key -> new ConcurrentHashMap<>())
            .put(lowerPos.toImmutable(), info);
    }

    public static DoomDoorInfo get(ServerWorld world, BlockPos pos) {
        Map<BlockPos, DoomDoorInfo> doors = DOORS_BY_WORLD.get(world.getRegistryKey());
        if (doors == null) {
            return null;
        }
        DoomDoorInfo direct = doors.get(pos);
        if (direct != null) {
            return direct;
        }
        return doors.get(pos.down());
    }

    public static void clear(ServerWorld world) {
        DOORS_BY_WORLD.remove(world.getRegistryKey());
    }

    public static List<BlockPos> findDoorsByTag(ServerWorld world, int tag) {
        if (tag == 0) {
            return List.of();
        }
        Map<BlockPos, DoomDoorInfo> doors = DOORS_BY_WORLD.get(world.getRegistryKey());
        if (doors == null || doors.isEmpty()) {
            return List.of();
        }
        List<BlockPos> out = new ArrayList<>();
        for (Map.Entry<BlockPos, DoomDoorInfo> e : doors.entrySet()) {
            DoomDoorInfo info = e.getValue();
            if (info != null && info.tag() == tag) {
                out.add(e.getKey());
            }
        }
        return out;
    }
}

