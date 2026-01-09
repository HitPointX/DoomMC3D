package com.hitpo.doommc3d.client.lighting;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ClientExtralightManager {
    private static int extralightTicks = 0;
    private static final int MAX_TICKS = 12;

    public static void flash(int ticks) {
        if (ticks <= 0) return;
        extralightTicks = Math.max(extralightTicks, Math.min(ticks, MAX_TICKS));
    }

    public static void tick() {
        if (extralightTicks > 0) extralightTicks--;
    }

    public static boolean isActive() {
        return extralightTicks > 0;
    }

    public static int getTicks() { return extralightTicks; }

    // Returns a scaled extralight value (0..max) based on remaining ticks.
    public static int scaledExtra(int max) {
        if (extralightTicks <= 0) return 0;
        return (int) Math.ceil((extralightTicks / (double) MAX_TICKS) * max);
    }
}
