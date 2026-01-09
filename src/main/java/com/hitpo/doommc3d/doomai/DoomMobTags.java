package com.hitpo.doommc3d.doomai;

public final class DoomMobTags {
    public static final String MOB = "doommc3d_mob";
    public static final String TYPE_PREFIX = "doommc3d_type_";

    private DoomMobTags() {
    }

    public static String tagForType(DoomMobType type) {
        return TYPE_PREFIX + type.name().toLowerCase();
    }
}

