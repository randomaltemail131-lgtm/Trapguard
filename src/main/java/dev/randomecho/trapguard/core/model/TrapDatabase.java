package dev.randomecho.trapguard.core.model;

import java.util.ArrayList;
import java.util.List;

/** Root JSON document. schemaVersion allows future migrations without breaking old snapshots. */
public final class TrapDatabase {
    public int schemaVersion = 3;
    public List<TrapDefinition> traps = new ArrayList<>();
    public List<String> groups = new ArrayList<>(List.of(TrapDefinition.UNGROUPED));

    public void normalizeAfterLoad() {
        if (traps == null) traps = new ArrayList<>();
        traps.removeIf(trap -> trap == null || trap.name == null || trap.dimension == null);
        traps.forEach(TrapDefinition::normalizeAfterLoad);

        List<String> normalized = new ArrayList<>();
        normalized.add(TrapDefinition.UNGROUPED);
        if (groups != null) {
            for (String group : groups) addGroupIfMissing(normalized, group);
        }
        for (TrapDefinition trap : traps) {
            String canonical = findIgnoreCase(normalized, trap.group);
            if (canonical == null) {
                addGroupIfMissing(normalized, trap.group);
                canonical = findIgnoreCase(normalized, trap.group);
            }
            trap.group = canonical == null ? TrapDefinition.UNGROUPED : canonical;
        }
        groups = normalized;
        if (schemaVersion < 3) schemaVersion = 3;
    }

    private static void addGroupIfMissing(List<String> groups, String raw) {
        if (raw == null || raw.isBlank()) return;
        String value = raw.trim();
        if (findIgnoreCase(groups, value) == null) groups.add(value);
    }

    private static String findIgnoreCase(List<String> groups, String value) {
        if (value == null) return null;
        for (String group : groups) if (group.equalsIgnoreCase(value)) return group;
        return null;
    }
}
