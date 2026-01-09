package com.hitpo.doommc3d.wad;

import com.hitpo.doommc3d.DoomConstants;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class WadLoader {
    private WadLoader() {
    }

    public static Path getWadsDirectory() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        return gameDir.resolve("mods").resolve(DoomConstants.MOD_ID).resolve("wads");
    }

    public static List<Path> getWadsDirectories() {
        Set<Path> dirs = new LinkedHashSet<>();
        Path primary = getWadsDirectory();
        dirs.add(primary);

        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path devReference = gameDir.getParent() == null ? null : gameDir.getParent().resolve("reference").resolve("WADS");
        if (devReference != null && Files.isDirectory(devReference)) {
            dirs.add(devReference);
        }
        return List.copyOf(dirs);
    }

    public static WadFile loadFirstIwad() throws IOException {
        return load(null);
    }

    public static WadFile load(String requestedName) throws IOException {
        List<Path> wadsDirs = getWadsDirectories();
        Files.createDirectories(getWadsDirectory());
        List<Path> candidates = wadsDirs.stream()
            .flatMap(dir -> {
                try {
                    return listWadFiles(dir).stream();
                } catch (IOException e) {
                    return java.util.stream.Stream.empty();
                }
            })
            .sorted(Comparator.naturalOrder())
            .collect(Collectors.toList());

        if (requestedName != null && !requestedName.isBlank()) {
            Path explicit = matchByName(candidates, requestedName);
            if (explicit == null) {
                throw new IOException("Requested WAD '" + requestedName + "' not found in " + wadsDirs);
            }
            WadFile wad = new WadFile(explicit);
            if (!wad.isValidHeader()) {
                throw new IOException("Requested WAD is not valid: " + explicit);
            }
            return wad;
        }
        WadFile pwadCandidate = null;
        for (Path candidate : candidates) {
            WadFile wad = new WadFile(candidate);
            if (wad.isIwad()) {
                return wad;
            }
            if ("PWAD".equals(wad.getIdentification()) && pwadCandidate == null) {
                pwadCandidate = wad;
            }
        }
        if (pwadCandidate != null) {
            return pwadCandidate;
        }
        throw new IOException("No IWAD found in " + wadsDirs);
    }

    private static List<Path> listWadFiles(Path wadsDir) throws IOException {
        if (!Files.isDirectory(wadsDir)) {
            return List.of();
        }
        try (var stream = Files.list(wadsDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wad"))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        }
    }

    private static Path matchByName(List<Path> candidates, String requestedName) {
        return candidates.stream()
            .filter(path -> path.getFileName().toString().equalsIgnoreCase(requestedName))
            .findFirst()
            .orElse(null);
    }
}
