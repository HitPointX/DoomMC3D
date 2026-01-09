package com.hitpo.doommc3d.interact;

import com.hitpo.doommc3d.worldgen.DoomWorldBuilder;
import com.hitpo.doommc3d.state.DoomWorldState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.hitpo.doommc3d.net.PlayDoomSfxPayload;

public final class DoomTriggerInteractions {
    private static final Map<UUID, Integer> COOLDOWN_UNTIL_TICK = new HashMap<>();

    private DoomTriggerInteractions() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            if (!(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) {
                return ActionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);
            // If this is a lever handle triggers.
            if (state.isOf(Blocks.LEVER)) {
                DoomTriggerInfo info = DoomTriggerRegistry.getUse(sw, pos);
                if (info == null) {
                    return ActionResult.PASS;
                }

                System.out.println("[DoomMC3D] Lever activated at " + pos + " action: " + info.action());

                int now = (int) sw.getTime();
                int until = COOLDOWN_UNTIL_TICK.getOrDefault(sp.getUuid(), 0);
                if (now < until) {
                    return ActionResult.FAIL;
                }
                COOLDOWN_UNTIL_TICK.put(sp.getUuid(), now + Math.max(0, info.cooldownTicks()));

                execute(sw, sp, info);
                return ActionResult.SUCCESS;
            }

            // Not a lever: check if there's a Doom "use" trigger at this position.
            DoomTriggerInfo info = DoomTriggerRegistry.getUse(sw, pos);
            if (info != null) {
                int now = (int) sw.getTime();
                int until = COOLDOWN_UNTIL_TICK.getOrDefault(sp.getUuid(), 0);
                if (now < until) {
                    return ActionResult.FAIL;
                }
                COOLDOWN_UNTIL_TICK.put(sp.getUuid(), now + Math.max(0, info.cooldownTicks()));

                execute(sw, sp, info);
                return ActionResult.SUCCESS;
            }

            // No trigger here — if clicking a non-air block (a wall), play the player grunt SFX
            // to mimic vanilla Doom's "use wall with no effect" feedback.
            if (!state.isOf(Blocks.AIR)) {
                ServerPlayNetworking.send(sp, new PlayDoomSfxPayload("DSPLPAIN", sp.getX(), sp.getY(), sp.getZ(), 1.0f, 1.0f));
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });
    }

    public static void executeAction(ServerWorld world, ServerPlayerEntity player, DoomTriggerAction action) {
        execute(world, player, new DoomTriggerInfo(action, true, 0));
    }

    private static void execute(ServerWorld world, ServerPlayerEntity player, DoomTriggerInfo trigger) {
        if (trigger.action() instanceof DoomTriggerAction.ExitNextMap) {
            System.out.println("[DoomMC3D] ExitNextMap triggered!");
            DoomLevelState state = DoomLevelStateRegistry.get(world);
            String current = state == null ? null : state.mapName();
            System.out.println("[DoomMC3D] Current map: " + current);
            String next = DoomMapProgression.nextMapName(current);
            System.out.println("[DoomMC3D] Next map: " + next);
            if (next == null) {
                player.sendMessage(Text.literal("[DoomMC3D] Exit triggered, but next map is unknown (current=" + current + ")"), false);
                return;
            }
            String wadName = state == null ? null : state.wadFileName();
            player.sendMessage(Text.literal("[DoomMC3D] Exiting to " + next + "..."), false);
            DoomWorldBuilder.build(world, player, next, wadName);
            
            // Save this as the last map for auto-loading on rejoin
            DoomWorldState worldState = DoomWorldState.get(world.getServer().getOverworld());
            worldState.setLastMap(next);
            worldState.setCurrentLoadedMap(next);
            
            return;
        }

        if (trigger.action() instanceof DoomTriggerAction.ToggleDoorAt a) {
            DoomDoorInfo doorInfo = DoomDoorRegistry.get(world, a.doorLowerPos());
            if (!DoomDoorLogic.canOpen(player, doorInfo)) {
                player.sendMessage(Text.literal("[DoomMC3D] Door locked."), false);
                return;
            }
            DoomDoorLogic.toggleDoor(world, a.doorLowerPos());
            return;
        }

        if (trigger.action() instanceof DoomTriggerAction.ToggleDoorsByTag a) {
            int tag = a.tag();
            if (tag == 0) {
                return;
            }
            for (BlockPos doorPos : DoomDoorRegistry.findDoorsByTag(world, tag)) {
                DoomDoorInfo doorInfo = DoomDoorRegistry.get(world, doorPos);
                if (!DoomDoorLogic.canOpen(player, doorInfo)) {
                    continue;
                }
                DoomDoorLogic.toggleDoor(world, doorPos);
            }
            return;
        }

        if (trigger.action() instanceof DoomTriggerAction.ActivateLiftByTag a) {
            DoomLiftSystem.activateByTag(world, player, a.tag());
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
        }
    }
}
