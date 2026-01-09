package com.hitpo.doommc3d.interact;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public record DoomTeleporterTrigger(int id, Box box, Vec3d destPos, float destYaw) {
}

