package dev.randomecho.trapguard.client;

import dev.randomecho.trapguard.client.compat.MinecraftBlockAdapter;
import dev.randomecho.trapguard.core.model.IntPos;
import dev.randomecho.trapguard.core.model.Region;
import dev.randomecho.trapguard.core.model.SnapshotEntry;
import net.minecraft.client.Minecraft;
import dev.randomecho.trapguard.core.monitor.BlockReader;
import dev.randomecho.trapguard.core.monitor.ObserverClockDetector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SnapshotBuilder {
    /** Hard guard against accidentally selecting an enormous volume and freezing the client. */
    public static final int MAX_WATCHED_BLOCKS = 2_000_000;

    private SnapshotBuilder() {}

    public static Result build(Minecraft client, List<Region> regions, Set<IntPos> extraBlocks) {
        if (client.level == null) return Result.error("No world is loaded.");
        if (regions.isEmpty() && extraBlocks.isEmpty()) return Result.error("Selection is empty.");

        long upperBound = extraBlocks.size();
        try {
            for (Region region : regions) upperBound = Math.addExact(upperBound, region.blockCount());
        } catch (ArithmeticException tooLarge) {
            return Result.error("Selection is too large.");
        }
        if (upperBound > MAX_WATCHED_BLOCKS) {
            return Result.error("Selection can contain up to " + MAX_WATCHED_BLOCKS + " watched blocks; this selection can contain " + upperBound + ".");
        }

        LinkedHashSet<IntPos> positions = new LinkedHashSet<>((int) Math.min(Integer.MAX_VALUE - 8L, Math.max(16L, upperBound * 4L / 3L)));
        for (Region region : regions) region.forEach(positions::add);
        positions.addAll(extraBlocks);

        if (positions.size() > MAX_WATCHED_BLOCKS) {
            return Result.error("Selection contains " + positions.size() + " watched blocks, above the limit of " + MAX_WATCHED_BLOCKS + ".");
        }

        BlockReader reader = MinecraftBlockAdapter.reader(client);
        List<SnapshotEntry> snapshot = new ArrayList<>(positions.size());
        int unloaded = 0;
        IntPos firstUnloaded = null;
        for (IntPos pos : positions) {
            BlockReader.ReadResult result = reader.read(pos);
            if (!result.loaded()) {
                unloaded++;
                if (firstUnloaded == null) firstUnloaded = pos;
                continue;
            }
            snapshot.add(new SnapshotEntry(pos, result.block()));
        }
        if (unloaded > 0) {
            return Result.error("Cannot snapshot: " + unloaded + " watched positions are in unloaded chunks. First unloaded position: " + firstUnloaded.compact());
        }
        int observerClockPairs = ObserverClockDetector.countFaceToFacePairs(snapshot, reader);
        return Result.success(snapshot, observerClockPairs);
    }

    public record Result(List<SnapshotEntry> snapshot, String error, int observerClockPairs) {
        public static Result success(List<SnapshotEntry> snapshot, int observerClockPairs) {
            return new Result(List.copyOf(snapshot), null, observerClockPairs);
        }

        public static Result error(String error) {
            return new Result(List.of(), error, 0);
        }

        public boolean ok() {
            return error == null;
        }
    }
}
