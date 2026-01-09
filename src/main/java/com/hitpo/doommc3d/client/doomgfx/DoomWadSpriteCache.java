package com.hitpo.doommc3d.client.doomgfx;

import com.hitpo.doommc3d.DoomMC3D;
import com.hitpo.doommc3d.wad.WadDirectoryEntry;
import com.hitpo.doommc3d.wad.WadFile;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

public final class DoomWadSpriteCache {
    private static final AtomicBoolean LOGGED_ERROR = new AtomicBoolean(false);

    private final Map<String, DoomWadSprite> sprites = new HashMap<>();
    private volatile int[] paletteArgb;

    public DoomWadSprite getOrLoad(WadFile wad, String lumpName) {
        String key = lumpName.toUpperCase(Locale.ROOT);
        DoomWadSprite cached = sprites.get(key);
        if (cached != null) {
            return cached;
        }

        try {
            WadDirectoryEntry entry = find(wad, key);
            if (entry == null) {
                return null;
            }
            int[] palette = getPaletteArgb(wad);
            ByteBuffer lump = wad.readLump(entry);

            ByteBuffer header = lump.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            int width = Short.toUnsignedInt(header.getShort(0));
            int height = Short.toUnsignedInt(header.getShort(2));
            int leftOffset = header.getShort(4);
            int topOffset = header.getShort(6);

            NativeImage image = DoomPatchDecoder.decode(lump, palette);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> DoomMC3D.MOD_ID + ":" + key, image);
            Identifier id = Identifier.of(DoomMC3D.MOD_ID, "doom_patch/" + key.toLowerCase(Locale.ROOT));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            texture.upload();

            // Use header width/height for offsets consistency (should match decoded image).
            DoomWadSprite sprite = new DoomWadSprite(id, width, height, leftOffset, topOffset);
            sprites.put(key, sprite);
            return sprite;
        } catch (Exception e) {
            if (LOGGED_ERROR.compareAndSet(false, true)) {
                System.err.println("[DoomMC3D] Failed to load DOOM patch '" + key + "' from " + wad.getSource());
                e.printStackTrace();
            }
            return null;
        }
    }

    private int[] getPaletteArgb(WadFile wad) {
        int[] local = paletteArgb;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (paletteArgb != null) {
                return paletteArgb;
            }
            int[] palette = new int[256];
            WadDirectoryEntry playpal = find(wad, "PLAYPAL");
            if (playpal == null) {
                for (int i = 0; i < 256; i++) {
                    palette[i] = 0xFF000000 | (i << 16) | (i << 8) | i;
                }
                paletteArgb = palette;
                return palette;
            }
            ByteBuffer buf = wad.readLump(playpal);
            if (buf.remaining() < 256 * 3) {
                for (int i = 0; i < 256; i++) {
                    palette[i] = 0xFF000000 | (i << 16) | (i << 8) | i;
                }
                paletteArgb = palette;
                return palette;
            }
            for (int i = 0; i < 256; i++) {
                int r = Byte.toUnsignedInt(buf.get(i * 3));
                int g = Byte.toUnsignedInt(buf.get(i * 3 + 1));
                int b = Byte.toUnsignedInt(buf.get(i * 3 + 2));
                palette[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
            paletteArgb = palette;
            return palette;
        }
    }

    private WadDirectoryEntry find(WadFile wad, String name) {
        for (WadDirectoryEntry entry : wad.getDirectory()) {
            if (name.equals(entry.getName())) {
                return entry;
            }
        }
        return null;
    }

    public record DoomWadSprite(Identifier texture, int width, int height, int leftOffset, int topOffset) {
    }
}

