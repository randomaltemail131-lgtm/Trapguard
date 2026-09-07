package dev.randomecho.trapguard.core.monitor;

import dev.randomecho.trapguard.core.model.IntPos;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record MonitorDelta(
        List<Difference> newlyBroken,
        List<IntPos> resolved,
        Map<IntPos, Difference> active,
        int checked,
        int skippedUnloaded
) {
    public MonitorDelta {
        newlyBroken = List.copyOf(newlyBroken);
        resolved = List.copyOf(resolved);
        active = Collections.unmodifiableMap(active);
    }
}
