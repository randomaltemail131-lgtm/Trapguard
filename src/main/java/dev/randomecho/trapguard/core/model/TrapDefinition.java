package dev.randomecho.trapguard.core.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Gson-friendly persisted trap definition. */
public final class TrapDefinition {
    public static final String UNGROUPED = "Ungrouped";

    public String name;
    public String dimension;
    /** Server/world scope. Legacy v1 traps use "*" until resnapshotted/applied on a server. */
    public String serverScope = "*";
    /** User-facing folder/group name. */
    public String group = UNGROUPED;
    public List<Region> regions = new ArrayList<>();
    public Set<IntPos> extraBlocks = new LinkedHashSet<>();
    public List<SnapshotEntry> snapshot = new ArrayList<>();
    public ComparisonMode mode = ComparisonMode.STRUCTURAL;
    public boolean enabled = true;
    public boolean overlay = true;

    public TrapDefinition() {
        // Gson
    }

    public TrapDefinition(String name, String dimension, String serverScope) {
        this.name = Objects.requireNonNull(name, "name");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.serverScope = Objects.requireNonNull(serverScope, "serverScope");
    }

    public void normalizeAfterLoad() {
        if (regions == null) regions = new ArrayList<>();
        if (extraBlocks == null) extraBlocks = new LinkedHashSet<>();
        if (snapshot == null) snapshot = new ArrayList<>();
        if (mode == null) mode = ComparisonMode.STRUCTURAL;
        if (serverScope == null || serverScope.isBlank()) serverScope = "*";
        if (group == null || group.isBlank()) group = UNGROUPED;
    }

    public long watchedPositionCount() {
        return snapshot == null ? 0 : snapshot.size();
    }
}
