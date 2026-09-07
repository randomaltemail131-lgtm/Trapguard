package dev.randomecho.trapguard.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory = FabricLoader.getInstance().getConfigDir().resolve("trapguard");
    private final Path file = directory.resolve("config.json");

    public TrapGuardConfig load() {
        if (!Files.exists(file)) return new TrapGuardConfig();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            TrapGuardConfig config = GSON.fromJson(reader, TrapGuardConfig.class);
            if (config == null) config = new TrapGuardConfig();
            config.normalizeAfterLoad();
            return config;
        } catch (IOException | JsonParseException exception) {
            throw new IllegalStateException("Could not load " + file, exception);
        }
    }

    public void save(TrapGuardConfig config) {
        try {
            Files.createDirectories(directory);
            Path temp = directory.resolve("config.json.tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
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
}
