package dev.randomecho.trapguard.core.model;

/** Version-independent block position used by TrapGuard's persisted data. */
public record IntPos(int x, int y, int z) implements Comparable<IntPos> {
    @Override
    public int compareTo(IntPos other) {
        int cx = Integer.compare(x, other.x);
        if (cx != 0) return cx;
        int cy = Integer.compare(y, other.y);
        if (cy != 0) return cy;
        return Integer.compare(z, other.z);
    }

    public String compact() {
        return x + "," + y + "," + z;
    }
}
