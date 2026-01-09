package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.doomai.DoomMobType;
import com.hitpo.doommc3d.doomai.DoomMobTags;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DoomDeathWatcher {
    private static final Map<RegistryKey<World>, Map<UUID, Snapshot>> PREV = new HashMap<>();

    private DoomDeathWatcher() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DoomDeathWatcher::tickWorld);
    }

    private static void tickWorld(ServerWorld world) {
        RegistryKey<World> key = world.getRegistryKey();
        Map<UUID, Snapshot> prev = PREV.computeIfAbsent(key, k -> new HashMap<>());

        // Collect current doom mob snapshots in a large area (covers loaded chunks)
        Box b = new Box(-1e6, -1e6, -1e6, 1e6, 1e6, 1e6);
        Map<UUID, Snapshot> curr = new HashMap<>();
        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, b, m -> m.getCommandTags().contains(DoomMobTags.MOB))) {
            DoomMobType type = DoomMobType.ZOMBIEMAN;
            for (DoomMobType t : DoomMobType.values()) {
                if (mob.getCommandTags().contains(DoomMobTags.tagForType(t))) { type = t; break; }
            }
            curr.put(mob.getUuid(), new Snapshot(mob.getX(), mob.getY(), mob.getZ(), type));
        }

        // Find deaths: present in prev but not in curr
        for (Map.Entry<UUID, Snapshot> e : prev.entrySet()) {
            if (!curr.containsKey(e.getKey())) {
                Snapshot s = e.getValue();
                DoomMobDrops.spawnDropsForType(world, s.type, s.x, s.y, s.z);
            }
        }

        PREV.put(key, curr);
    }

    private static final class Snapshot {
        final double x, y, z;
        final DoomMobType type;
        Snapshot(double x, double y, double z, DoomMobType type) { this.x = x; this.y = y; this.z = z; this.type = type; }
    }
}
