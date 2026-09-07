package dev.randomecho.trapguard.client;

import dev.randomecho.trapguard.client.compat.GizmoOverlay;
import dev.randomecho.trapguard.client.compat.MinecraftBlockAdapter;
import dev.randomecho.trapguard.client.config.ConfigStore;
import dev.randomecho.trapguard.client.config.TrapGuardConfig;
import dev.randomecho.trapguard.client.hud.HudAlertManager;
import dev.randomecho.trapguard.client.export.SchemExporter;
import dev.randomecho.trapguard.client.storage.TrapStore;
import dev.randomecho.trapguard.core.model.ComparisonMode;
import dev.randomecho.trapguard.core.model.IntPos;
import dev.randomecho.trapguard.core.model.Region;
import dev.randomecho.trapguard.core.model.SnapshotEntry;
import dev.randomecho.trapguard.core.model.TrapDatabase;
import dev.randomecho.trapguard.core.model.TrapDefinition;
import dev.randomecho.trapguard.core.monitor.Difference;
import dev.randomecho.trapguard.core.monitor.MonitorDelta;
import dev.randomecho.trapguard.core.monitor.MonitorEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TrapGuardController {
    private static final Logger LOGGER = LoggerFactory.getLogger("TrapGuard");
    private static final int TOTAL_SCAN_BUDGET_PER_TICK = 4096;

    private final SelectionSession selection = new SelectionSession();
    private final TrapStore store = new TrapStore();
    private final ConfigStore configStore = new ConfigStore();
    private final MonitorEngine monitor = new MonitorEngine();
    private final HudAlertManager alerts = new HudAlertManager();
    private TrapDatabase database;
    private TrapGuardConfig config;
    private String loadedDimension = "";
    private String loadedServerScope = "";
    private boolean loadFailureReported;
    private boolean configLoadFailureReported;
    private IntPos focusedDifference;
    private long focusedDifferenceUntil;

    public TrapGuardController() {
        try {
            database = store.load();
        } catch (RuntimeException exception) {
            database = new TrapDatabase();
            loadFailureReported = true;
            LOGGER.error("Could not load TrapGuard data; starting with an empty in-memory database", exception);
        }

        try {
            config = configStore.load();
        } catch (RuntimeException exception) {
            config = new TrapGuardConfig();
            configLoadFailureReported = true;
            LOGGER.error("Could not load TrapGuard config; using defaults", exception);
        }
    }

    public SelectionSession selection() { return selection; }
    public TrapDatabase database() { return database; }
    public TrapGuardConfig config() { return config; }
    public HudAlertManager alerts() { return alerts; }

    public void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            if (!loadedDimension.isEmpty() || !loadedServerScope.isEmpty()) selection.clear();
            loadedDimension = "";
            loadedServerScope = "";
            monitor.clearAll();
            focusedDifference = null;
            return;
        }

        if (loadFailureReported) {
            loadFailureReported = false;
            client.player.displayClientMessage(Component.literal("[TrapGuard] traps.json could not be loaded; check latest.log."), false);
        }
        if (configLoadFailureReported) {
            configLoadFailureReported = false;
            client.player.displayClientMessage(Component.literal("[TrapGuard] config.json could not be loaded; settings were reset."), false);
        }

        String dimension = MinecraftBlockAdapter.currentDimension(client);
        String serverScope = MinecraftBlockAdapter.currentServerScope(client);
        if (!dimension.equals(loadedDimension) || !serverScope.equals(loadedServerScope)) {
            selection.clear();
            loadedDimension = dimension;
            loadedServerScope = serverScope;
            monitor.clearAll();
            alerts.clear();
            focusedDifference = null;
        }

        List<TrapDefinition> inWorld = database.traps.stream()
                .filter(trap -> dimension.equals(trap.dimension))
                .filter(trap -> scopeMatches(trap, serverScope))
                .toList();

        List<TrapDefinition> enabled = config.globalMonitoringEnabled
                ? inWorld.stream()
                    .filter(trap -> trap.enabled)
                    .filter(trap -> trap.snapshot != null && !trap.snapshot.isEmpty())
                    .toList()
                : List.of();

        int perTrapBudget = enabled.isEmpty() ? 0 : Math.max(1, TOTAL_SCAN_BUDGET_PER_TICK / enabled.size());
        for (TrapDefinition trap : enabled) {
            MonitorDelta delta = monitor.scan(trap, MinecraftBlockAdapter.reader(client), perTrapBudget);
            if (delta.skippedUnloaded() > 0) monitor.invalidateCycle(trap.name);
            if (!delta.newlyBroken().isEmpty()) {
                alerts.trapChanged(trap.name, delta.active().size(), config);
            }
            if (!delta.resolved().isEmpty() && delta.active().isEmpty()) {
                alerts.trapRestored(trap.name, config);
            }
        }

        GizmoOverlay.renderSelection(client, selection);
        for (TrapDefinition trap : inWorld) {
            GizmoOverlay.renderTrap(trap, monitor.active(trap.name));
        }
        if (focusedDifference != null) {
            if (System.currentTimeMillis() < focusedDifferenceUntil) GizmoOverlay.renderFocus(focusedDifference);
            else focusedDifference = null;
        }
    }

    public Optional<IntPos> targetedBlock(Minecraft client) { return MinecraftBlockAdapter.targetedBlock(client); }

    public boolean isSelectorStack(ItemStack stack) {
        if (!config.selectorEnabled || stack == null || stack.isEmpty()) return false;
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(config.selectorItem);
    }

    public String selectorItemId() { return config.selectorItem; }

    public OperationResult setSelectorFromHeld(Minecraft client) {
        if (client.player == null) return OperationResult.error("No player is loaded.");
        ItemStack stack = client.player.getMainHandItem();
        if (stack.isEmpty()) return OperationResult.error("Hold an item in your main hand first.");
        String previous = config.selectorItem;
        config.selectorItem = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        try { persistConfig(); }
        catch (RuntimeException exception) {
            config.selectorItem = previous;
            return OperationResult.error("Could not write config.json: " + exception.getMessage());
        }
        return OperationResult.ok("Selector item set to " + config.selectorItem + ".");
    }

    public OperationResult resetSelectorItem() {
        String previous = config.selectorItem;
        config.selectorItem = "minecraft:stick";
        try { persistConfig(); }
        catch (RuntimeException exception) {
            config.selectorItem = previous;
            return OperationResult.error("Could not write config.json: " + exception.getMessage());
        }
        return OperationResult.ok("Selector item reset to minecraft:stick.");
    }

    public OperationResult setSelectorEnabled(boolean enabled) {
        boolean previous = config.selectorEnabled;
        config.selectorEnabled = enabled;
        try { persistConfig(); }
        catch (RuntimeException exception) {
            config.selectorEnabled = previous;
            return OperationResult.error("Could not write config.json: " + exception.getMessage());
        }
        return OperationResult.ok("Selector clicks " + (enabled ? "enabled" : "disabled") + ".");
    }

    public OperationResult setGlobalMonitoringEnabled(boolean enabled) {
        boolean previous = config.globalMonitoringEnabled;
        config.globalMonitoringEnabled = enabled;
        try { persistConfig(); }
        catch (RuntimeException exception) {
            config.globalMonitoringEnabled = previous;
            return OperationResult.error("Could not write config.json: " + exception.getMessage());
        }
        if (enabled && !previous) monitor.resetScanProgress();
        alerts.system(enabled ? "Monitoring resumed" : "Monitoring paused");
        return OperationResult.ok("Global monitoring " + (enabled ? "resumed" : "paused") + ".");
    }

    public OperationResult setAlertCooldownSeconds(int seconds) {
        if (seconds < 1 || seconds > 300) return OperationResult.error("Alert cooldown must be between 1 and 300 seconds.");
        int previous = config.alertCooldownSeconds;
        config.alertCooldownSeconds = seconds;
        try { persistConfig(); }
        catch (RuntimeException exception) {
            config.alertCooldownSeconds = previous;
            return OperationResult.error("Could not write config.json: " + exception.getMessage());
        }
        alerts.applyTrapCooldownSeconds(seconds);
        return OperationResult.ok("Per-trap alert cooldown set to " + seconds + " seconds.");
    }

    public void previewHudPosition(float x, float y) {
        config.hudAnchorX = Math.max(0.05F, Math.min(0.98F, x));
        config.hudAnchorY = Math.max(0.10F, Math.min(0.98F, y));
    }

    public OperationResult commitHudPosition(float oldX, float oldY) {
        try { persistConfig(); }
        catch (RuntimeException exception) {
            config.hudAnchorX = oldX;
            config.hudAnchorY = oldY;
            return OperationResult.error("Could not write config.json: " + exception.getMessage());
        }
        return OperationResult.ok("HUD position saved.");
    }

    public OperationResult moveHud(float x, float y) {
        float oldX = config.hudAnchorX;
        float oldY = config.hudAnchorY;
        config.hudAnchorX = Math.max(0.05F, Math.min(0.98F, x));
        config.hudAnchorY = Math.max(0.10F, Math.min(0.98F, y));
        try { persistConfig(); }
        catch (RuntimeException exception) {
            config.hudAnchorX = oldX;
            config.hudAnchorY = oldY;
            return OperationResult.error("Could not write config.json: " + exception.getMessage());
        }
        return OperationResult.ok("HUD position saved.");
    }

    public Optional<TrapDefinition> findTrap(String name) {
        return database.traps.stream().filter(t -> t.name.equalsIgnoreCase(name)).findFirst();
    }

    public List<TrapDefinition> trapsSorted() {
        return database.traps.stream().sorted(Comparator.comparing(t -> t.name.toLowerCase(Locale.ROOT))).toList();
    }

    public List<TrapDefinition> trapsSorted(String groupFilter) {
        if (groupFilter == null || "*".equals(groupFilter)) return trapsSorted();
        return database.traps.stream()
                .filter(trap -> trap.group.equalsIgnoreCase(groupFilter))
                .sorted(Comparator.comparing(t -> t.name.toLowerCase(Locale.ROOT)))
                .toList();
    }

    public List<String> groupsSorted() {
        List<String> result = new ArrayList<>();
        result.add(TrapDefinition.UNGROUPED);
        database.groups.stream()
                .filter(group -> !TrapDefinition.UNGROUPED.equalsIgnoreCase(group))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(result::add);
        return result;
    }

    public OperationResult createGroup(String rawName) {
        String name = cleanGroupName(rawName);
        if (name == null) return OperationResult.error("Folder name must be 1-32 printable characters.");
        if (groupExists(name)) return OperationResult.error("A folder named '" + name + "' already exists.");
        database.groups.add(name);
        try { persist(); }
        catch (RuntimeException exception) {
            database.groups.remove(name);
            return OperationResult.error("Could not write traps.json: " + exception.getMessage());
        }
        return OperationResult.ok("Created folder '" + name + "'.");
    }

    public OperationResult renameGroup(String oldName, String rawNewName) {
        if (TrapDefinition.UNGROUPED.equalsIgnoreCase(oldName)) return OperationResult.error("Ungrouped cannot be renamed.");
        String newName = cleanGroupName(rawNewName);
        if (newName == null) return OperationResult.error("Folder name must be 1-32 printable characters.");
        String actualOld = findGroup(oldName).orElse(null);
        if (actualOld == null) return OperationResult.error("Unknown folder: " + oldName);
        if (!actualOld.equalsIgnoreCase(newName) && groupExists(newName)) return OperationResult.error("A folder named '" + newName + "' already exists.");

        int index = database.groups.indexOf(actualOld);
        List<TrapDefinition> affected = database.traps.stream().filter(t -> t.group.equalsIgnoreCase(actualOld)).toList();
        database.groups.set(index, newName);
        affected.forEach(trap -> trap.group = newName);
        try { persist(); }
        catch (RuntimeException exception) {
            database.groups.set(index, actualOld);
            affected.forEach(trap -> trap.group = actualOld);
            return OperationResult.error("Could not write traps.json: " + exception.getMessage());
        }
        return OperationResult.ok("Renamed folder to '" + newName + "'.");
    }

    public OperationResult deleteGroup(String name) {
        if (TrapDefinition.UNGROUPED.equalsIgnoreCase(name)) return OperationResult.error("Ungrouped cannot be deleted.");
        String actual = findGroup(name).orElse(null);
        if (actual == null) return OperationResult.error("Unknown folder: " + name);
        int index = database.groups.indexOf(actual);
        List<TrapDefinition> affected = database.traps.stream().filter(t -> t.group.equalsIgnoreCase(actual)).toList();
        database.groups.remove(actual);
        affected.forEach(trap -> trap.group = TrapDefinition.UNGROUPED);
        try { persist(); }
        catch (RuntimeException exception) {
            database.groups.add(index, actual);
            affected.forEach(trap -> trap.group = actual);
            return OperationResult.error("Could not write traps.json: " + exception.getMessage());
        }
        return OperationResult.ok("Deleted folder '" + actual + "'; its traps were moved to Ungrouped.");
    }

    public OperationResult setGroup(String trapName, String groupName) {
        TrapDefinition trap = findTrap(trapName).orElse(null);
        if (trap == null) return OperationResult.error("Unknown trap: " + trapName);
        String actual = findGroup(groupName).orElse(null);
        if (actual == null) return OperationResult.error("Unknown folder: " + groupName);
        String previous = trap.group;
        trap.group = actual;
        try { persist(); }
        catch (RuntimeException exception) {
            trap.group = previous;
            return OperationResult.error("Could not write traps.json: " + exception.getMessage());
        }
        return OperationResult.ok("Moved '" + trap.name + "' to " + actual + ".");
    }

    public int trapCountInGroup(String groupName) {
        return (int) database.traps.stream().filter(t -> t.group.equalsIgnoreCase(groupName)).count();
    }

    private boolean groupExists(String name) { return findGroup(name).isPresent(); }

    private Optional<String> findGroup(String name) {
        if (name == null) return Optional.empty();
        return database.groups.stream().filter(group -> group.equalsIgnoreCase(name)).findFirst();
    }

    public OperationResult saveSelection(Minecraft client, String name) {
        String cleanName = cleanName(name);
        if (cleanName == null) return OperationResult.error("Trap name must contain only letters, numbers, _, or - (1-48 characters).");
        if (findTrap(cleanName).isPresent()) return OperationResult.error("A trap named '" + cleanName + "' already exists. Delete it first or use resnapshot.");
        Optional<Region> main = selection.mainRegion();
        if (main.isEmpty()) return OperationResult.error("Set pos1 and pos2 first.");
        if (client.level == null) return OperationResult.error("No world is loaded.");

        List<Region> regions = List.of(main.get());
        SnapshotBuilder.Result snapshot = SnapshotBuilder.build(client, regions, selection.extraBlocks());
        if (!snapshot.ok()) return OperationResult.error(snapshot.error());

        TrapDefinition trap = new TrapDefinition(cleanName, MinecraftBlockAdapter.currentDimension(client), MinecraftBlockAdapter.currentServerScope(client));
        trap.regions = new ArrayList<>(regions);
        trap.extraBlocks.addAll(selection.extraBlocks());
        trap.snapshot = new ArrayList<>(snapshot.snapshot());
        trap.mode = selection.draftMode();
        trap.group = TrapDefinition.UNGROUPED;
        database.traps.add(trap);
        try { persist(); }
        catch (RuntimeException exception) {
            database.traps.remove(trap);
            return OperationResult.error("Could not write traps.json: " + exception.getMessage());
        }
        monitor.clearTrap(trap.name);
        return OperationResult.ok("Saved '" + trap.name + "' with " + trap.snapshot.size() + " watched blocks in " + trap.mode + " mode." + observerClockWarning(snapshot.observerClockPairs(), trap.mode));
    }

    public OperationResult resnapshot(Minecraft client, String name) {
        TrapDefinition trap = findTrap(name).orElse(null);
        if (trap == null) return OperationResult.error("Unknown trap: " + name);
        if (client.level == null) return OperationResult.error("No world is loaded.");
        String dimension = MinecraftBlockAdapter.currentDimension(client);
        if (!dimension.equals(trap.dimension)) return OperationResult.error("Trap '" + trap.name + "' belongs to " + trap.dimension + ", not " + dimension + ".");
        String currentScope = MinecraftBlockAdapter.currentServerScope(client);
        if (!scopeMatches(trap, currentScope)) return OperationResult.error("Trap '" + trap.name + "' belongs to another server/world.");

        SnapshotBuilder.Result snapshot = SnapshotBuilder.build(client, trap.regions, trap.extraBlocks);
        if (!snapshot.ok()) return OperationResult.error(snapshot.error());
        List<SnapshotEntry> previous = trap.snapshot;
        String previousScope = trap.serverScope;
        trap.snapshot = new ArrayList<>(snapshot.snapshot());
        trap.serverScope = currentScope;
        try { persist(); }
        catch (RuntimeException exception) {
            trap.snapshot = previous;
            trap.serverScope = previousScope;
            return OperationResult.error("Could not write traps.json: " + exception.getMessage());
        }
        monitor.clearTrap(trap.name);
        alerts.clearTrap(trap.name);
        return OperationResult.ok("Resnapshotted '" + trap.name + "' (" + trap.snapshot.size() + " watched blocks)." + observerClockWarning(snapshot.observerClockPairs(), trap.mode));
    }

    public OperationResult setMode(String name, ComparisonMode mode) {
        TrapDefinition trap = findTrap(name).orElse(null);
        if (trap == null) return OperationResult.error("Unknown trap: " + name);
        ComparisonMode previous = trap.mode;
        trap.mode = mode;
        try { persist(); }
        catch (RuntimeException exception) {
            trap.mode = previous;
            return OperationResult.error("Could not write traps.json: " + exception.getMessage());
        }
        monitor.clearTrap(trap.name);
        alerts.clearTrap(trap.name);
        return OperationResult.ok("'" + trap.name + "' mode: " + mode + ".");
    }

    public OperationResult setEnabled(String name, boolean enabled) {
        TrapDefinition trap = findTrap(name).orElse(null);
        if (trap == null) return OperationResult.error("Unknown trap: " + name);
        boolean previous = trap.enabled;
        trap.enabled = enabled;
        try { persist(); }
        catch (RuntimeException exception) {
            trap.enabled = previous;
            return OperationResult.error("Could not write traps.json: " + exception.getMessage());
        }
        monitor.clearTrap(trap.name);
        alerts.clearTrap(trap.name);
        return OperationResult.ok("'" + trap.name + "' monitoring " + (enabled ? "enabled" : "disabled") + ".");
    }

    public OperationResult setOverlay(String name, boolean overlay) {
        TrapDefinition trap = findTrap(name).orElse(null);
        if (trap == null) return OperationResult.error("Unknown trap: " + name);
        boolean previous = trap.overlay;
        trap.overlay = overlay;
        try { persist(); }
        catch (RuntimeException exception) {
            trap.overlay = previous;
            return OperationResult.error("Could not write traps.json: " + exception.getMessage());
        }
        return OperationResult.ok("'" + trap.name + "' saved-area overlay " + (overlay ? "enabled" : "disabled") + ".");
    }

    public OperationResult delete(String name) {
        TrapDefinition trap = findTrap(name).orElse(null);
        if (trap == null) return OperationResult.error("Unknown trap: " + name);
        int index = database.traps.indexOf(trap);
        database.traps.remove(trap);
        try { persist(); }
        catch (RuntimeException exception) {
            database.traps.add(index, trap);
            return OperationResult.error("Could not write traps.json: " + exception.getMessage());
        }
        monitor.clearTrap(trap.name);
        alerts.clearTrap(trap.name);
        return OperationResult.ok("Deleted '" + trap.name + "'.");
    }

    public OperationResult loadSelection(Minecraft client, String name) {
        TrapDefinition trap = findTrap(name).orElse(null);
        if (trap == null) return OperationResult.error("Unknown trap: " + name);
        if (client.level == null) return OperationResult.error("No world is loaded.");
        String dimension = MinecraftBlockAdapter.currentDimension(client);
        String scope = MinecraftBlockAdapter.currentServerScope(client);
        if (!dimension.equals(trap.dimension)) return OperationResult.error("Trap '" + trap.name + "' belongs to " + trap.dimension + ", not " + dimension + ".");
        if (!scopeMatches(trap, scope)) return OperationResult.error("Trap '" + trap.name + "' belongs to another server/world.");
        selection.loadFrom(trap);
        String suffix = trap.regions.size() > 1 ? " Note: this editor shows the first of " + trap.regions.size() + " regions; the remaining regions are preserved when you apply changes." : "";
        return OperationResult.ok("Loaded '" + trap.name + "' into the editor. Use Apply Changes after editing." + suffix);
    }

    public OperationResult applySelection(Minecraft client, String name) {
        TrapDefinition trap = findTrap(name).orElse(null);
        if (trap == null) return OperationResult.error("Unknown trap: " + name);
        if (client.level == null) return OperationResult.error("No world is loaded.");
        Optional<Region> main = selection.mainRegion();
        if (main.isEmpty()) return OperationResult.error("Set pos1 and pos2 first.");

        String dimension = MinecraftBlockAdapter.currentDimension(client);
        String scope = MinecraftBlockAdapter.currentServerScope(client);
        if (!dimension.equals(trap.dimension)) return OperationResult.error("Trap '" + trap.name + "' belongs to another dimension.");
        if (!scopeMatches(trap, scope)) return OperationResult.error("Trap '" + trap.name + "' belongs to another server/world.");

        List<Region> regions = new ArrayList<>();
        regions.add(main.get());
        if (trap.regions != null && trap.regions.size() > 1) regions.addAll(trap.regions.subList(1, trap.regions.size()));
        SnapshotBuilder.Result snapshot = SnapshotBuilder.build(client, regions, selection.extraBlocks());
        if (!snapshot.ok()) return OperationResult.error(snapshot.error());

        List<Region> oldRegions = trap.regions;
        Set<IntPos> oldExtras = trap.extraBlocks;
        List<SnapshotEntry> oldSnapshot = trap.snapshot;
        String oldScope = trap.serverScope;
        ComparisonMode oldMode = trap.mode;
        trap.regions = new ArrayList<>(regions);
        trap.extraBlocks = new LinkedHashSet<>(selection.extraBlocks());
        trap.snapshot = new ArrayList<>(snapshot.snapshot());
        trap.serverScope = scope;
        trap.mode = selection.draftMode();
        try { persist(); }
        catch (RuntimeException exception) {
            trap.regions = oldRegions;
            trap.extraBlocks = oldExtras;
            trap.snapshot = oldSnapshot;
            trap.serverScope = oldScope;
            trap.mode = oldMode;
            return OperationResult.error("Could not write traps.json: " + exception.getMessage());
        }
        monitor.clearTrap(trap.name);
        alerts.clearTrap(trap.name);
        selection.clear();
        return OperationResult.ok("Applied editor selection to '" + trap.name + "' and saved " + trap.snapshot.size() + " watched blocks in " + trap.mode + " mode." + observerClockWarning(snapshot.observerClockPairs(), trap.mode));
    }

    public OperationResult exportSchem(String name) {
        TrapDefinition trap = findTrap(name).orElse(null);
        if (trap == null) return OperationResult.error("Unknown trap: " + name);
        SchemExporter.Result result = SchemExporter.export(trap);
        if (!result.ok()) return OperationResult.error(result.error());
        return OperationResult.ok("Exported " + result.file().getFileName() + " to the instance schematics folder.");
    }

    public int activeDifferenceCount(String name) {
        TrapDefinition trap = findTrap(name).orElse(null);
        return trap == null ? 0 : monitor.activeCount(trap.name);
    }

    public Map<IntPos, Difference> activeDifferences(String name) {
        TrapDefinition trap = findTrap(name).orElse(null);
        return trap == null ? Map.of() : monitor.active(trap.name);
    }

    public List<Difference> activeDifferencesSorted(String name) {
        return activeDifferences(name).values().stream()
                .sorted(Comparator.comparingInt((Difference d) -> d.pos().y())
                        .thenComparingInt(d -> d.pos().x())
                        .thenComparingInt(d -> d.pos().z()))
                .toList();
    }

    public void highlightDifference(IntPos pos) {
        focusedDifference = pos;
        focusedDifferenceUntil = System.currentTimeMillis() + 8_000L;
    }

    public TrapStatus trapStatus(Minecraft client, TrapDefinition trap) {
        if (client.level == null) return TrapStatus.OTHER_WORLD;
        String dimension = MinecraftBlockAdapter.currentDimension(client);
        String scope = MinecraftBlockAdapter.currentServerScope(client);
        if (!trap.dimension.equals(dimension) || !scopeMatches(trap, scope)) return TrapStatus.OTHER_WORLD;
        if (!trap.enabled) return TrapStatus.DISABLED;
        if (!config.globalMonitoringEnabled) return TrapStatus.PAUSED;
        if (activeDifferenceCount(trap.name) > 0) return TrapStatus.CHANGED;
        if (trap.snapshot == null || trap.snapshot.isEmpty()) return TrapStatus.EMPTY;

        // Status should reflect the positions TrapGuard actually monitors, not the editor geometry.
        // This also keeps legacy/corrupt selections from producing false PARTIAL/UNLOADED states.
        Set<Long> chunks = new LinkedHashSet<>();
        for (SnapshotEntry entry : trap.snapshot) {
            IntPos pos = entry.pos();
            chunks.add(packChunk(pos.x() >> 4, pos.z() >> 4));
        }
        int loaded = 0;
        for (long packed : chunks) {
            int chunkX = (int) (packed >> 32);
            int chunkZ = (int) packed;
            if (MinecraftBlockAdapter.isChunkLoaded(client, chunkX, chunkZ)) loaded++;
        }
        if (loaded == 0) return TrapStatus.UNLOADED;
        if (loaded < chunks.size()) return TrapStatus.PARTIAL;
        if (!monitor.hasCompletedCycle(trap.name, trap.snapshot.size())) return TrapStatus.SCANNING;
        return TrapStatus.SAFE;
    }


    private static long packChunk(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    public String differenceSummary(Difference difference) {
        if (difference.blockChanged()) {
            return difference.pos().compact() + "  " + difference.expected().blockId() + " -> " + difference.actual().blockId();
        }
        List<String> changes = new ArrayList<>();
        for (String property : difference.changedProperties()) {
            String expected = difference.expected().properties().getOrDefault(property, "<unset>");
            String actual = difference.actual().properties().getOrDefault(property, "<unset>");
            changes.add(property + ": " + expected + " -> " + actual);
        }
        return difference.pos().compact() + "  " + String.join(", ", changes);
    }

    public void persist() { store.save(database); }
    public void persistConfig() { configStore.save(config); }

    public static boolean scopeMatches(TrapDefinition trap, String currentScope) {
        if (trap.serverScope == null || trap.serverScope.isBlank() || "*".equals(trap.serverScope)) return true;
        if ("singleplayer".equals(trap.serverScope) && currentScope != null && currentScope.startsWith("singleplayer:")) return true;
        return trap.serverScope.equals(currentScope);
    }

    private static String observerClockWarning(int pairs, ComparisonMode mode) {
        if (pairs <= 0) return "";
        String noun = pairs == 1 ? "pair" : "pairs";
        if (mode == ComparisonMode.EXACT) return " Warning: detected " + pairs + " face-to-face observer clock " + noun + "; Exact mode can report their powered pulses.";
        return " Detected " + pairs + " face-to-face observer clock " + noun + "; Structural mode ignores their powered pulses.";
    }

    private static String cleanName(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.length() < 1 || value.length() > 48) return null;
        if (!value.matches("[A-Za-z0-9_-]+")) return null;
        return value;
    }

    private static String cleanGroupName(String raw) {
        if (raw == null) return null;
        String value = raw.trim().replaceAll("\\s+", " ");
        if (value.length() < 1 || value.length() > 32 || "*".equals(value)) return null;
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return null;
        return value;
    }

    public enum TrapStatus {
        SAFE("SAFE"), CHANGED("CHANGED"), SCANNING("SCANNING"), UNLOADED("UNLOADED"), PARTIAL("PARTIAL"), PAUSED("PAUSED"),
        DISABLED("DISABLED"), OTHER_WORLD("OTHER WORLD"), EMPTY("EMPTY");
        private final String label;
        TrapStatus(String label) { this.label = label; }
        public String label() { return label; }
    }

    public record OperationResult(boolean ok, String message) {
        public static OperationResult ok(String message) { return new OperationResult(true, message); }
        public static OperationResult error(String message) { return new OperationResult(false, message); }
    }
}
