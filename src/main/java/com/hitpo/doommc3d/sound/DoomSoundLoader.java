package com.hitpo.doommc3d.sound;

import com.hitpo.doommc3d.wad.WadDirectoryEntry;
import com.hitpo.doommc3d.wad.WadFile;
import com.hitpo.doommc3d.wad.WadRepository;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads Doom sound effects from WAD files and converts them to playable formats.
 * 
 * Doom WAD sound format:
 * - Sounds start with "DS" prefix (e.g., DSPISTOL, DSSHTGN)
 * - Format: 3-byte header + 16-bit sample rate + raw 8-bit PCM data
 * - Header: [0x00, 0x00, format] where format is usually 0x00 or 0x03
 * - Sample rate at bytes 2-3 (little endian, typically 11025 or 22050 Hz)
 * - Audio data starts at byte 8
 * 
 * See Chocolate Doom's i_sdlsound.c for reference implementation.
 */
public class DoomSoundLoader {
    private static final Map<String, Path> SOUND_CACHE = new HashMap<>();
    private static Path soundCacheDir = null;
    
    /**
     * Initialize the sound cache directory.
     * Extract sounds ONE TIME to source directory during development.
     * After extraction, sounds must be committed and jar rebuilt.
     */
    public static void init(Path gameDir) {
        try {
            // For development: extract to build/resources/main (will be in jar after build)
            // Simpler: just extract to run/ for now and handle resource loading separately
            soundCacheDir = gameDir.resolve("extracted_sounds");
            Files.createDirectories(soundCacheDir);
            
            System.out.println("[DoomMC3D] Sound cache directory: " + soundCacheDir);
            System.out.println("[DoomMC3D] NOTE: Sounds must be manually copied to src/main/resources/assets/doommc3d/sounds/ and rebuilt");
        } catch (Exception e) {
            System.err.println("[DoomMC3D] Failed to create sound cache directory: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Extract and convert all Doom sounds from the loaded WAD to .wav files.
     * This should be called once after WAD is loaded.
     */
    public static void extractAllSounds() {
        if (soundCacheDir == null) {
            System.err.println("[DoomMC3D] Sound cache not initialized!");
            return;
        }
        
        WadFile wad;
        try {
            wad = WadRepository.getOrLoad(null);
        } catch (Exception e) {
            System.err.println("[DoomMC3D] Failed to load WAD for sound extraction: " + e.getMessage());
            return;
        }
        
        if (wad == null) {
            System.err.println("[DoomMC3D] No WAD loaded, cannot extract sounds");
            return;
        }
        
        int extracted = 0;
        int skipped = 0;
        System.out.println("[DoomMC3D] Extracting Doom sounds from WAD: " + wad.getSource());
        
        for (WadDirectoryEntry entry : wad.getDirectory()) {
            String name = entry.getName();
            
            // Doom sounds start with "DS" (e.g., DSPISTOL, DSSHTGN, DSPOPAIN)
            if (name.startsWith("DS") && name.length() > 2) {
                try {
                    String soundName = name.substring(2).toLowerCase(); // Remove "DS" prefix
                    Path outputFile = soundCacheDir.resolve(soundName + ".wav");
                    
                    // Skip if already extracted
                    if (Files.exists(outputFile)) {
                        SOUND_CACHE.put(soundName, outputFile);
                        skipped++;
                        continue;
                    }
                    
                    ByteBuffer lumpData = wad.readLump(entry);
                    if (convertDoomSoundToWav(lumpData, outputFile)) {
                        SOUND_CACHE.put(soundName, outputFile);
                        extracted++;
                        System.out.println("[DoomMC3D]   Extracted: " + soundName + " -> " + outputFile.getFileName());
                    }
                } catch (Exception e) {
                    System.err.println("[DoomMC3D] Failed to extract sound " + name + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        System.out.println("[DoomMC3D] Sound extraction complete:");
        System.out.println("[DoomMC3D]   Newly extracted: " + extracted);
        System.out.println("[DoomMC3D]   Already cached: " + skipped);
        System.out.println("[DoomMC3D]   Total available: " + SOUND_CACHE.size());
        System.out.println("[DoomMC3D]   Cache directory: " + soundCacheDir);
        if (!SOUND_CACHE.isEmpty()) {
            System.out.println("[DoomMC3D]   Sample sounds: " + 
                SOUND_CACHE.keySet().stream().limit(10).toList());
        }
    }
    
    /**
     * Convert a Doom sound lump to WAV format.
     * 
     * Doom sound format (from Chocolate Doom):
     * Bytes 0-1: Format (usually 0x0000 or 0x0003)
     * Bytes 2-3: Sample rate (little endian, e.g., 11025 Hz = 0x2B11)
     * Bytes 4-7: Sample count (little endian)
     * Bytes 8+: Raw 8-bit unsigned PCM data (128 = silence)
     */
    private static boolean convertDoomSoundToWav(ByteBuffer data, Path outputFile) {
        try {
            if (data.remaining() < 8) {
                return false;  // Too small to be valid
            }
            
            // Skip first 2 bytes (format marker)
            data.position(2);
            
            // Read sample rate (bytes 2-3, little endian)
            int sampleRate = data.getShort() & 0xFFFF;
            if (sampleRate == 0) {
                sampleRate = 11025;  // Default fallback
            }
            
            // Read sample count (bytes 4-7, little endian)
            int sampleCount = data.getInt();
            
            // Validate sample count
            if (sampleCount <= 0 || sampleCount > data.remaining()) {
                sampleCount = data.remaining();
            }
            
            // Read raw 8-bit PCM data
            byte[] pcmData = new byte[sampleCount];
            data.get(pcmData);
            
            // Convert 8-bit unsigned (0-255) to 8-bit signed (-128 to 127)
            // Doom uses 128 as silence (center), we need 0 as center
            for (int i = 0; i < pcmData.length; i++) {
                pcmData[i] = (byte) ((pcmData[i] & 0xFF) - 128);
            }
            
            // Create audio format: 8-bit mono PCM
            AudioFormat audioFormat = new AudioFormat(
                sampleRate,  // Sample rate
                8,           // Sample size in bits
                1,           // Channels (mono)
                true,        // Signed
                false        // Little endian
            );
            
            // Create audio input stream
            ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
            AudioInputStream audioStream = new AudioInputStream(bais, audioFormat, pcmData.length);
            
            // Write to WAV file
            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, outputFile.toFile());
            audioStream.close();
            
            return true;
        } catch (Exception e) {
            System.err.println("[DoomMC3D] Error converting sound to WAV: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the path to a cached sound file.
     * Returns null if sound hasn't been extracted.
     */
    public static Path getSoundPath(String soundName) {
        return SOUND_CACHE.get(soundName.toLowerCase());
    }
    
    /**
     * Check if a sound has been extracted.
     */
    public static boolean hasSound(String soundName) {
        return SOUND_CACHE.containsKey(soundName.toLowerCase());
    }
}
