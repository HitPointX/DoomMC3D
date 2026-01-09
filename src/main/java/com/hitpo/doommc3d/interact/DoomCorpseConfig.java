package com.hitpo.doommc3d.interact;

import com.hitpo.doommc3d.doomai.DoomMobType;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public final class DoomCorpseConfig {
    private static final Properties PROPS = new Properties();

    static {
        // Try external config first (config/doommc3d_corpse.properties), else fall back to bundled resource
        Path external = Path.of("config", "doommc3d_corpse.properties");
        try (InputStream in = Files.exists(external) ? new FileInputStream(external.toFile()) : DoomCorpseConfig.class.getClassLoader().getResourceAsStream("doommc3d_corpse.properties")) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (Exception e) {
            // ignore and keep defaults
        }
    }

    private DoomCorpseConfig() {}

    public static int getTicksFor(DoomMobType type, int fallback) {
        String key = type.name().toUpperCase(Locale.ROOT) + ".ticks";
        return Integer.parseInt(PROPS.getProperty(key, String.valueOf(PROPS.getProperty("DEFAULT.ticks", String.valueOf(fallback)))));
    }

    public static double getDragFor(DoomMobType type, double fallback) {
        String key = type.name().toUpperCase(Locale.ROOT) + ".drag";
        return Double.parseDouble(PROPS.getProperty(key, PROPS.getProperty("DEFAULT.drag", String.valueOf(fallback))));
    }
}
