package com.hitpo.doommc3d.interact;

public record DoomDoorInfo(DoorKeyColor keyColor, int tag, int autoCloseTicks) {
    public boolean requiresKey() {
        return keyColor != null;
    }
}

