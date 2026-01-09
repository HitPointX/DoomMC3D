package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.doomai.DoomBossTags;
import com.hitpo.doommc3d.doomai.DoomMobTags;
import com.hitpo.doommc3d.interact.DoomLevelBoundsRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

public final class DoomVanillaSpawnSuppressor {
    private DoomVanillaSpawnSuppressor() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(DoomVanillaSpawnSuppressor::onEntityLoad);
    }

    private static void onEntityLoad(Entity entity, ServerWorld world) {
        Box bounds = DoomLevelBoundsRegistry.get(world);
        if (bounds == null) {
            return;
        }
        if (!bounds.contains(entity.getX(), entity.getY(), entity.getZ())) {
            return;
        }
        if (!(entity instanceof MobEntity mob)) {
            return;
        }
        if (entity instanceof PlayerEntity) {
            return;
        }
        if (isAllowedDoomEntity(mob)) {
            return;
        }
        mob.discard();
    }

    private static boolean isAllowedDoomEntity(MobEntity mob) {
        var tags = mob.getCommandTags();
        return tags.contains(DoomThingSpawner.TAG_SPAWNED)
            || tags.contains(DoomMobTags.MOB)
            || tags.contains(DoomBossTags.BOSS);
    }
}
