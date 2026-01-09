package com.hitpo.doommc3d.client.audio;

import com.hitpo.doommc3d.wad.WadDirectoryEntry;
import com.hitpo.doommc3d.wad.WadFile;
import com.hitpo.doommc3d.wad.WadRepository;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

public final class DoomSfxPlayer {
    private static final int DEFAULT_SAMPLE_RATE = 11025;

    private static final Map<String, Integer> BUFFERS_BY_LUMP = new HashMap<>();
    private static final List<Integer> ACTIVE_SOURCES = new ArrayList<>();

    private DoomSfxPlayer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tickCleanup());
    }

    public static void playAt(String lumpName, double x, double y, double z, float volume, float pitch) {
        if (lumpName == null || lumpName.isBlank()) {
            System.out.println("[DoomSfxPlayer] ERROR: lumpName is null or blank");
            return;
        }

        System.out.println("[DoomSfxPlayer] Attempting to play: " + lumpName + " at (" + x + ", " + y + ", " + z + ")");

        // Ensure we only touch OpenAL on the render/client thread.
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread()) {
            client.execute(() -> playAt(lumpName, x, y, z, volume, pitch));
            return;
        }

        try {
            Integer bufferId = BUFFERS_BY_LUMP.get(normalize(lumpName));
            if (bufferId == null) {
                System.out.println("[DoomSfxPlayer] Loading lump: " + lumpName);
                bufferId = loadToOpenAlBuffer(lumpName);
                if (bufferId == null) {
                    System.out.println("[DoomSfxPlayer] ERROR: Failed to load lump: " + lumpName);
                    return;
                }
                BUFFERS_BY_LUMP.put(normalize(lumpName), bufferId);
                System.out.println("[DoomSfxPlayer] Successfully loaded and cached: " + lumpName);
            } else {
                System.out.println("[DoomSfxPlayer] Using cached buffer for: " + lumpName);
            }

            // ====== DOOM-STYLE 2D AUDIO ======
            // Compute Doom-accurate stereo separation and distance attenuation
            float pan = 0.0f;
            double vol = 1.0;
            double dist = 0.0;
            
            if (client.player != null) {
                // World delta (X/Z only - Doom ignores vertical)
                double dx = x - client.player.getX();
                double dz = z - client.player.getZ();
                dist = Math.hypot(dx, dz);  // 2D distance in blocks
                
                // Player weapon sounds: always centered (sound is from the player)
                if (dist < 0.5) {
                    // Sound at or very near player position - no panning, full volume
                    pan = 0.0f;
                    vol = 1.0;
                    System.out.println("[DoomSfxPlayer] Player weapon sound (centered, dist=" + String.format("%.2f", dist) + ")");
                } else {
                    // External sound source: apply Doom-style positioning
                    
                    // Player yaw in radians (MC: 0=south, 90=west, 180=north, 270=east)
                    double yawRad = Math.toRadians(client.player.getYaw());
                    
                    // Rotate world delta into player-facing space
                    // Forward = +Z in listener space, Right = +X in listener space
                    double sin = Math.sin(yawRad);
                    double cos = Math.cos(yawRad);
                    double right = dx * cos - dz * sin;
                    double forward = dx * sin + dz * cos;
                    
                    // Doom-style stereo separation (0-255, center at 128)
                    double ang = Math.atan2(right, forward);  // -pi..pi, right positive
                    double swing = 0.75;  // Doom-ish swing (tune 0.6..0.9)
                    int sep = (int) Math.round(128 + 128 * swing * Math.sin(ang));
                    sep = Math.max(0, Math.min(255, sep));  // Clamp
                    pan = (sep - 128) / 128.0f;  // Convert to OpenAL pan (-1..+1)
                    
                    // Doom-style distance attenuation (close distance + clipping distance)
                    double closeDist = 2.5;   // Full volume within 2.5 blocks
                    double clipDist = 18.75;  // Inaudible beyond 18.75 blocks
                    
                    if (dist <= closeDist) {
                        vol = 1.0;
                    } else if (dist >= clipDist) {
                        vol = 0.0;
                    } else {
                        vol = 1.0 - ((dist - closeDist) / (clipDist - closeDist));
                        vol = vol * vol;  // Doom-ish curve (slightly steeper falloff)
                    }
                    
                    System.out.printf(
                        "[DoomSfxPlayer] right=%.2f forward=%.2f ang=%.2f sep=%d pan=%.2f dist=%.2f vol=%.2f%n",
                        right, forward, ang, sep, pan, dist, vol
                    );
                }
            }
            
            // Skip silent sounds
            if (vol <= 0.0) {
                System.out.println("[DoomSfxPlayer] Sound too far away, skipping");
                return;
            }
            
            int sourceId = AL10.alGenSources();
            int err = AL10.alGetError();
            if (err != AL10.AL_NO_ERROR) {
                System.err.println("[DoomSfxPlayer] OpenAL error creating source: " + err);
                return;
            }
            
            AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferId);
            AL10.alSourcef(sourceId, AL10.AL_GAIN, (float) (volume * vol));  // Apply Doom volume
            AL10.alSourcef(sourceId, AL10.AL_PITCH, Math.max(0.01f, pitch));
            
            // Disable OpenAL distance model - we compute volume ourselves
            AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 0.0f);
            
            // Listener-relative stereo positioning (not world 3D)
            AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(sourceId, AL10.AL_POSITION, pan, 0.0f, 1.0f);  // Pan on X, fixed Z=1
            AL10.alSource3f(sourceId, AL10.AL_VELOCITY, 0f, 0f, 0f);

            AL10.alSourcePlay(sourceId);
            err = AL10.alGetError();
            if (err != AL10.AL_NO_ERROR) {
                System.err.println("[DoomSfxPlayer] OpenAL error playing source: " + err);
                AL10.alDeleteSources(sourceId);
                return;
            }
            
            ACTIVE_SOURCES.add(sourceId);
            System.out.println("[DoomSfxPlayer] Playing sound, source ID: " + sourceId);
        } catch (Throwable t) {
            System.err.println("[DoomSfxPlayer] ERROR playing sound: " + lumpName);
            t.printStackTrace();
        }
    }

    private static void tickCleanup() {
        if (ACTIVE_SOURCES.isEmpty()) {
            return;
        }
        for (int i = ACTIVE_SOURCES.size() - 1; i >= 0; i--) {
            int sourceId = ACTIVE_SOURCES.get(i);
            int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
            if (state == AL10.AL_STOPPED) {
                AL10.alDeleteSources(sourceId);
                ACTIVE_SOURCES.remove(i);
            }
        }
    }

    private static Integer loadToOpenAlBuffer(String lumpName) {
        ByteBuffer lump = readLumpBytes(lumpName);
        if (lump == null || lump.remaining() < 8) {
            return null;
        }

        DmxSound decoded = decodeDmxSound(lump);
        if (decoded == null || decoded.pcm == null || decoded.pcm.remaining() == 0) {
            return null;
        }

        int bufferId = AL10.alGenBuffers();
        AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO8, decoded.pcm, decoded.sampleRate);
        return bufferId;
    }

    private static ByteBuffer readLumpBytes(String lumpName) {
        WadFile wad;
        try {
            wad = WadRepository.getOrLoad(null);
        } catch (IOException e) {
            System.err.println("[DoomSfxPlayer] ERROR: Failed to load WAD: " + e.getMessage());
            return null;
        }

        String target = normalize(lumpName);
        System.out.println("[DoomSfxPlayer] Searching for lump: " + target);
        WadDirectoryEntry entry = null;
        for (WadDirectoryEntry e : wad.getDirectory()) {
            if (normalize(e.getName()).equals(target)) {
                entry = e;
                break;
            }
        }
        if (entry == null) {
            System.out.println("[DoomSfxPlayer] ERROR: Lump not found: " + target);
            System.out.println("[DoomSfxPlayer] Available DS lumps:");
            for (WadDirectoryEntry e : wad.getDirectory()) {
                if (e.getName().toUpperCase().startsWith("DS")) {
                    System.out.println("  - " + e.getName());
                }
            }
            return null;
        }
        System.out.println("[DoomSfxPlayer] Found lump: " + entry.getName() + " (" + entry.getSize() + " bytes)");
        return wad.readLump(entry);
    }

    private static DmxSound decodeDmxSound(ByteBuffer lump) {
        // Doom DMX SFX header (common):
        // uint16 format (typically 3)
        // uint16 sampleRate
        // int32 sampleCount
        // followed by unsigned 8-bit PCM samples.
        // We keep this tolerant because PWADs/ports sometimes vary.
        ByteBuffer buf = lump.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN);

        int format = Short.toUnsignedInt(buf.getShort());
        int sampleRate = Short.toUnsignedInt(buf.getShort());
        int sampleCount = buf.getInt();

        boolean headerLooksValid = (format == 3 || format == 0) && sampleRate >= 8000 && sampleRate <= 48000 && sampleCount > 0;
        if (!headerLooksValid) {
            // Fallback: treat the whole lump as raw mono8 at a default rate.
            ByteBuffer pcm = BufferUtils.createByteBuffer(lump.remaining());
            pcm.put(lump.duplicate());
            pcm.flip();
            return new DmxSound(DEFAULT_SAMPLE_RATE, pcm);
        }

        int available = Math.max(0, buf.remaining());
        int len = Math.min(sampleCount, available);
        if (len <= 0) {
            return null;
        }
        ByteBuffer pcm = BufferUtils.createByteBuffer(len);
        // Copy only the declared sample count.
        ByteBuffer slice = buf.slice();
        slice.limit(len);
        pcm.put(slice);
        pcm.flip();

        return new DmxSound(sampleRate > 0 ? sampleRate : DEFAULT_SAMPLE_RATE, pcm);
    }

    private static String normalize(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }

    private record DmxSound(int sampleRate, ByteBuffer pcm) {
    }
}
