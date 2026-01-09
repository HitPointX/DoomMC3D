package com.hitpo.doommc3d.util;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class DebugLogger {
    private static final boolean ENABLED;
    private static final Map<String, Long> LAST_PRINT_MS = new ConcurrentHashMap<>();

    static {
        boolean enabled = false;
        try {
            String prop = System.getProperty("doommc3d.debug");
            if (prop != null && prop.equalsIgnoreCase("true")) enabled = true;
            String env = System.getenv("DOOMMC3D_DEBUG");
            if (!enabled && env != null && env.equalsIgnoreCase("true")) enabled = true;
            if (!enabled) {
                // allow passing a mod-specific JVM arg to enable debug (avoid matching generic --debug)
                for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                    if ("--doommc3d-debug".equals(a)) { enabled = true; break; }
                }
            }
        } catch (Throwable t) {
            enabled = false;
        }
        ENABLED = enabled;
    }

    private DebugLogger() { }

    public static boolean isEnabled() { return ENABLED; }

    public static void debug(String key, Supplier<String> msgSupplier) {
        if (!ENABLED) return;
        try {
            System.out.println(msgSupplier.get());
        } catch (Throwable ignored) { }
    }

    public static void debugThrottled(String key, long minIntervalMs, Supplier<String> msgSupplier) {
        if (!ENABLED) return;
        long now = System.currentTimeMillis();
        Long last = LAST_PRINT_MS.get(key);
        if (last != null && now - last < minIntervalMs) return;
        LAST_PRINT_MS.put(key, now);
        try {
            System.out.println(msgSupplier.get());
        } catch (Throwable ignored) { }
    }
}
