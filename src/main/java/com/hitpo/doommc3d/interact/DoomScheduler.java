package com.hitpo.doommc3d.interact;

import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public final class DoomScheduler {
    private static final Map<RegistryKey<World>, PriorityQueue<Scheduled>> QUEUES = new ConcurrentHashMap<>();

    private DoomScheduler() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DoomScheduler::tickWorld);
    }

    public static void clear(ServerWorld world) {
        QUEUES.remove(world.getRegistryKey());
    }

    public static void schedule(ServerWorld world, int delayTicks, Runnable action) {
        if (delayTicks <= 0) {
            action.run();
            return;
        }
        long due = world.getTime() + delayTicks;
        QUEUES
            .computeIfAbsent(world.getRegistryKey(), k -> new PriorityQueue<>(Comparator.comparingLong(Scheduled::dueTick)))
            .add(new Scheduled(due, action));
    }

    private static void tickWorld(ServerWorld world) {
        PriorityQueue<Scheduled> q = QUEUES.get(world.getRegistryKey());
        if (q == null || q.isEmpty()) {
            return;
        }
        long now = world.getTime();
        while (!q.isEmpty() && q.peek().dueTick <= now) {
            Scheduled s = q.poll();
            try {
                s.action.run();
            } catch (Throwable ignored) {
            }
        }
    }

    private record Scheduled(long dueTick, Runnable action) {
    }
}
