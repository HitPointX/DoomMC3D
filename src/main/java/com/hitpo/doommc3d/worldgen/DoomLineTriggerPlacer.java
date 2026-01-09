package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.convert.DoomOrigin;
import com.hitpo.doommc3d.convert.DoomToMCScale;
import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.doommap.Linedef;
import com.hitpo.doommc3d.doommap.Vertex;
import com.hitpo.doommc3d.interact.DoomLineTrigger;
import com.hitpo.doommc3d.interact.DoomLineTriggerRegistry;
import com.hitpo.doommc3d.interact.DoomTriggerAction;
import com.hitpo.doommc3d.interact.TriggerActivation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Registers Doom "special lines" that trigger on use or by being shot.
 *
 * Comprehensive mapping of Doom linedef specials to trigger actions.
 * References vanilla Doom linedef special list for W1/WR/S1/SR/G1/GR activation types.
 */
public final class DoomLineTriggerPlacer {
    // Doom special constants organized by activation type and effect

    // ===== W1 (Walk Over Once) =====
    private static final int SPECIAL_W1_DOOR_OPEN = 1;           // Door Open
    private static final int SPECIAL_W1_DOOR_CLOSE = 3;          // Door Close
    private static final int SPECIAL_W1_FLOOR_RAISE = 23;        // Raise Floor
    private static final int SPECIAL_W1_PLATFORM_DOWN_UP = 24;   // Raise Platform (DownWaitUpStay)
    private static final int SPECIAL_W1_CEILING_CRUSH = 25;      // Ceiling Crush & Raise

    // ===== WR (Walk Over Repeatable) =====
    private static final int SPECIAL_WR_DOOR_OPEN = 2;           // Door Open
    private static final int SPECIAL_WR_DOOR_CLOSE = 4;          // Door Close
    private static final int SPECIAL_WR_FLOOR_RAISE = 27;        // Raise Floor (Repeatable)
    private static final int SPECIAL_WR_PLATFORM_DOWN_UP = 26;   // Raise Platform (Repeatable)
    private static final int SPECIAL_WR_LIGHT_OFF = 35;          // Light Off (not implemented)
    private static final int SPECIAL_WR_LIGHT_ON = 81;           // Light On (not implemented)

    // ===== S1 (Use Once) =====
    private static final int SPECIAL_S1_DOOR_OPEN = 31;          // Door Open
    private static final int SPECIAL_S1_DOOR_CLOSE = 32;         // Door Close
    private static final int SPECIAL_S1_FLOOR_RAISE = 60;        // Floor Raise
    private static final int SPECIAL_S1_PLATFORM_DOWN_UP = 47;   // Raise Platform
    private static final int SPECIAL_S1_EXIT = 11;               // Exit Next Map
    private static final int SPECIAL_S1_SECRET_EXIT = 51;        // Secret Exit

    // ===== SR (Use Repeatable) =====
    private static final int SPECIAL_SR_DOOR_OPEN = 33;          // Door Open
    private static final int SPECIAL_SR_DOOR_CLOSE = 34;         // Door Close
    private static final int SPECIAL_SR_FLOOR_RAISE = 61;        // Floor Raise
    private static final int SPECIAL_SR_PLATFORM_DOWN_UP = 48;   // Raise Platform
    private static final int SPECIAL_SR_LIGHT_OFF = 83;          // Light Off (not implemented)
    private static final int SPECIAL_SR_LIGHT_ON = 82;           // Light On (not implemented)

    // ===== G1 (Shoot Once) =====
    private static final int SPECIAL_G1_DOOR_OPEN = 46;          // Door Open
    private static final int SPECIAL_G1_FLOOR_RAISE = 62;        // Floor Raise

    // ===== GR (Shoot Repeatable) =====
    private static final int SPECIAL_GR_DOOR_OPEN = 45;          // Door Open
    private static final int SPECIAL_GR_FLOOR_RAISE = 63;        // Floor Raise
    private static final int SPECIAL_GR_LIGHT_OFF = 85;          // Light Off (not implemented)
    private static final int SPECIAL_GR_LIGHT_ON = 84;           // Light On (not implemented)

    // ===== M1 (Monster Cross Once) =====
    private static final int SPECIAL_M1_DOOR_OPEN = 50;          // Door Open
    private static final int SPECIAL_M1_PLATFORM_DOWN_UP = 37;   // Raise Platform

    // ===== Teleport (Multiple activation types) =====
    private static final int SPECIAL_W1_TELEPORT = 39;           // Teleport (W1, SRD)
    private static final int SPECIAL_GR_TELEPORT = 125;          // Teleport (GR, BOOM)

    // ===== Other / Future =====
    private static final int SPECIAL_POLYOBJ = 88;               // Polyobj Door Slide (not applicable)
    private static final int SPECIAL_POLYOBJ_ROTATE = 89;        // Polyobj Rotate (not applicable)

    private DoomLineTriggerPlacer() {
    }

    public static void place(ServerWorld world, DoomMap map, DoomOrigin origin, BlockPos buildOrigin) {
        List<DoomLineTrigger> triggers = new ArrayList<>();
        int id = 1;
        Vertex[] vertices = map.vertices();

        for (Linedef line : map.linedefs()) {
            int special = line.specialType();
            int tag = line.sectorTag();

            DoomLineTrigger.Type type = null;
            TriggerActivation activation = null;
            DoomTriggerAction action = null;
            int cooldown = 10;

            // Extract action type and activation from special number
            SpecialInfo info = getSpecialInfo(special, tag);
            if (info != null) {
                type = info.type;
                activation = info.activation;
                action = info.action;
                cooldown = info.cooldown;
            }

            if (type == null || action == null || activation == null) {
                continue;
            }

            Vertex v1 = vertices[line.startVertex()];
            Vertex v2 = vertices[line.endVertex()];

            int rx1 = DoomToMCScale.toBlock(v1.x()) - origin.originBlockX();
            int rz1 = origin.originBlockZ() - DoomToMCScale.toBlock(v1.y());
            int rx2 = DoomToMCScale.toBlock(v2.x()) - origin.originBlockX();
            int rz2 = origin.originBlockZ() - DoomToMCScale.toBlock(v2.y());

            double x1 = buildOrigin.getX() + rx1 + 0.5;
            double z1 = buildOrigin.getZ() + rz1 + 0.5;
            double x2 = buildOrigin.getX() + rx2 + 0.5;
            double z2 = buildOrigin.getZ() + rz2 + 0.5;

            triggers.add(new DoomLineTrigger(id++, type, x1, z1, x2, z2, action, activation.isOnce(), cooldown));
        }

        DoomLineTriggerRegistry.clear(world);
        DoomLineTriggerRegistry.set(world, triggers);
        com.hitpo.doommc3d.util.DebugLogger.debug("DoomLineTriggerPlacer", () -> "[DoomMC3D] Registered " + triggers.size() + " line triggers (Task #5: Extended Triggers)");
    }

    /**
     * Maps Doom linedef special number to action, activation type, and timing info.
     * Note: Walk-over triggers (W1, WR) are handled by DoomWalkTriggerRegistry,
     * not by DoomLineTrigger. This only returns USE and SHOOT trigger types.
     */
    private static SpecialInfo getSpecialInfo(int special, int tag) {
        DoomLineTrigger.Type type = null;
        TriggerActivation activation = null;
        DoomTriggerAction action = null;
        int cooldown = 10;

        // S1 (Use Once)
        if (special == SPECIAL_S1_DOOR_OPEN) {
            type = DoomLineTrigger.Type.USE;
            activation = TriggerActivation.USE_ONCE;
            action = new DoomTriggerAction.OpenDoorsByTag(tag);
            cooldown = 20;
        } else if (special == SPECIAL_S1_FLOOR_RAISE) {
            type = DoomLineTrigger.Type.USE;
            activation = TriggerActivation.USE_ONCE;
            action = new DoomTriggerAction.RaiseFloor(tag);
            cooldown = 20;
        } else if (special == SPECIAL_S1_PLATFORM_DOWN_UP) {
            type = DoomLineTrigger.Type.USE;
            activation = TriggerActivation.USE_ONCE;
            action = new DoomTriggerAction.ActivateLiftByTag(tag);
            cooldown = 20;
        } else if (special == SPECIAL_S1_EXIT) {
            type = DoomLineTrigger.Type.USE;
            activation = TriggerActivation.USE_ONCE;
            action = new DoomTriggerAction.ExitNextMap();
            cooldown = 20;
        } else if (special == SPECIAL_S1_SECRET_EXIT) {
            type = DoomLineTrigger.Type.USE;
            activation = TriggerActivation.USE_ONCE;
            action = new DoomTriggerAction.ExitNextMap();
            cooldown = 20;
        }
        // SR (Use Repeatable)
        else if (special == SPECIAL_SR_DOOR_OPEN) {
            type = DoomLineTrigger.Type.USE;
            activation = TriggerActivation.USE_REPEATABLE;
            action = new DoomTriggerAction.OpenDoorsByTag(tag);
            cooldown = 20;
        } else if (special == SPECIAL_SR_FLOOR_RAISE) {
            type = DoomLineTrigger.Type.USE;
            activation = TriggerActivation.USE_REPEATABLE;
            action = new DoomTriggerAction.RaiseFloor(tag);
            cooldown = 20;
        } else if (special == SPECIAL_SR_PLATFORM_DOWN_UP) {
            type = DoomLineTrigger.Type.USE;
            activation = TriggerActivation.USE_REPEATABLE;
            action = new DoomTriggerAction.ActivateLiftByTag(tag);
            cooldown = 20;
        }
        // G1 (Shoot Once)
        else if (special == SPECIAL_G1_DOOR_OPEN) {
            type = DoomLineTrigger.Type.SHOOT;
            activation = TriggerActivation.SHOOT_ONCE;
            action = new DoomTriggerAction.OpenDoorsByTag(tag);
            cooldown = 35;
        } else if (special == SPECIAL_G1_FLOOR_RAISE) {
            type = DoomLineTrigger.Type.SHOOT;
            activation = TriggerActivation.SHOOT_ONCE;
            action = new DoomTriggerAction.RaiseFloor(tag);
            cooldown = 35;
        }
        // GR (Shoot Repeatable)
        else if (special == SPECIAL_GR_DOOR_OPEN) {
            type = DoomLineTrigger.Type.SHOOT;
            activation = TriggerActivation.SHOOT_REPEATABLE;
            action = new DoomTriggerAction.OpenDoorsByTag(tag);
            cooldown = 35;
        } else if (special == SPECIAL_GR_FLOOR_RAISE) {
            type = DoomLineTrigger.Type.SHOOT;
            activation = TriggerActivation.SHOOT_REPEATABLE;
            action = new DoomTriggerAction.RaiseFloor(tag);
            cooldown = 35;
        } else if (special == SPECIAL_GR_TELEPORT) {
            type = DoomLineTrigger.Type.SHOOT;
            activation = TriggerActivation.SHOOT_REPEATABLE;
            action = new DoomTriggerAction.Teleport(tag, false);
            cooldown = 35;
        }
        // Polyobj (not applicable in Minecraft geometry)
        else if (special == SPECIAL_POLYOBJ || special == SPECIAL_POLYOBJ_ROTATE) {
            action = new DoomTriggerAction.NoOp();
        }

        if (type != null && action != null) {
            return new SpecialInfo(type, activation, action, cooldown);
        }
        return null;
    }

    private record SpecialInfo(DoomLineTrigger.Type type, TriggerActivation activation, DoomTriggerAction action, int cooldown) {
    }
}
