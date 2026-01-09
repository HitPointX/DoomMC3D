package com.hitpo.doommc3d.interact;

import net.minecraft.util.math.BlockPos;

public sealed interface DoomTriggerAction {
    record ExitNextMap() implements DoomTriggerAction {
    }

    record ToggleDoorAt(BlockPos doorLowerPos) implements DoomTriggerAction {
    }

    record ToggleDoorsByTag(int tag) implements DoomTriggerAction {
    }

    record ActivateLiftByTag(int tag) implements DoomTriggerAction {
    }

    /**
     * Classic Doom tag-targeted door open. Used for many "monster closet" events.
     *
     * In the vanilla engine, the linedef tag selects one or more door sectors.
     * In our Minecraft approximation, we resolve the tag to registered Doom door blocks.
     */
    record OpenDoorsByTag(int tag) implements DoomTriggerAction {
    }

    /**
     * Raise floor to neighboring ceiling height.
     * Doom special 23: Floor Lower (W1)
     * Maps to platform that raises until it hits neighbor ceiling.
     */
    record RaiseFloor(int tag) implements DoomTriggerAction {
    }

    /**
     * Lower floor to neighboring floor height.
     * Doom special 25: Ceiling Crush & Raise (W1)
     * Often used for crush pads.
     */
    record LowerFloor(int tag) implements DoomTriggerAction {
    }

    /**
     * Raise ceiling upward.
     * Doom special 38/44: Ceiling Raise To Highest (W1/G1)
     */
    record RaiseCeiling(int tag) implements DoomTriggerAction {
    }

    /**
     * Lower ceiling downward (crush trap).
     * Doom special 40: Ceiling Lower To Floor (W1)
     */
    record LowerCeiling(int tag) implements DoomTriggerAction {
    }

    /**
     * Teleport to exit point.
     * Doom special 39: Teleport (W1, SRD)
     * Requires exit tag.
     */
    record Teleport(int destinationTag, boolean reverseYaw) implements DoomTriggerAction {
    }

    /**
     * Close door by tag.
     * Doom special 3: Door Close (W1)
     */
    record CloseDoorsByTag(int tag) implements DoomTriggerAction {
    }

    /**
     * Do nothing (placeholder for recognized but unimplemented specials).
     */
    record NoOp() implements DoomTriggerAction {
    }
}
