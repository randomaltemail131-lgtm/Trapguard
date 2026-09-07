package dev.randomecho.trapguard.core.monitor;

import dev.randomecho.trapguard.core.model.IntPos;
import dev.randomecho.trapguard.core.model.SnapshotEntry;
import dev.randomecho.trapguard.core.model.TrapDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Incremental, version-independent scanner. It deliberately advances over unloaded positions
 * without declaring them healthy or broken. They are checked again on a later scan cycle.
 */
public final class MonitorEngine {
    private final Map<String, Integer> cursors = new HashMap<>();
    private final Map<String, LinkedHashMap<IntPos, Difference>> activeByTrap = new HashMap<>();
    private final Map<String, java.util.Set<Integer>> scannedIndices = new HashMap<>();

    public MonitorDelta scan(TrapDefinition trap, BlockReader reader, int budget) {
        if (trap.snapshot == null || trap.snapshot.isEmpty() || budget <= 0) {
            return new MonitorDelta(List.of(), List.of(), active(trap.name), 0, 0);
        }

        int size = trap.snapshot.size();
        int cursor = Math.floorMod(cursors.getOrDefault(trap.name, 0), size);
        int iterations = Math.min(budget, size);
        int checked = 0;
        int skipped = 0;
        List<Difference> newlyBroken = new ArrayList<>();
        List<IntPos> resolved = new ArrayList<>();
        LinkedHashMap<IntPos, Difference> active = activeByTrap.computeIfAbsent(trap.name, ignored -> new LinkedHashMap<>());

        java.util.Set<Integer> scanned = scannedIndices.computeIfAbsent(trap.name, ignored -> new java.util.HashSet<>());

        for (int i = 0; i < iterations; i++) {
            int index = cursor;
            SnapshotEntry entry = trap.snapshot.get(index);
            cursor = (cursor + 1) % size;

            BlockReader.ReadResult read = reader.read(entry.pos());
            if (!read.loaded()) {
                skipped++;
                continue;
            }
            checked++;
            scanned.add(index);

            Optional<Difference> difference = ComparisonPolicy.compare(
                    entry.pos(), entry.expected(), read.block(), trap.mode
            );

            if (difference.isPresent()) {
                Difference next = difference.get();
                Difference previous = active.put(entry.pos(), next);
                if (previous == null || !sameDifference(previous, next)) {
                    newlyBroken.add(next);
                }
            } else if (active.remove(entry.pos()) != null) {
                resolved.add(entry.pos());
            }
        }

        cursors.put(trap.name, cursor);

        return new MonitorDelta(newlyBroken, resolved, new LinkedHashMap<>(active), checked, skipped);
    }

    public Map<IntPos, Difference> active(String trapName) {
        return Map.copyOf(activeByTrap.getOrDefault(trapName, new LinkedHashMap<>()));
    }

    public int activeCount(String trapName) {
        return activeByTrap.getOrDefault(trapName, new LinkedHashMap<>()).size();
    }

    public boolean hasCompletedCycle(String trapName, int snapshotSize) {
        return snapshotSize <= 0 || scannedIndices.getOrDefault(trapName, java.util.Set.of()).size() >= snapshotSize;
    }

    /** Marks scan coverage stale without discarding known active differences. */
    public void invalidateCycle(String trapName) {
        scannedIndices.remove(trapName);
    }

    /** Restarts scan coverage/cursors for all traps while preserving known differences. */
    public void resetScanProgress() {
        cursors.clear();
        scannedIndices.clear();
    }

    public void clearTrap(String trapName) {
        cursors.remove(trapName);
        activeByTrap.remove(trapName);
        scannedIndices.remove(trapName);
    }

    public void clearAll() {
        cursors.clear();
        activeByTrap.clear();
        scannedIndices.clear();
    }

    private static boolean sameDifference(Difference a, Difference b) {
        return a.actual().equals(b.actual()) && a.changedProperties().equals(b.changedProperties());
    }
}
