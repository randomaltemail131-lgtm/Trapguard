package dev.randomecho.trapguard.core.model;

import java.util.Objects;

public record SnapshotEntry(IntPos pos, BlockDescriptor expected) {
    public SnapshotEntry {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(expected, "expected");
    }
}
