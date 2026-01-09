package com.hitpo.doommc3d.worldgen;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks Doom exploding barrel hit points for TNT blocks placed by {@link ThingPlacer}.
 *
 * <p>Barrels are blocks, so once destroyed they persist as AIR in the world. We only
 * need to remember partially-damaged barrels.</p>
 */
public final class DoomBarrelRegistry {
    public static final int BARREL_HP = 20;

    private static final Map<RegistryKey<World>, Map<Long, Integer>> HP_BY_WORLD = new ConcurrentHashMap<>();

    private DoomBarrelRegistry() {
    }

    public static void clear(ServerWorld world) {
        HP_BY_WORLD.remove(world.getRegistryKey());
    }

    public static int getHp(ServerWorld world, BlockPos pos) {
        Map<Long, Integer> hp = HP_BY_WORLD.get(world.getRegistryKey());
        if (hp == null) {
            return BARREL_HP;
        }
        return hp.getOrDefault(pos.asLong(), BARREL_HP);
    }

    /**
     * Applies damage and returns the new HP (0 means destroyed).
     */
    public static int applyDamage(ServerWorld world, BlockPos pos, int damage) {
        if (damage <= 0) {
            return getHp(world, pos);
        }

        Map<Long, Integer> hp = HP_BY_WORLD.computeIfAbsent(world.getRegistryKey(), key -> new ConcurrentHashMap<>());
        long key = pos.asLong();
        int newHp = Math.max(0, hp.getOrDefault(key, BARREL_HP) - damage);
        if (newHp == 0) {
            hp.remove(key);
        } else {
            hp.put(key, newHp);
        }
        return newHp;
    }
}
