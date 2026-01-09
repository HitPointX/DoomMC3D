package com.hitpo.doommc3d.client.doomgfx;

import java.nio.ByteBuffer;
import net.minecraft.client.texture.NativeImage;

public final class DoomPatchDecoder {
    private DoomPatchDecoder() {
    }

    public static NativeImage decode(ByteBuffer patchLump, int[] paletteArgb) {
        ByteBuffer buf = patchLump.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int width = Short.toUnsignedInt(buf.getShort(0));
        int height = Short.toUnsignedInt(buf.getShort(2));
        int headerSize = 8 + width * 4;
        if (width <= 0 || height <= 0 || buf.capacity() < headerSize) {
            throw new IllegalArgumentException("Invalid patch lump");
        }

        NativeImage image = new NativeImage(width, height, true);
        image.fillRect(0, 0, width, height, 0x00000000);

        int columnOfsBase = 8;
        for (int x = 0; x < width; x++) {
            int columnOffset = buf.getInt(columnOfsBase + x * 4);
            if (columnOffset < 0 || columnOffset >= buf.capacity()) {
                continue;
            }
            int pos = columnOffset;
            while (pos < buf.capacity()) {
                int topDelta = Byte.toUnsignedInt(buf.get(pos++));
                if (topDelta == 255) {
                    break;
                }
                if (pos + 1 >= buf.capacity()) {
                    break;
                }
                int length = Byte.toUnsignedInt(buf.get(pos++));
                pos++; // unused
                for (int i = 0; i < length && pos < buf.capacity(); i++) {
                    int y = topDelta + i;
                    int colorIndex = Byte.toUnsignedInt(buf.get(pos++));
                    if (y >= 0 && y < height && colorIndex < paletteArgb.length) {
                        image.setColorArgb(x, y, paletteArgb[colorIndex]);
                    }
                }
                pos++; // unused
            }
        }

        return image;
    }
}

