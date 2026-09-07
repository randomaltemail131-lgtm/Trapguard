package dev.randomecho.trapguard.client.export;

import dev.randomecho.trapguard.core.export.SpongeSchemV2Writer;
import dev.randomecho.trapguard.core.model.TrapDefinition;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Minecraft/Fabric path + data-version bridge for the portable Sponge schematic writer. */
public final class SchemExporter {
    private SchemExporter() {}

    public static Result export(TrapDefinition trap) {
        if (trap == null || trap.snapshot == null || trap.snapshot.isEmpty()) {
            return Result.error("This trap has no saved snapshot to export.");
        }

        Path directory = FabricLoader.getInstance().getGameDir().resolve("schematics");
        Path file = directory.resolve(trap.name + ".schem");
        Path temp = null;
        try {
            Files.createDirectories(directory);
            temp = Files.createTempFile(directory, trap.name + ".", ".schem.tmp");
            try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                SpongeSchemV2Writer.write(trap, SharedConstants.WORLD_VERSION, out);
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
        } catch (IllegalArgumentException | IOException exception) {
            return Result.error("Could not export " + trap.name + ".schem: " + exception.getMessage());
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            }
        }
        return Result.success(file);
    }

    public record Result(boolean ok, Path file, String error) {
        public static Result success(Path file) { return new Result(true, file, null); }
        public static Result error(String error) { return new Result(false, null, error); }
    }
}
