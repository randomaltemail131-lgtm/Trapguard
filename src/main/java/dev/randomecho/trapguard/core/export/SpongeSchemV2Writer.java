package dev.randomecho.trapguard.core.export;

import dev.randomecho.trapguard.core.model.BlockDescriptor;
import dev.randomecho.trapguard.core.model.IntPos;
import dev.randomecho.trapguard.core.model.SnapshotEntry;
import dev.randomecho.trapguard.core.model.TrapDefinition;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.zip.GZIPOutputStream;

/** Portable Sponge Schematic v2 writer for TrapGuard snapshots. */
public final class SpongeSchemV2Writer {
    private static final BlockDescriptor AIR = new BlockDescriptor("minecraft:air", Map.of());
    /** Prevent a far-away extra watched block from creating an enormous air-filled export cuboid. */
    public static final long MAX_EXPORT_VOLUME = 8_000_000L;

    private SpongeSchemV2Writer() {}

    public static Summary write(TrapDefinition trap, int dataVersion, OutputStream output) throws IOException {
        if (trap == null || trap.snapshot == null || trap.snapshot.isEmpty()) {
            throw new IllegalArgumentException("This trap has no saved snapshot to export.");
        }
        if (output == null) throw new IllegalArgumentException("Output stream is required.");

        Bounds bounds = bounds(trap);
        long volume;
        try {
            volume = Math.multiplyExact(Math.multiplyExact((long) bounds.width, bounds.height), bounds.length);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("The saved trap is too large to export as a .schem file.", exception);
        }
        if (bounds.width > 65_535 || bounds.height > 65_535 || bounds.length > 65_535) {
            throw new IllegalArgumentException("Sponge .schem dimensions cannot exceed 65535 blocks on an axis.");
        }
        if (volume > MAX_EXPORT_VOLUME) {
            throw new IllegalArgumentException("The trap's export bounding box is " + volume
                    + " blocks, above TrapGuard's safe .schem export limit of " + MAX_EXPORT_VOLUME + ".");
        }

        Map<IntPos, BlockDescriptor> states = new HashMap<>(Math.max(16, trap.snapshot.size() * 4 / 3));
        for (SnapshotEntry entry : trap.snapshot) states.put(entry.pos(), entry.expected());

        LinkedHashMap<String, Integer> palette = new LinkedHashMap<>();
        palette.put(stateString(AIR), 0);
        for (SnapshotEntry entry : trap.snapshot) {
            palette.computeIfAbsent(stateString(entry.expected()), ignored -> palette.size());
        }

        byte[] blockData = encodeBlockData(states, palette, bounds, Math.toIntExact(volume));
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(output)))) {
            writeSchematic(out, trap, dataVersion, bounds, palette, blockData);
        }
        return new Summary(bounds.width, bounds.height, bounds.length, volume, palette.size());
    }

    private static byte[] encodeBlockData(Map<IntPos, BlockDescriptor> states, Map<String, Integer> palette,
                                          Bounds bounds, int volume) {
        // Pre-resolve only the watched positions. This avoids allocating a new IntPos for every
        // air block in large sparse bounding boxes during the full-volume export pass.
        Map<Integer, Integer> sparsePalette = new HashMap<>(Math.max(16, states.size() * 4 / 3));
        for (Map.Entry<IntPos, BlockDescriptor> entry : states.entrySet()) {
            Integer paletteId = palette.get(stateString(entry.getValue()));
            if (paletteId == null) {
                throw new IllegalStateException("Missing palette entry for " + entry.getValue().blockId());
            }
            IntPos pos = entry.getKey();
            sparsePalette.put(localIndex(pos.x() - bounds.minX, pos.y() - bounds.minY, pos.z() - bounds.minZ, bounds.width, bounds.length), paletteId);
        }

        byte[] buffer = new byte[Math.max(32, volume * 2)];
        int cursor = 0;
        for (int y = 0; y < bounds.height; y++) {
            for (int z = 0; z < bounds.length; z++) {
                for (int x = 0; x < bounds.width; x++) {
                    Integer paletteId = sparsePalette.get(localIndex(x, y, z, bounds.width, bounds.length));
                    int value = paletteId == null ? 0 : paletteId;
                    while ((value & ~0x7F) != 0) {
                        if (cursor >= buffer.length) buffer = grow(buffer);
                        buffer[cursor++] = (byte) ((value & 0x7F) | 0x80);
                        value >>>= 7;
                    }
                    if (cursor >= buffer.length) buffer = grow(buffer);
                    buffer[cursor++] = (byte) value;
                }
            }
        }
        byte[] exact = new byte[cursor];
        System.arraycopy(buffer, 0, exact, 0, cursor);
        return exact;
    }

    private static int localIndex(int x, int y, int z, int width, int length) {
        return Math.multiplyExact(Math.addExact(Math.multiplyExact(y, length), z), width) + x;
    }

    private static byte[] grow(byte[] source) {
        byte[] grown = new byte[Math.max(source.length + 32, source.length * 2)];
        System.arraycopy(source, 0, grown, 0, source.length);
        return grown;
    }

    private static void writeSchematic(DataOutputStream out, TrapDefinition trap, int dataVersion, Bounds b,
                                       LinkedHashMap<String, Integer> palette, byte[] blockData) throws IOException {
        // WorldEdit and Sponge v2 writers use a named root compound called "Schematic".
        out.writeByte(10);
        out.writeUTF("Schematic");

        writeInt(out, "Version", 2);
        writeInt(out, "DataVersion", dataVersion);
        writeCompoundStart(out, "Metadata");
        writeString(out, "Name", trap.name);
        writeString(out, "Author", "TrapGuard");
        writeLong(out, "Date", System.currentTimeMillis());
        out.writeByte(0);

        writeShort(out, "Width", b.width);
        writeShort(out, "Height", b.height);
        writeShort(out, "Length", b.length);
        // Preserve the saved area's world-space minimum as the schematic offset.
        writeIntArray(out, "Offset", new int[] {b.minX, b.minY, b.minZ});
        writeInt(out, "PaletteMax", palette.size());

        writeCompoundStart(out, "Palette");
        for (Map.Entry<String, Integer> entry : palette.entrySet()) writeInt(out, entry.getKey(), entry.getValue());
        out.writeByte(0);

        writeByteArray(out, "BlockData", blockData);
        writeEmptyCompoundList(out, "BlockEntities");
        out.writeByte(0);
    }

    private static String stateString(BlockDescriptor descriptor) {
        if (descriptor.properties().isEmpty()) return descriptor.blockId();
        StringJoiner joiner = new StringJoiner(",", descriptor.blockId() + "[", "]");
        descriptor.properties().forEach((key, value) -> joiner.add(key + "=" + value));
        return joiner.toString();
    }

    private static Bounds bounds(TrapDefinition trap) {
        SnapshotEntry first = trap.snapshot.getFirst();
        int minX = first.pos().x(), minY = first.pos().y(), minZ = first.pos().z();
        int maxX = minX, maxY = minY, maxZ = minZ;
        for (SnapshotEntry entry : trap.snapshot) {
            IntPos pos = entry.pos();
            minX = Math.min(minX, pos.x());
            minY = Math.min(minY, pos.y());
            minZ = Math.min(minZ, pos.z());
            maxX = Math.max(maxX, pos.x());
            maxY = Math.max(maxY, pos.y());
            maxZ = Math.max(maxZ, pos.z());
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ,
                maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    private static void writeCompoundStart(DataOutputStream out, String name) throws IOException {
        out.writeByte(10); out.writeUTF(name);
    }
    private static void writeInt(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); out.writeUTF(name); out.writeInt(value);
    }
    private static void writeLong(DataOutputStream out, String name, long value) throws IOException {
        out.writeByte(4); out.writeUTF(name); out.writeLong(value);
    }
    private static void writeShort(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(2); out.writeUTF(name); out.writeShort(value & 0xFFFF);
    }
    private static void writeString(DataOutputStream out, String name, String value) throws IOException {
        out.writeByte(8); out.writeUTF(name); out.writeUTF(value);
    }
    private static void writeByteArray(DataOutputStream out, String name, byte[] value) throws IOException {
        out.writeByte(7); out.writeUTF(name); out.writeInt(value.length); out.write(value);
    }
    private static void writeIntArray(DataOutputStream out, String name, int[] value) throws IOException {
        out.writeByte(11); out.writeUTF(name); out.writeInt(value.length); for (int item : value) out.writeInt(item);
    }
    private static void writeEmptyCompoundList(DataOutputStream out, String name) throws IOException {
        out.writeByte(9); out.writeUTF(name); out.writeByte(10); out.writeInt(0);
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                          int width, int height, int length) {}

    public record Summary(int width, int height, int length, long volume, int paletteSize) {}
}
