package com.hitpo.doommc3d.client.audio;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DoomMusicNames {
    private static final Pattern E_M_PATTERN = Pattern.compile("^E(\\d)M(\\d)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAP_PATTERN = Pattern.compile("^MAP(\\d\\d)$", Pattern.CASE_INSENSITIVE);

    private static final String[] DOOM2 = {
        null,
        "D_RUNNIN", "D_STALKS", "D_COUNTD", "D_BETWEE", "D_DOOM", "D_THE_DA", "D_SHAWN", "D_DDTBLU",
        "D_IN_CIT", "D_DEAD", "D_STLKS2", "D_THEDA2", "D_DOOM2", "D_DDTBL2", "D_RUNNI2", "D_DEAD2",
        "D_STLKS3", "D_ROMERO", "D_SHAWN2", "D_MESSAG", "D_COUNT2", "D_DDTBL3", "D_AMPIE", "D_THEDA3",
        "D_ADRIAN", "D_MESSG2", "D_ROMER2", "D_TENSE", "D_SHAWN3", "D_OPENIN", "D_EVIL", "D_ULTIMA"
    };

    private DoomMusicNames() {
    }

    public static String lumpForMap(String mapName) {
        if (mapName == null) {
            return null;
        }
        String normalized = mapName.trim().toUpperCase(Locale.ROOT);

        Matcher em = E_M_PATTERN.matcher(normalized);
        if (em.matches()) {
            return "D_" + em.group(0);
        }

        Matcher map = MAP_PATTERN.matcher(normalized);
        if (map.matches()) {
            int num = Integer.parseInt(map.group(1));
            if (num >= 1 && num < DOOM2.length) {
                return DOOM2[num];
            }
        }

        return null;
    }
}

