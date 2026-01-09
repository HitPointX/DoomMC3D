package com.hitpo.doommc3d.interact;

import java.util.Locale;

public final class DoomMapProgression {
    private DoomMapProgression() {
    }

    public static String nextMapName(String current) {
        if (current == null || current.isBlank()) {
            return null;
        }
        String c = current.trim().toUpperCase(Locale.ROOT);

        // Doom 1 format: E#M#
        if (c.length() == 4 && c.charAt(0) == 'E' && c.charAt(2) == 'M') {
            int ep = digit(c.charAt(1));
            int map = digit(c.charAt(3));
            if (ep > 0 && map > 0) {
                int next = map + 1;
                if (next <= 9) {
                    return "E" + ep + "M" + next;
                }
            }
        }

        // Doom 2 format: MAP##
        if (c.startsWith("MAP") && c.length() == 5) {
            int tens = digit(c.charAt(3));
            int ones = digit(c.charAt(4));
            if (tens >= 0 && ones >= 0) {
                int n = tens * 10 + ones;
                int next = n + 1;
                if (next <= 99) {
                    return String.format(Locale.ROOT, "MAP%02d", next);
                }
            }
        }

        return null;
    }

    private static int digit(char c) {
        return (c >= '0' && c <= '9') ? (c - '0') : -1;
    }
}
