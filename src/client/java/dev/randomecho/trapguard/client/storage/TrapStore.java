package dev.randomecho.trapguard.client.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.randomecho.trapguard.core.model.TrapDatabase;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class TrapStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory = FabricLoader.getInstance().getConfigDir().resolve("trapguard");
    private final Path file = directory.resolve("traps.json");

    public TrapDatabase load() {
        if (!Files.exists(file)) return new TrapDatabase();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            TrapDatabase database = GSON.fromJson(reader, TrapDatabase.class);
            if (database == null) database = new TrapDatabase();
            database.normalizeAfterLoad();
            return database;
        } catch (IOException | JsonParseException exception) {
            throw new IllegalStateException("Could not load " + file, exception);
        }
    }

    public void save(TrapDatabase database) {
        try {
            Files.createDirectories(directory);
            Path temp = directory.resolve("traps.json.tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(database, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailed) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save " + file, exception);
        }
    }

    public Path file() {
        return file;
    }
}
