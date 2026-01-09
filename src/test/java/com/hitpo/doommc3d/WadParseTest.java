package com.hitpo.doommc3d;

import com.hitpo.doommc3d.doommap.DoomMap;
import com.hitpo.doommc3d.wad.DoomMapParser;
import com.hitpo.doommc3d.wad.WadFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class WadParseTest {
    @Test
    public void parseE1M1FromReferenceWad() throws IOException {
        Path wadPath = Path.of("reference/WADS/DOOM.WAD");
        assertTrue(Files.exists(wadPath), "Missing reference WAD for tests");
        WadFile wadFile = new WadFile(wadPath);
        assertTrue(wadFile.isValidHeader(), "Reference WAD has invalid header");
        DoomMap doomMap = DoomMapParser.parse(wadFile, "E1M1");
        assertTrue(doomMap.vertices().length > 0, "No vertices read from E1M1");
        assertTrue(doomMap.sectors().length > 0, "E1M1 has no sectors");
    }
}
