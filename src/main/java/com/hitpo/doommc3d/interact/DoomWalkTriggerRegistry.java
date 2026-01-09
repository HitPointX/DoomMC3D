package com.hitpo.doommc3d.interact;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class DoomWalkTriggerRegistry {
    private static final Map<RegistryKey<World>, Map<BlockPos, WalkTrigger>> WALK_TRIGGERS = new HashMap<>();

    private DoomWalkTriggerRegistry() {
    }

    public static void clear(ServerWorld world) {
        WALK_TRIGGERS.remove(world.getRegistryKey());
    }

    public static void register(ServerWorld world, BlockPos pos, WalkTrigger trigger) {
        WALK_TRIGGERS.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>())
            .put(pos.toImmutable(), trigger);
    }

    public static WalkTrigger get(ServerWorld world, BlockPos pos) {
        Map<BlockPos, WalkTrigger> map = WALK_TRIGGERS.get(world.getRegistryKey());
        if (map == null) {
            return null;
        }
        return map.get(pos);
    }

    public static void markGroupActivated(ServerWorld world, int groupId) {
        Map<BlockPos, WalkTrigger> map = WALK_TRIGGERS.get(world.getRegistryKey());
        if (map == null) {
            return;
        }
        for (Map.Entry<BlockPos, WalkTrigger> e : map.entrySet()) {
            WalkTrigger t = e.getValue();
            if (t.groupId() == groupId) {
                e.setValue(t.withActivated(true));
            }
        }
    }

    public record WalkTrigger(int groupId, DoomTriggerInfo info, boolean activated) {
        public WalkTrigger withActivated(boolean value) {
            return new WalkTrigger(groupId, info, value);
        }
    }
}
