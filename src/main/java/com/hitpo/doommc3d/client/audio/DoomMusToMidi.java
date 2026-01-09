package com.hitpo.doommc3d.client.audio;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class DoomMusToMidi {
    private static final int NUM_CHANNELS = 16;
    private static final int MIDI_PERCUSSION_CHAN = 9;
    private static final int MUS_PERCUSSION_CHAN = 15;

    private static final int[] CONTROLLER_MAP = {
        0x00, 0x20, 0x01, 0x07, 0x0A, 0x0B, 0x5B, 0x5D,
        0x40, 0x43, 0x78, 0x7B, 0x7E, 0x7F, 0x79
    };

    private static final byte[] MIDI_HEADER = {
        'M', 'T', 'h', 'd',
        0x00, 0x00, 0x00, 0x06,
        0x00, 0x00,
        0x00, 0x01,
        0x00, 0x46,
        'M', 'T', 'r', 'k',
        0x00, 0x00, 0x00, 0x00
    };

    private DoomMusToMidi() {
    }

    public static byte[] convert(byte[] musBytes) {
        ByteBuffer mus = ByteBuffer.wrap(musBytes).order(ByteOrder.LITTLE_ENDIAN);
        if (mus.remaining() < 16) {
            throw new IllegalArgumentException("MUS data too short");
        }
        byte[] id = new byte[4];
        mus.get(id);
        if (id[0] != 'M' || id[1] != 'U' || id[2] != 'S' || (id[3] & 0xFF) != 0x1A) {
            throw new IllegalArgumentException("Invalid MUS header");
        }

        int scoreLength = Short.toUnsignedInt(mus.getShort());
        int scoreStart = Short.toUnsignedInt(mus.getShort());
        mus.getShort(); // primary channels
        mus.getShort(); // secondary channels
        int instrumentCount = Short.toUnsignedInt(mus.getShort());
        mus.getShort(); // padding

        int instrumentsBytes = instrumentCount * 2;
        if (mus.remaining() < instrumentsBytes) {
            throw new IllegalArgumentException("Invalid MUS instrument table");
        }
        mus.position(mus.position() + instrumentsBytes);

        if (scoreStart < 0 || scoreStart > musBytes.length) {
            throw new IllegalArgumentException("Invalid MUS score start");
        }
        int scoreEnd = Math.min(musBytes.length, scoreStart + scoreLength);

        ByteArrayOutputStream out = new ByteArrayOutputStream(scoreLength * 2);
        writeBytes(out, MIDI_HEADER);
        int trackSize = 0;

        // tempo meta: 120bpm => 500000 us/qn, combined with 0x46 PPQ gives 1 tick = 1/140s
        trackSize += writeVarLen(out, 0);
        trackSize += writeBytes(out, new byte[] {(byte) 0xFF, 0x51, 0x03, 0x07, (byte) 0xA1, 0x20});

        int[] channelMap = new int[NUM_CHANNELS];
        Arrays.fill(channelMap, -1);
        int[] channelVelocities = new int[NUM_CHANNELS];
        Arrays.fill(channelVelocities, 127);
        int queuedTime = 0;

        int pos = scoreStart;
        boolean hitEnd = false;
        while (!hitEnd && pos < scoreEnd) {
            while (!hitEnd && pos < scoreEnd) {
                int eventDescriptor = musBytes[pos++] & 0xFF;
                int channel = getMidiChannel(eventDescriptor & 0x0F, channelMap, out, queuedTime);
                int event = eventDescriptor & 0x70;

                switch (event) {
                    case 0x00 -> { // release key
                        int key = musBytes[pos++] & 0xFF;
                        trackSize += writeReleaseKey(out, queuedTime, channel, key);
                        queuedTime = 0;
                    }
                    case 0x10 -> { // press key
                        int key = musBytes[pos++] & 0xFF;
                        if ((key & 0x80) != 0) {
                            channelVelocities[channel] = musBytes[pos++] & 0x7F;
                        }
                        trackSize += writePressKey(out, queuedTime, channel, key & 0x7F, channelVelocities[channel]);
                        queuedTime = 0;
                    }
                    case 0x20 -> { // pitch wheel
                        int key = musBytes[pos++] & 0xFF;
                        int wheel = (key & 0xFF) * 64;
                        trackSize += writePitchWheel(out, queuedTime, channel, wheel);
                        queuedTime = 0;
                    }
                    case 0x30 -> { // system event
                        int controllerNumber = musBytes[pos++] & 0xFF;
                        if (controllerNumber < 10 || controllerNumber > 14) {
                            throw new IllegalArgumentException("Invalid MUS system event controller: " + controllerNumber);
                        }
                        trackSize += writeController(out, queuedTime, channel, CONTROLLER_MAP[controllerNumber], 0);
                        queuedTime = 0;
                    }
                    case 0x40 -> { // change controller
                        int controllerNumber = musBytes[pos++] & 0xFF;
                        int controllerValue = musBytes[pos++] & 0xFF;
                        if (controllerNumber == 0) {
                            trackSize += writePatchChange(out, queuedTime, channel, controllerValue);
                            queuedTime = 0;
                        } else {
                            if (controllerNumber < 1 || controllerNumber > 9) {
                                throw new IllegalArgumentException("Invalid MUS controller: " + controllerNumber);
                            }
                            trackSize += writeController(out, queuedTime, channel, CONTROLLER_MAP[controllerNumber], controllerValue);
                            queuedTime = 0;
                        }
                    }
                    case 0x60 -> hitEnd = true;
                    default -> throw new IllegalArgumentException("Unknown MUS event: " + event);
                }

                if ((eventDescriptor & 0x80) != 0) {
                    break;
                }
            }

            if (!hitEnd) {
                int timeDelay = 0;
                for (;;) {
                    int b = musBytes[pos++] & 0xFF;
                    timeDelay = timeDelay * 128 + (b & 0x7F);
                    if ((b & 0x80) == 0) {
                        break;
                    }
                }
                queuedTime += timeDelay;
            }
        }

        trackSize += writeVarLen(out, queuedTime);
        trackSize += writeBytes(out, new byte[] {(byte) 0xFF, 0x2F, 0x00});

        byte[] midi = out.toByteArray();
        int trackLenOffset = 18;
        midi[trackLenOffset] = (byte) ((trackSize >> 24) & 0xFF);
        midi[trackLenOffset + 1] = (byte) ((trackSize >> 16) & 0xFF);
        midi[trackLenOffset + 2] = (byte) ((trackSize >> 8) & 0xFF);
        midi[trackLenOffset + 3] = (byte) (trackSize & 0xFF);
        return midi;
    }

    private static int getMidiChannel(int musChannel, int[] channelMap, ByteArrayOutputStream out, int queuedTime) {
        if (musChannel == MUS_PERCUSSION_CHAN) {
            return MIDI_PERCUSSION_CHAN;
        }
        if (channelMap[musChannel] == -1) {
            channelMap[musChannel] = allocateMidiChannel(channelMap);
            // all notes off on first use (D_DDTBLU fix)
            writeController(out, queuedTime, channelMap[musChannel], 0x7B, 0);
        }
        return channelMap[musChannel];
    }

    private static int allocateMidiChannel(int[] channelMap) {
        int max = -1;
        for (int c : channelMap) {
            max = Math.max(max, c);
        }
        int result = max + 1;
        if (result == MIDI_PERCUSSION_CHAN) {
            result++;
        }
        return result;
    }

    private static int writeReleaseKey(ByteArrayOutputStream out, int delta, int channel, int key) {
        int size = 0;
        size += writeVarLen(out, delta);
        out.write(0x80 | (channel & 0x0F));
        out.write(key & 0x7F);
        out.write(0);
        return size + 3;
    }

    private static int writePressKey(ByteArrayOutputStream out, int delta, int channel, int key, int velocity) {
        int size = 0;
        size += writeVarLen(out, delta);
        out.write(0x90 | (channel & 0x0F));
        out.write(key & 0x7F);
        out.write(velocity & 0x7F);
        return size + 3;
    }

    private static int writePitchWheel(ByteArrayOutputStream out, int delta, int channel, int wheel) {
        int size = 0;
        size += writeVarLen(out, delta);
        out.write(0xE0 | (channel & 0x0F));
        out.write(wheel & 0x7F);
        out.write((wheel >> 7) & 0x7F);
        return size + 3;
    }

    private static int writePatchChange(ByteArrayOutputStream out, int delta, int channel, int patch) {
        int size = 0;
        size += writeVarLen(out, delta);
        out.write(0xC0 | (channel & 0x0F));
        out.write(patch & 0x7F);
        return size + 2;
    }

    private static int writeController(ByteArrayOutputStream out, int delta, int channel, int control, int value) {
        int size = 0;
        size += writeVarLen(out, delta);
        out.write(0xB0 | (channel & 0x0F));
        out.write(control & 0x7F);
        int v = value;
        if ((v & 0x80) != 0) {
            v = 0x7F;
        }
        out.write(v);
        return size + 3;
    }

    private static int writeVarLen(ByteArrayOutputStream out, int time) {
        int buffer = time & 0x7F;
        int size = 0;
        while ((time >>= 7) != 0) {
            buffer <<= 8;
            buffer |= ((time & 0x7F) | 0x80);
        }
        for (;;) {
            int b = buffer & 0xFF;
            out.write(b);
            size++;
            if ((buffer & 0x80) != 0) {
                buffer >>= 8;
            } else {
                break;
            }
        }
        return size;
    }

    private static int writeBytes(ByteArrayOutputStream out, byte[] bytes) {
        out.writeBytes(bytes);
        return bytes.length;
    }
}

