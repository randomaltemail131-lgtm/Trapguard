package dev.randomecho.trapguard.core.model;

public enum ComparisonMode {
    STRUCTURAL,
    EXACT;

    public static ComparisonMode parse(String value) {
        return switch (value.toLowerCase()) {
            case "structural", "structure", "s" -> STRUCTURAL;
            case "exact", "e" -> EXACT;
            default -> throw new IllegalArgumentException("Unknown comparison mode: " + value);
        };
    }
}
