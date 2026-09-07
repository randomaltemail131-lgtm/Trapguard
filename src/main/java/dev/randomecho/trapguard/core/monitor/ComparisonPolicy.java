package dev.randomecho.trapguard.core.monitor;

import dev.randomecho.trapguard.core.model.BlockDescriptor;
import dev.randomecho.trapguard.core.model.ComparisonMode;
import dev.randomecho.trapguard.core.model.IntPos;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ComparisonPolicy {
    private ComparisonPolicy() {}

    /**
     * Properties that represent transient redstone/game state rather than construction/configuration.
     * Structural mode ignores these while still checking facing, repeater delay, comparator mode, etc.
     */
    public static final Set<String> STRUCTURAL_IGNORED_PROPERTIES = Set.of(
            "powered",
            "power",
            "triggered",
            "lit",
            "open",
            "extended",
            "locked",
            "enabled",
            "occupied",
            "phase"
    );

    public static Optional<Difference> compare(
            IntPos pos,
            BlockDescriptor expected,
            BlockDescriptor actual,
            ComparisonMode mode
    ) {
        if (!expected.blockId().equals(actual.blockId())) {
            return Optional.of(new Difference(pos, expected, actual, Set.of("<block>")));
        }

        Set<String> changed = new HashSet<>();
        Set<String> keys = new HashSet<>();
        keys.addAll(expected.properties().keySet());
        keys.addAll(actual.properties().keySet());

        for (String key : keys) {
            if (mode == ComparisonMode.STRUCTURAL && STRUCTURAL_IGNORED_PROPERTIES.contains(key)) {
                continue;
            }
            String a = expected.properties().get(key);
            String b = actual.properties().get(key);
            if (!java.util.Objects.equals(a, b)) {
                changed.add(key);
            }
        }

        if (changed.isEmpty()) return Optional.empty();
        return Optional.of(new Difference(pos, expected, actual, changed));
    }
}
