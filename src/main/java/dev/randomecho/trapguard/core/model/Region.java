package dev.randomecho.trapguard.core.model;

import java.util.Objects;
import java.util.function.Consumer;

/** Inclusive cuboid. Coordinates are normalized at construction. */
public record Region(IntPos min, IntPos max) {
    public Region {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        int minX = Math.min(min.x(), max.x());
        int minY = Math.min(min.y(), max.y());
        int minZ = Math.min(min.z(), max.z());
        int maxX = Math.max(min.x(), max.x());
        int maxY = Math.max(min.y(), max.y());
        int maxZ = Math.max(min.z(), max.z());
        min = new IntPos(minX, minY, minZ);
        max = new IntPos(maxX, maxY, maxZ);
    }

    public static Region between(IntPos a, IntPos b) {
        return new Region(a, b);
    }

    public long blockCount() {
        long sx = (long) max.x() - min.x() + 1L;
        long sy = (long) max.y() - min.y() + 1L;
        long sz = (long) max.z() - min.z() + 1L;
        return Math.multiplyExact(Math.multiplyExact(sx, sy), sz);
    }

    public boolean contains(IntPos pos) {
        return pos.x() >= min.x() && pos.x() <= max.x()
                && pos.y() >= min.y() && pos.y() <= max.y()
                && pos.z() >= min.z() && pos.z() <= max.z();
    }

    public void forEach(Consumer<IntPos> consumer) {
        for (int y = min.y(); y <= max.y(); y++) {
            for (int z = min.z(); z <= max.z(); z++) {
                for (int x = min.x(); x <= max.x(); x++) {
                    consumer.accept(new IntPos(x, y, z));
                }
            }
        }
    }
}
