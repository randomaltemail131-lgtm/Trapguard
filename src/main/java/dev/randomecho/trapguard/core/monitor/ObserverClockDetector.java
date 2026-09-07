package dev.randomecho.trapguard.core.monitor;

import dev.randomecho.trapguard.core.model.BlockDescriptor;
import dev.randomecho.trapguard.core.model.IntPos;
import dev.randomecho.trapguard.core.model.SnapshotEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Detects the common two-observer face-to-face clock without depending on Minecraft classes. */
public final class ObserverClockDetector {
    private static final String OBSERVER = "minecraft:observer";

    private ObserverClockDetector() {}

    public static int countFaceToFacePairs(List<SnapshotEntry> snapshot, BlockReader reader) {
        Set<Pair> pairs = new HashSet<>();
        for (SnapshotEntry entry : snapshot) {
            BlockDescriptor block = entry.expected();
            if (!OBSERVER.equals(block.blockId())) continue;

            Direction facing = Direction.parse(block.properties().get("facing"));
            if (facing == null) continue;

            IntPos neighborPos = offset(entry.pos(), facing);
            BlockReader.ReadResult neighborResult = reader.read(neighborPos);
            if (!neighborResult.loaded()) continue;

            BlockDescriptor neighbor = neighborResult.block();
            if (!OBSERVER.equals(neighbor.blockId())) continue;

            Direction neighborFacing = Direction.parse(neighbor.properties().get("facing"));
            if (neighborFacing != facing.opposite()) continue;

            pairs.add(Pair.canonical(entry.pos(), neighborPos));
        }
        return pairs.size();
    }

    private static IntPos offset(IntPos pos, Direction direction) {
        return new IntPos(pos.x() + direction.dx, pos.y() + direction.dy, pos.z() + direction.dz);
    }

    private static int compare(IntPos a, IntPos b) {
        int x = Integer.compare(a.x(), b.x());
        if (x != 0) return x;
        int y = Integer.compare(a.y(), b.y());
        if (y != 0) return y;
        return Integer.compare(a.z(), b.z());
    }

    private record Pair(IntPos first, IntPos second) {
        static Pair canonical(IntPos a, IntPos b) {
            return compare(a, b) <= 0 ? new Pair(a, b) : new Pair(b, a);
        }
    }

    private enum Direction {
        DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

        final int dx;
        final int dy;
        final int dz;

        Direction(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        Direction opposite() {
            return switch (this) {
                case DOWN -> UP;
                case UP -> DOWN;
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case EAST -> WEST;
            };
        }

        static Direction parse(String value) {
            if (value == null) return null;
            try {
                return Direction.valueOf(value.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}
