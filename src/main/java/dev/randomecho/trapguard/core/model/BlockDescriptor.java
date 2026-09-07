package dev.randomecho.trapguard.core.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Portable representation of a Minecraft block state.
 * No Minecraft classes are stored in snapshots, which makes saved traps portable across mod ports.
 */
public record BlockDescriptor(String blockId, Map<String, String> properties) {
    public BlockDescriptor {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(properties, "properties");
        properties = Collections.unmodifiableMap(new TreeMap<>(properties));
    }
}
