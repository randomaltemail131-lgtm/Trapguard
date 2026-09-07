package dev.randomecho.trapguard.core.monitor;

import dev.randomecho.trapguard.core.model.BlockDescriptor;
import dev.randomecho.trapguard.core.model.IntPos;

public interface BlockReader {
    ReadResult read(IntPos pos);

    record ReadResult(boolean loaded, BlockDescriptor block) {
        public static ReadResult unloaded() {
            return new ReadResult(false, null);
        }

        public static ReadResult loaded(BlockDescriptor block) {
            return new ReadResult(true, block);
        }
    }
}
