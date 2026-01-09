package com.hitpo.doommc3d.doomai;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import com.hitpo.doommc3d.interact.DoomSectorGraph;
import com.hitpo.doommc3d.interact.DoomSectorGraphRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.entity.ai.goal.GoalSelector;
import com.hitpo.doommc3d.mixin.MobEntityAccessor;

public final class DoomMobSystem {
    private static final Map<RegistryKey<World>, Map<UUID, DoomMobBrain>> MOBS_BY_WORLD = new HashMap<>();

    private DoomMobSystem() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DoomMobSystem::tickWorld);
    }

    public static void attach(MobEntity mob, DoomMobType type) {
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            return;
        }
        mob.addCommandTag(DoomMobTags.MOB);
        mob.addCommandTag(DoomMobTags.tagForType(type));
        DoomMobBrain brain = new DoomMobBrain(type);
        brain.applyTuning(mob);
        stripVanillaAi(mob);
        brains(sw).put(mob.getUuid(), brain);
    }

    /**
     * Doom-like sound propagation.
     *
     * If a sector graph exists for the built map, we flood through connected sectors
     * and respect sound-blocking lines (ML_SOUNDBLOCK). Otherwise, fall back to a
     * simple radius alert.
     */
    public static void alertSound(ServerWorld world, Vec3d sourcePos, double radiusBlocks) {
        DoomSectorGraph graph = DoomSectorGraphRegistry.get(world);
        var reachable = graph == null ? null : graph.floodSoundReachable(graph.findSectorIndex(sourcePos));

        double r = Math.max(0.0, radiusBlocks);
        double scan = Math.max(r, 256.0);
        if (scan <= 0.01) {
            scan = 64.0;
        }
        Box box = new Box(
            sourcePos.x - scan,
            sourcePos.y - scan,
            sourcePos.z - scan,
            sourcePos.x + scan,
            sourcePos.y + scan,
            sourcePos.z + scan
        );

        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, box, m -> m.getCommandTags().contains(DoomMobTags.MOB))) {
            // Doom ambush ("deaf") flag: ignore sound wake-ups.
            if (mob.getCommandTags().contains(com.hitpo.doommc3d.worldgen.DoomThingSpawner.TAG_AMBUSH)) {
                continue;
            }

            if (reachable != null) {
                int mobSector = graph.findSectorIndex(new Vec3d(mob.getX(), mob.getY(), mob.getZ()));
                if (!reachable.contains(mobSector)) {
                    continue;
                }
            } else {
                // Fallback: approximate by radius.
                if (r > 0.01 && mob.squaredDistanceTo(sourcePos) > r * r) {
                    continue;
                }
            }

            DoomMobBrain brain = brains(world).get(mob.getUuid());
            if (brain == null) {
                DoomMobType type = readType(mob);
                if (type == null) {
                    continue;
                }
                brain = new DoomMobBrain(type);
                brain.applyTuning(mob);
                stripVanillaAi(mob);
                brains(world).put(mob.getUuid(), brain);
            }
            brain.alertBySound();
        }
    }

    private static void tickWorld(ServerWorld world) {
        // Auto-attach tagged mobs near players (loaded from disk).
        if (world.getTime() % 40 == 0) {
            for (var player : world.getPlayers()) {
                Box box = player.getBoundingBox().expand(128);
                for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, box, m -> m.getCommandTags().contains(DoomMobTags.MOB))) {
                    Map<UUID, DoomMobBrain> map = brains(world);
                    if (!map.containsKey(mob.getUuid())) {
                        DoomMobType type = readType(mob);
                        if (type == null) {
                            continue;
                        }
                        DoomMobBrain brain = new DoomMobBrain(type);
                        brain.applyTuning(mob);
                        stripVanillaAi(mob);
                        map.put(mob.getUuid(), brain);
                    }
                }
            }
        }

        Map<UUID, DoomMobBrain> map = MOBS_BY_WORLD.get(world.getRegistryKey());
        if (map == null || map.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, DoomMobBrain>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DoomMobBrain> entry = it.next();
            MobEntity mob = (MobEntity) world.getEntity(entry.getKey());
            if (mob == null || !mob.isAlive()) {
                it.remove();
                continue;
            }
            entry.getValue().tick(world, mob);
        }
    }

    private static Map<UUID, DoomMobBrain> brains(ServerWorld world) {
        return MOBS_BY_WORLD.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>());
    }

    private static void stripVanillaAi(MobEntity mob) {
        // Keep vanilla animation ticking but remove goal/target logic so custom Doom brain drives movement.
        mob.setAiDisabled(false);
        MobEntityAccessor accessor = (MobEntityAccessor) mob;
        clearSelector(accessor.getGoalSelector());
        clearSelector(accessor.getTargetSelector());
        mob.getNavigation().stop();
    }

    private static void clearSelector(GoalSelector selector) {
        try {
            selector.getGoals().clear();
        } catch (UnsupportedOperationException ignored) {
            // Fallback: nothing else to do.
        }
    }

    private static DoomMobType readType(MobEntity mob) {
        for (DoomMobType t : DoomMobType.values()) {
            if (mob.getCommandTags().contains(DoomMobTags.tagForType(t))) {
                return t;
            }
        }
        return null;
    }
}

