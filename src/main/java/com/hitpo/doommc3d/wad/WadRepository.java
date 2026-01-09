package com.hitpo.doommc3d.wad;

import java.io.IOException;

public final class WadRepository {
    private static volatile WadFile cached;

    private WadRepository() {
    }

    public static WadFile getOrLoad(String requestedName) throws IOException {
        if (requestedName != null && !requestedName.isBlank()) {
            return WadLoader.load(requestedName);
        }
        WadFile local = cached;
        if (local != null) {
            return local;
        }
        synchronized (WadRepository.class) {
            if (cached == null) {
                cached = WadLoader.loadFirstIwad();
            }
            return cached;
        }
    }

    public static void clearCache() {
        cached = null;
    }
}
