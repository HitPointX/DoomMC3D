package com.hitpo.doommc3d.client.audio;

import com.hitpo.doommc3d.wad.WadDirectoryEntry;
import com.hitpo.doommc3d.wad.WadFile;
import com.hitpo.doommc3d.wad.WadLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays Doom sound effects directly from WAD lumps using Java AudioSystem.
 * Similar to DoomMusicPlayer but for sound effects (DMX format).
 */
public final class DoomSoundPlayer {
    private static final Map<String, byte[]> SOUND_CACHE = new ConcurrentHashMap<>();
    
    private DoomSoundPlayer() {
    }

    /**
     * Play a Doom sound effect at the player's position.
     * @param soundName The sound lump name (e.g., "DSPISTOL", "DSSHTGN")
     * @param volume Volume multiplier (0.0 to 1.0)
     * @param pitch Pitch multiplier (typically 1.0)
     */
    public static void playSound(String soundName, float volume, float pitch) {
        playSound(soundName, null, volume, pitch);
    }

    /**
     * Play a Doom sound effect at a specific position.
     * @param soundName The sound lump name (e.g., "DSPISTOL", "DSSHTGN")
     * @param position World position (null for player position)
     * @param volume Volume multiplier (0.0 to 1.0)
     * @param pitch Pitch multiplier (typically 1.0)
     */
    public static void playSound(String soundName, Vec3d position, float volume, float pitch) {
        try {
            // Normalize sound name to WAD format (add DS prefix if needed)
            String lumpName = soundName.toUpperCase(Locale.ROOT);
            if (!lumpName.startsWith("DS")) {
                lumpName = "DS" + lumpName;
            }

            // Get sound data from cache or load from WAD
            byte[] wavData = SOUND_CACHE.computeIfAbsent(lumpName, DoomSoundPlayer::loadSoundFromWad);
            
            if (wavData == null) {
                System.err.println("[DoomMC3D] Sound not found: " + lumpName);
                return;
            }

            // Calculate volume based on distance if position is provided
            float finalVolume = volume;
            if (position != null) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    Vec3d playerPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
                    double distance = playerPos.distanceTo(position);
                    // Simple distance attenuation (16 block range)
                    finalVolume = (float) Math.max(0, volume * (1.0 - distance / 16.0));
                }
            }

            // Play the sound on a separate thread to avoid blocking
            if (finalVolume > 0.01f) {
                float finalVolumeForThread = finalVolume;
                float finalPitch = pitch;
                new Thread(() -> playSoundData(wavData, finalVolumeForThread, finalPitch), "DoomSFX-" + lumpName).start();
            }
            
        } catch (Exception e) {
            System.err.println("[DoomMC3D] Failed to play sound " + soundName + ": " + e.getMessage());
        }
    }

    /**
     * Load a sound from the WAD file and convert to WAV format.
     */
    private static byte[] loadSoundFromWad(String lumpName) {
        try {
            WadFile wad = WadLoader.loadFirstIwad();
            WadDirectoryEntry entry = findLump(wad, lumpName);
            
            if (entry == null) {
                return null;
            }

            ByteBuffer data = wad.readLump(entry);
            return convertDoomSoundToWav(data);
            
        } catch (Exception e) {
            System.err.println("[DoomMC3D] Failed to load sound " + lumpName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Convert Doom DMX sound format to WAV format in memory.
     * Based on Chocolate Doom's i_sdlsound.c implementation.
     */
    private static byte[] convertDoomSoundToWav(ByteBuffer data) throws Exception {
        if (data.remaining() < 8) {
            throw new Exception("Sound data too small");
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
        
        // Read raw 8-bit PCM data (unsigned, 128 = silence)
        byte[] pcmData = new byte[sampleCount];
        data.get(pcmData);
        
        // Convert unsigned (0-255, center=128) to signed (-128 to 127, center=0)
        for (int i = 0; i < pcmData.length; i++) {
            pcmData[i] = (byte) ((pcmData[i] & 0xFF) - 128);
        }

        // Create WAV format header
        AudioFormat format = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate,
            8,  // 8-bit
            1,  // Mono
            1,  // Frame size
            sampleRate,
            false  // Little endian
        );

        // Create audio input stream from PCM data
        ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
        AudioInputStream ais = new AudioInputStream(bais, format, pcmData.length);
        
        // Convert to WAV byte array
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, baos);
        
        return baos.toByteArray();
    }

    /**
     * Play WAV data from memory using Java AudioSystem.
     */
    private static void playSoundData(byte[] wavData, float volume, float pitch) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(wavData);
            AudioInputStream ais = AudioSystem.getAudioInputStream(bais);
            
            // Get audio format and prepare for playback
            AudioFormat baseFormat = ais.getFormat();
            
            // Apply pitch adjustment if needed
            AudioFormat targetFormat = baseFormat;
            if (Math.abs(pitch - 1.0f) > 0.01f) {
                targetFormat = new AudioFormat(
                    baseFormat.getEncoding(),
                    baseFormat.getSampleRate() * pitch,  // Adjust sample rate for pitch
                    baseFormat.getSampleSizeInBits(),
                    baseFormat.getChannels(),
                    baseFormat.getFrameSize(),
                    baseFormat.getFrameRate() * pitch,
                    baseFormat.isBigEndian()
                );
            }
            
            // Get a line to play the sound
            DataLine.Info info = new DataLine.Info(Clip.class, targetFormat);
            Clip clip = (Clip) AudioSystem.getLine(info);
            
            // Apply volume control
            clip.open(ais);
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                // Convert linear volume to decibels (logarithmic scale)
                float dB = (float) (Math.log(Math.max(0.0001, volume)) / Math.log(10.0) * 20.0);
                dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
                gainControl.setValue(dB);
            }
            
            // Play and dispose when done
            clip.start();
            
            // Clean up after playback
            new Thread(() -> {
                try {
                    // Wait for clip to finish
                    while (clip.isRunning()) {
                        Thread.sleep(10);
                    }
                    clip.close();
                } catch (InterruptedException ignored) {
                }
            }, "DoomSFX-Cleanup").start();
            
        } catch (Exception e) {
            System.err.println("[DoomMC3D] Error playing sound data: " + e.getMessage());
        }
    }

    /**
     * Find a lump in the WAD directory.
     */
    private static WadDirectoryEntry findLump(WadFile wad, String name) {
        for (WadDirectoryEntry entry : wad.getDirectory()) {
            if (name.equals(entry.getName())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Clear the sound cache.
     */
    public static void clearCache() {
        SOUND_CACHE.clear();
    }
}
