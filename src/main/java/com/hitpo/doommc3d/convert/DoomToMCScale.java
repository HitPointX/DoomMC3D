package com.hitpo.doommc3d.convert;

import com.hitpo.doommc3d.DoomConstants;

public final class DoomToMCScale {
    public static int toBlock(int doomUnits) {
        return Math.floorDiv(doomUnits, DoomConstants.DOOM_TO_MC_SCALE);
    }

    public static int toHeight(int doomUnits) {
        return Math.floorDiv(doomUnits, DoomConstants.DOOM_TO_MC_SCALE);
    }

    private DoomToMCScale() {
    }
}
