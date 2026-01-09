package com.hitpo.doommc3d.interact;

public record DoomTriggerInfo(DoomTriggerAction action, boolean once, int cooldownTicks) {
}
