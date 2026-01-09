package com.hitpo.doommc3d.client.audio;

import com.hitpo.doommc3d.wad.WadDirectoryEntry;
import com.hitpo.doommc3d.wad.WadFile;
import com.hitpo.doommc3d.wad.WadLoader;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;

public final class DoomMusicPlayer {
    private static Sequencer sequencer;
    private static String currentLump;

    private DoomMusicPlayer() {
    }

    public static void playForMap(String mapName) {
        String lump = DoomMusicNames.lumpForMap(mapName);
        if (lump == null) {
            return;
        }
        try {
            WadFile wad = WadLoader.loadFirstIwad();
            playLump(wad, lump);
        } catch (Exception e) {
            System.err.println("[DoomMC3D] Failed to start music for map " + mapName + ": " + e.getMessage());
        }
    }

    public static void playLump(WadFile wad, String lumpName) throws Exception {
        String key = lumpName.toUpperCase(Locale.ROOT);
        if (key.equals(currentLump)) {
            return;
        }
        WadDirectoryEntry entry = find(wad, key);
        if (entry == null) {
            System.err.println("[DoomMC3D] Music lump not found: " + key);
            return;
        }
        ByteBuffer data = wad.readLump(entry);
        byte[] musBytes = new byte[data.remaining()];
        data.get(musBytes);
        byte[] midiBytes = DoomMusToMidi.convert(musBytes);
        Sequence seq = MidiSystem.getSequence(new ByteArrayInputStream(midiBytes));

        Sequencer s = getSequencer();
        s.stop();
        s.setSequence(seq);
        s.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
        s.start();
        currentLump = key;
        System.out.println("[DoomMC3D] Now playing " + key);
    }

    public static void stop() {
        try {
            if (sequencer != null) {
                sequencer.stop();
            }
        } catch (Exception ignored) {
        }
        currentLump = null;
    }

    private static Sequencer getSequencer() throws Exception {
        if (sequencer != null) {
            return sequencer;
        }
        sequencer = MidiSystem.getSequencer();
        sequencer.open();
        return sequencer;
    }

    private static WadDirectoryEntry find(WadFile wad, String name) {
        for (WadDirectoryEntry entry : wad.getDirectory()) {
            if (name.equals(entry.getName())) {
                return entry;
            }
        }
        return null;
    }
}

