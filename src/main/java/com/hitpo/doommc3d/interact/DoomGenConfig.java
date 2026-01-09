package com.hitpo.doommc3d.interact;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class DoomGenConfig {
    private static final Properties PROPS = new Properties();

    static {
        Path external = Path.of("config", "doommc3d_gen.properties");
        try (InputStream in = Files.exists(external) ? new FileInputStream(external.toFile()) : DoomGenConfig.class.getClassLoader().getResourceAsStream("doommc3d_gen.properties")) {
            if (in != null) PROPS.load(in);
        } catch (Exception e) {
            // ignore, use defaults
        }
    }

    private DoomGenConfig() {}

    public static int getBaseY() {
        return Integer.parseInt(PROPS.getProperty("BASE_Y", "160"));
    }

    public static int getMaxWorldHeight() {
        return Integer.parseInt(PROPS.getProperty("MAX_WORLD_HEIGHT", "320"));
    }
}
