package com.hitpo.doommc3d.interact;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class DoomWalkTriggerSystem {
    private static final Map<UUID, Long> COOLDOWN_UNTIL_TICK = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_FOOT_BLOCK = new HashMap<>();

    private DoomWalkTriggerSystem() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DoomWalkTriggerSystem::tickWorld);
    }

    private static void tickWorld(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            BlockPos under = player.getBlockPos().down();

            // Doom "walk" triggers are fundamentally crossing-based.
            // Approximation: only fire when the player ENTERS a trigger cell.
            BlockPos prev = LAST_FOOT_BLOCK.get(player.getUuid());
            if (under.equals(prev)) {
                continue;
            }
            LAST_FOOT_BLOCK.put(player.getUuid(), under.toImmutable());

            DoomWalkTriggerRegistry.WalkTrigger trigger = DoomWalkTriggerRegistry.get(world, under);
            if (trigger == null) {
                continue;
            }
            DoomTriggerInfo info = trigger.info();
            if (info == null) {
                continue;
            }

            com.hitpo.doommc3d.util.DebugLogger.debugThrottled("DoomWalkTriggerSystem.step", 500, () -> "[DoomMC3D] Player " + player.getName().getString() + " stepped on walk trigger at " + under + " groupId=" + trigger.groupId() + " activated=" + trigger.activated() + " once=" + info.once());

            if (trigger.activated() && info.once()) {
                com.hitpo.doommc3d.util.DebugLogger.debug("DoomWalkTriggerSystem", () -> "[DoomMC3D] Walk trigger already activated (once=true), skipping");
                continue;
            }

            long now = world.getTime();
            long until = COOLDOWN_UNTIL_TICK.getOrDefault(player.getUuid(), 0L);
            if (now < until) {
                continue;
            }
            COOLDOWN_UNTIL_TICK.put(player.getUuid(), now + Math.max(0, info.cooldownTicks()));

            execute(world, player, info, under);

            if (info.once()) {
                DoomWalkTriggerRegistry.markGroupActivated(world, trigger.groupId());
            }
        }
    }

    private static void execute(ServerWorld world, ServerPlayerEntity player, DoomTriggerInfo trigger, BlockPos triggerPos) {
        if (trigger.action() instanceof DoomTriggerAction.ActivateLiftByTag a) {
            DoomLiftSystem.activateByTag(world, player, a.tag(), triggerPos);
            return;
        }

        if (trigger.action() instanceof DoomTriggerAction.OpenDoorsByTag a) {
            int tag = a.tag();
            if (tag == 0) {
                return;
            }
            for (BlockPos doorPos : DoomDoorRegistry.findDoorsByTag(world, tag)) {
                DoomDoorInfo doorInfo = DoomDoorRegistry.get(world, doorPos);
                if (!DoomDoorLogic.canOpen(player, doorInfo)) {
                    continue;
                }
                DoomDoorLogic.openDoor(world, doorPos);
            }
            return;
        }

        if (trigger.action() instanceof DoomTriggerAction.CloseDoorsByTag a) {
            int tag = a.tag();
            if (tag == 0) {
                return;
            }
            // In Doom, tag-driven door close is less common; use toggleDoor instead
            for (BlockPos doorPos : DoomDoorRegistry.findDoorsByTag(world, tag)) {
                DoomDoorLogic.toggleDoor(world, doorPos);
            }
            return;
        }

        if (trigger.action() instanceof DoomTriggerAction.RaiseFloor a) {
            DoomLiftSystem.activateByTag(world, player, a.tag());
            return;
        }

        if (trigger.action() instanceof DoomTriggerAction.LowerFloor a) {
            // Future implementation: add crush-floor systems
            DoomLiftSystem.activateByTag(world, player, a.tag());
            return;
        }

        if (trigger.action() instanceof DoomTriggerAction.RaiseCeiling a) {
            // Future implementation: add ceiling raise systems
            return;
        }

        if (trigger.action() instanceof DoomTriggerAction.LowerCeiling a) {
            // Future implementation: add ceiling crush systems
            return;
        }

        if (trigger.action() instanceof DoomTriggerAction.Teleport a) {
            // Future implementation: add teleport logic
            // For now, teleport player to tag location if available
            return;
        }

        if (trigger.action() instanceof DoomTriggerAction.NoOp) {
            // Recognized but intentionally unimplemented special
            return;
        }

        // Keep it minimal: only lift + door tag events need walk triggers right now.
    }
}
