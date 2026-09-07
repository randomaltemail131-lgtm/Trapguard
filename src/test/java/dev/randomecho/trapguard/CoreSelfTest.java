package dev.randomecho.trapguard;

import dev.randomecho.trapguard.core.export.SpongeSchemV2Writer;
import dev.randomecho.trapguard.core.model.BlockDescriptor;
import dev.randomecho.trapguard.core.model.ComparisonMode;
import dev.randomecho.trapguard.core.model.IntPos;
import dev.randomecho.trapguard.core.model.SnapshotEntry;
import dev.randomecho.trapguard.core.model.TrapDefinition;
import dev.randomecho.trapguard.core.model.TrapDatabase;
import dev.randomecho.trapguard.core.monitor.BlockReader;
import dev.randomecho.trapguard.core.monitor.ComparisonPolicy;
import dev.randomecho.trapguard.core.monitor.MonitorDelta;
import dev.randomecho.trapguard.core.monitor.MonitorEngine;
import dev.randomecho.trapguard.core.monitor.ObserverClockDetector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.zip.GZIPInputStream;
import java.util.List;
import java.util.Map;

/**
 * Dependency-free behavioral smoke tests that run as part of Gradle check/build.
 * Throwing AssertionError fails CI.
 */
public final class CoreSelfTest {
    private CoreSelfTest() {}

    public static void main(String[] args) {
        IntPos pos = new IntPos(1, 64, 2);
        BlockDescriptor repeaterOff = block("minecraft:repeater",
                "powered", "false", "delay", "1", "facing", "north", "locked", "false");
        BlockDescriptor repeaterOn = block("minecraft:repeater",
                "powered", "true", "delay", "1", "facing", "north", "locked", "true");
        BlockDescriptor repeaterDelay = block("minecraft:repeater",
                "powered", "false", "delay", "4", "facing", "north", "locked", "false");

        require(ComparisonPolicy.compare(pos, repeaterOff, repeaterOn, ComparisonMode.STRUCTURAL).isEmpty(),
                "Structural mode must ignore powered/locked changes");
        require(ComparisonPolicy.compare(pos, repeaterOff, repeaterOn, ComparisonMode.EXACT).isPresent(),
                "Exact mode must detect powered/locked changes");
        require(ComparisonPolicy.compare(pos, repeaterOff, repeaterDelay, ComparisonMode.STRUCTURAL).isPresent(),
                "Structural mode must detect repeater delay changes");
        require(ComparisonPolicy.compare(pos, repeaterOff, block("minecraft:air"), ComparisonMode.STRUCTURAL).isPresent(),
                "Block replacement must always be detected");

        TrapDefinition trap = new TrapDefinition("Test", "minecraft:overworld", "server:example.org");
        trap.snapshot = List.of(new SnapshotEntry(pos, repeaterOff));
        MonitorEngine engine = new MonitorEngine();

        MonitorDelta unloaded = engine.scan(trap, ignored -> BlockReader.ReadResult.unloaded(), 10);
        require(unloaded.skippedUnloaded() == 1 && unloaded.checked() == 0 && unloaded.active().isEmpty(),
                "Unloaded chunks must be skipped rather than treated as air");

        require(!engine.hasCompletedCycle(trap.name, trap.snapshot.size()),
                "An unloaded position must not count toward completed scan coverage");
        engine.invalidateCycle(trap.name);
        require(!engine.hasCompletedCycle(trap.name, trap.snapshot.size()),
                "Invalidating scan coverage must force the trap back into scanning state");
        engine.scan(trap, ignored -> BlockReader.ReadResult.loaded(repeaterOff), 10);
        require(engine.hasCompletedCycle(trap.name, trap.snapshot.size()),
                "A fresh complete scan must restore completed-cycle state");

        TrapDefinition coverageTrap = new TrapDefinition("Coverage", "minecraft:overworld", "server:example.org");
        IntPos coverageA = new IntPos(2, 64, 2);
        IntPos coverageB = new IntPos(3, 64, 2);
        coverageTrap.snapshot = List.of(
                new SnapshotEntry(coverageA, repeaterOff),
                new SnapshotEntry(coverageB, repeaterOff)
        );
        MonitorEngine coverageEngine = new MonitorEngine();
        java.util.Set<IntPos> loadedPositions = java.util.Set.of(coverageA);
        coverageEngine.scan(coverageTrap, pos2 -> loadedPositions.contains(pos2)
                ? BlockReader.ReadResult.loaded(repeaterOff)
                : BlockReader.ReadResult.unloaded(), 1);
        coverageEngine.scan(coverageTrap, pos2 -> loadedPositions.contains(pos2)
                ? BlockReader.ReadResult.loaded(repeaterOff)
                : BlockReader.ReadResult.unloaded(), 1);
        require(!coverageEngine.hasCompletedCycle(coverageTrap.name, coverageTrap.snapshot.size()),
                "Repeatedly checking one loaded position must not complete coverage for an unloaded position");

        MonitorDelta grief = engine.scan(trap, ignored -> BlockReader.ReadResult.loaded(block("minecraft:air")), 10);
        require(grief.newlyBroken().size() == 1 && grief.active().size() == 1,
                "A loaded changed block must become an active discrepancy");

        MonitorDelta unloadWhileBroken = engine.scan(trap, ignored -> BlockReader.ReadResult.unloaded(), 10);
        require(unloadWhileBroken.newlyBroken().isEmpty() && unloadWhileBroken.resolved().isEmpty()
                        && unloadWhileBroken.active().size() == 1,
                "Unloading a broken position must neither re-alert nor resolve it");

        MonitorDelta duplicate = engine.scan(trap, ignored -> BlockReader.ReadResult.loaded(block("minecraft:air")), 10);
        require(duplicate.newlyBroken().isEmpty() && duplicate.active().size() == 1,
                "An unchanged discrepancy must not duplicate-alert");

        MonitorDelta restored = engine.scan(trap, ignored -> BlockReader.ReadResult.loaded(repeaterOff), 10);
        require(restored.resolved().equals(List.of(pos)) && restored.active().isEmpty(),
                "Restoring the saved state must resolve the discrepancy");

        IntPos observerA = new IntPos(10, 64, 10);
        IntPos observerB = new IntPos(11, 64, 10);
        BlockDescriptor observerEast = block("minecraft:observer", "facing", "east", "powered", "false");
        BlockDescriptor observerWest = block("minecraft:observer", "facing", "west", "powered", "true");
        Map<IntPos, BlockDescriptor> observerWorld = Map.of(observerA, observerEast, observerB, observerWest);
        BlockReader observerReader = readPos -> {
            BlockDescriptor found = observerWorld.get(readPos);
            return found == null ? BlockReader.ReadResult.loaded(block("minecraft:air")) : BlockReader.ReadResult.loaded(found);
        };
        require(ObserverClockDetector.countFaceToFacePairs(
                        List.of(new SnapshotEntry(observerA, observerEast), new SnapshotEntry(observerB, observerWest)), observerReader) == 1,
                "Face-to-face observers must be recognized as one observer-clock pair");
        require(ObserverClockDetector.countFaceToFacePairs(
                        List.of(new SnapshotEntry(observerA, observerEast)), observerReader) == 1,
                "A watched observer must detect a loaded face-to-face neighbor even when the neighbor is outside the snapshot");
        require(ObserverClockDetector.countFaceToFacePairs(
                        List.of(new SnapshotEntry(observerA, block("minecraft:observer", "facing", "west", "powered", "false"))), observerReader) == 0,
                "Observers facing away from each other must not be reported as a clock pair");

        testSchemExport();
        testSchemVarIntAndAirGap();

        TrapDefinition legacy = new TrapDefinition();
        legacy.name = "Legacy";
        legacy.dimension = "minecraft:overworld";
        legacy.serverScope = null;
        legacy.group = null;
        legacy.normalizeAfterLoad();
        require("*".equals(legacy.serverScope), "Legacy trap files must normalize to wildcard scope");
        require(TrapDefinition.UNGROUPED.equals(legacy.group), "Legacy trap files must normalize to Ungrouped");

        TrapDatabaseTest();

        System.out.println("TrapGuard core self-test passed.");
    }


    private static void TrapDatabaseTest() {
        TrapDatabase database = new TrapDatabase();
        database.groups = new java.util.ArrayList<>(List.of("Ungrouped", "Base", "base", ""));
        TrapDefinition trap = new TrapDefinition("Grouped", "minecraft:overworld", "*");
        trap.group = "base";
        database.traps.add(trap);
        database.normalizeAfterLoad();
        require(database.groups.size() == 2, "Group normalization must collapse case-insensitive duplicates and blanks");
        require("Base".equals(trap.group), "Trap group must use the canonical persisted folder name");
    }

    private static void testSchemExport() {
        TrapDefinition exportTrap = new TrapDefinition("ExportTest", "minecraft:overworld", "*");
        exportTrap.snapshot = List.of(
                new SnapshotEntry(new IntPos(10, 64, 20), block("minecraft:stone")),
                new SnapshotEntry(new IntPos(11, 64, 20), block("minecraft:repeater",
                        "powered", "false", "delay", "2", "facing", "north", "locked", "false"))
        );
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            SpongeSchemV2Writer.Summary summary = SpongeSchemV2Writer.write(exportTrap, 9999, raw);
            require(summary.width() == 2 && summary.height() == 1 && summary.length() == 1 && summary.volume() == 2,
                    "Sponge schematic export bounds must match the saved snapshot");

            try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(raw.toByteArray())))) {
                require(in.readUnsignedByte() == 10, "Sponge schematic root must be an NBT compound");
                require("Schematic".equals(in.readUTF()), "Sponge schematic root compound must be named Schematic");
                Map<String, Object> root = readCompound(in);
                require(Integer.valueOf(2).equals(root.get("Version")), "Sponge schematic Version must be 2");
                require(Integer.valueOf(9999).equals(root.get("DataVersion")), "Sponge schematic DataVersion must be preserved");
                require(((Short) root.get("Width")) == 2 && ((Short) root.get("Height")) == 1 && ((Short) root.get("Length")) == 1,
                        "Sponge schematic dimensions must be encoded as shorts");
                require(Arrays.equals((int[]) root.get("Offset"), new int[] {10, 64, 20}),
                        "Sponge schematic Offset must preserve the saved area's minimum world position");
                @SuppressWarnings("unchecked")
                Map<String, Object> palette = (Map<String, Object>) root.get("Palette");
                require(Integer.valueOf(0).equals(palette.get("minecraft:air")), "Sponge palette must reserve air");
                require(Integer.valueOf(1).equals(palette.get("minecraft:stone")), "Sponge palette must include block ids");
                require(Integer.valueOf(2).equals(palette.get("minecraft:repeater[delay=2,facing=north,locked=false,powered=false]")),
                        "Sponge palette must encode sorted block-state properties");
                require(Arrays.equals((byte[]) root.get("BlockData"), new byte[] {1, 2}),
                        "Sponge BlockData must follow x + z*Width + y*Width*Length order");
            }
        } catch (IOException exception) {
            throw new AssertionError("Sponge schematic export test failed", exception);
        }
    }


    private static void testSchemVarIntAndAirGap() {
        TrapDefinition gapTrap = new TrapDefinition("GapTest", "minecraft:overworld", "*");
        gapTrap.snapshot = List.of(
                new SnapshotEntry(new IntPos(0, 64, 0), block("minecraft:stone")),
                new SnapshotEntry(new IntPos(2, 64, 0), block("minecraft:diamond_block"))
        );
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            SpongeSchemV2Writer.Summary summary = SpongeSchemV2Writer.write(gapTrap, 9999, raw);
            require(summary.width() == 3 && summary.volume() == 3,
                    "Sponge export must include air gaps inside the saved bounding box");
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(raw.toByteArray())))) {
                require(in.readUnsignedByte() == 10 && "Schematic".equals(in.readUTF()),
                        "Air-gap schematic must have the standard Sponge root");
                Map<String, Object> root = readCompound(in);
                require(Arrays.equals((byte[]) root.get("BlockData"), new byte[] {1, 0, 2}),
                        "Air gaps must be encoded as palette air entries");
            }

            TrapDefinition varIntTrap = new TrapDefinition("VarIntTest", "minecraft:overworld", "*");
            List<SnapshotEntry> entries = new java.util.ArrayList<>();
            for (int x = 0; x < 130; x++) {
                entries.add(new SnapshotEntry(new IntPos(x, 64, 0), block("minecraft:test_block_" + x)));
            }
            varIntTrap.snapshot = entries;
            raw.reset();
            SpongeSchemV2Writer.Summary varIntSummary = SpongeSchemV2Writer.write(varIntTrap, 9999, raw);
            require(varIntSummary.paletteSize() == 131,
                    "Sponge palette should contain air plus all 130 unique block states");
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(raw.toByteArray())))) {
                in.readUnsignedByte();
                in.readUTF();
                Map<String, Object> root = readCompound(in);
                byte[] blockData = (byte[]) root.get("BlockData");
                require(blockData.length == 133,
                        "Palette indices above 127 must use multi-byte VarInt encoding");
                require((blockData[127] & 0xFF) == 0x80 && (blockData[128] & 0xFF) == 0x01,
                        "Palette index 128 must encode as VarInt 0x80,0x01");
            }
        } catch (IOException exception) {
            throw new AssertionError("Sponge VarInt/air-gap export test failed", exception);
        }
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) return result;
            String name = in.readUTF();
            result.put(name, readPayload(in, type));
        }
    }

    private static Object readPayload(DataInputStream in, int type) throws IOException {
        return switch (type) {
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 7 -> {
                int length = in.readInt();
                byte[] value = new byte[length];
                in.readFully(value);
                yield value;
            }
            case 8 -> in.readUTF();
            case 9 -> {
                int elementType = in.readUnsignedByte();
                int length = in.readInt();
                require(length == 0, "CoreSelfTest only expects empty NBT lists here");
                yield elementType;
            }
            case 10 -> readCompound(in);
            case 11 -> {
                int length = in.readInt();
                int[] value = new int[length];
                for (int i = 0; i < length; i++) value[i] = in.readInt();
                yield value;
            }
            default -> throw new IOException("Unsupported test NBT tag type " + type);
        };
    }
    private static BlockDescriptor block(String id, String... keyValues) {
        Map<String, String> properties = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.put(keyValues[i], keyValues[i + 1]);
        }
        return new BlockDescriptor(id, properties);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
