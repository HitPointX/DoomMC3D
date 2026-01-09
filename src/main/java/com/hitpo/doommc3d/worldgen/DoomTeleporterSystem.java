package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.interact.DoomTeleporterRegistry;
import com.hitpo.doommc3d.interact.DoomTeleporterTrigger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class DoomTeleporterSystem {
    private static final Map<UUID, Integer> COOLDOWN_UNTIL_TICK = new HashMap<>();

    private DoomTeleporterSystem() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DoomTeleporterSystem::tickWorld);
    }

    public static void clearPlayerCache() {
        COOLDOWN_UNTIL_TICK.clear();
    }

    private static void tickWorld(ServerWorld world) {
        var teleporters = DoomTeleporterRegistry.get(world);
        if (teleporters.isEmpty()) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            tickPlayer(world, player, teleporters);
        }
    }

    private static void tickPlayer(ServerWorld world, ServerPlayerEntity player, java.util.List<DoomTeleporterTrigger> teleporters) {
        int now = (int) world.getTime();
        int until = COOLDOWN_UNTIL_TICK.getOrDefault(player.getUuid(), 0);
        if (now < until) {
            return;
        }

        Box p = player.getBoundingBox().expand(0.15, 0.2, 0.15);
        for (DoomTeleporterTrigger trigger : teleporters) {
            if (!p.intersects(trigger.box())) {
                continue;
            }
            COOLDOWN_UNTIL_TICK.put(player.getUuid(), now + 12);
            doTeleport(world, player, trigger);
            break;
        }
    }

    private static void doTeleport(ServerWorld world, ServerPlayerEntity player, DoomTeleporterTrigger trigger) {
        Vec3d from = player.getEntityPos();
        Vec3d to = trigger.destPos();

        spawnFx(world, from);
        player.teleport(world, to.x, to.y, to.z, Set.<PositionFlag>of(), trigger.destYaw(), player.getPitch(), false);
        player.setVelocity(0, 0, 0);
        spawnFx(world, to);
    }

    private static void spawnFx(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.2, pos.z, 24, 0.35, 0.25, 0.35, 0.02);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.6, pos.z, 18, 0.25, 0.25, 0.25, 0.02);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.9f, 1.0f);
    }
}
