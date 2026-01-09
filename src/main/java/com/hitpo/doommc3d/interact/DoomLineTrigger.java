package com.hitpo.doommc3d.interact;

/**
 * A "special line" trigger (use/shoot) represented in world-space.
 */
public record DoomLineTrigger(
    int id,
    Type type,
    double x1,
    double z1,
    double x2,
    double z2,
    DoomTriggerAction action,
    boolean once,
    int cooldownTicks
) {
    public enum Type {
        USE,
        SHOOT
    }
}
