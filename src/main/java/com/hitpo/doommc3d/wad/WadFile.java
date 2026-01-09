package com.hitpo.doommc3d.wad;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class WadFile {
    private static final String[] VALID_HEADERS = {"IWAD", "PWAD"};

    private final ByteBuffer data;
    private final List<WadDirectoryEntry> directory = new ArrayList<>();
    private final String identification;
    private final Path source;

    public WadFile(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        identification = readIdentification();
        source = path;
        parseDirectory();
    }

    private String readIdentification() {
        byte[] magic = new byte[4];
        data.position(0);
        data.get(magic);
        return new String(magic, StandardCharsets.US_ASCII);
    }

    private void parseDirectory() {
        data.position(4);
        int numLumps = data.getInt();
        int dirOffset = data.getInt();
        data.position(dirOffset);
        for (int i = 0; i < numLumps; i++) {
            int pos = data.getInt();
            int size = data.getInt();
            byte[] nameBytes = new byte[8];
            data.get(nameBytes);
            String name = new String(nameBytes, StandardCharsets.US_ASCII).trim().toUpperCase(Locale.ROOT);
            directory.add(new WadDirectoryEntry(pos, size, name));
        }
    }

    public ByteBuffer readLump(WadDirectoryEntry entry) {
        ByteBuffer slice = data.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        slice.position(entry.getOffset());
        slice.limit(entry.getOffset() + entry.getSize());
        return slice.slice().order(ByteOrder.LITTLE_ENDIAN);
    }

    public List<WadDirectoryEntry> getDirectory() {
        return Collections.unmodifiableList(directory);
    }

    public String getIdentification() {
        return identification;
    }

    public Path getSource() {
        return source;
    }

    public boolean isValidHeader() {
        for (String header : VALID_HEADERS) {
            if (header.equals(identification)) {
                return true;
            }
        }
        return false;
    }

    public boolean isIwad() {
        return "IWAD".equals(identification);
    }
}
