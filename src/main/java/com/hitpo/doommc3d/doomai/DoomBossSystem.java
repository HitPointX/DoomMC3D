package com.hitpo.doommc3d.doomai;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.entity.ai.goal.GoalSelector;
import com.hitpo.doommc3d.mixin.MobEntityAccessor;

public final class DoomBossSystem {
    private static final Map<UUID, DoomBossBrain> BOSSES = new HashMap<>();

    private DoomBossSystem() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DoomBossSystem::tickWorld);
    }

    public static void attach(MobEntity mob, DoomBossType type) {
        mob.addCommandTag(DoomBossTags.BOSS);
        mob.addCommandTag(type == DoomBossType.CYBERDEMON ? DoomBossTags.CYBER : DoomBossTags.SPIDER);
        DoomBossBrain brain = new DoomBossBrain(type);
        brain.applyBossTuning(mob);
        stripVanillaAi(mob);
        BOSSES.put(mob.getUuid(), brain);
    }

    private static void tickWorld(ServerWorld world) {
        // Auto-attach any tagged boss mobs near players (e.g., loaded from disk).
        if (world.getTime() % 40 == 0) {
            for (var player : world.getPlayers()) {
                Box box = player.getBoundingBox().expand(128);
                for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, box, m -> m.getCommandTags().contains(DoomBossTags.BOSS))) {
                    if (!BOSSES.containsKey(mob.getUuid())) {
                        DoomBossType type = mob.getCommandTags().contains(DoomBossTags.CYBER) ? DoomBossType.CYBERDEMON : DoomBossType.SPIDER_MASTERMIND;
                        DoomBossBrain brain = new DoomBossBrain(type);
                        brain.applyBossTuning(mob);
                        stripVanillaAi(mob);
                        BOSSES.put(mob.getUuid(), brain);
                    }
                }
            }
        }

        Iterator<Map.Entry<UUID, DoomBossBrain>> it = BOSSES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DoomBossBrain> entry = it.next();
            MobEntity mob = (MobEntity) world.getEntity(entry.getKey());
            if (mob == null || !mob.isAlive()) {
                it.remove();
                continue;
            }
            entry.getValue().tick(world, mob);
        }
    }

    private static void stripVanillaAi(MobEntity mob) {
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
        }
    }
}
