package dev.randomecho.trapguard.core.monitor;

import dev.randomecho.trapguard.core.model.BlockDescriptor;
import dev.randomecho.trapguard.core.model.IntPos;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public record Difference(IntPos pos, BlockDescriptor expected, BlockDescriptor actual, Set<String> changedProperties) {
    public Difference {
        changedProperties = Collections.unmodifiableSet(new TreeSet<>(changedProperties));
    }

    public boolean blockChanged() {
        return !expected.blockId().equals(actual.blockId());
    }
}
