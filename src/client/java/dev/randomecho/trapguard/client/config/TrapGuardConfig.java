package dev.randomecho.trapguard.client.config;

/**
 * Small client-only configuration document. Keep gameplay/snapshot data out of this class so
 * future Minecraft ports can replace only the client integration without migrating trap files.
 */
public final class TrapGuardConfig {
    public int schemaVersion = 2;
    public String selectorItem = "minecraft:stick";
    public boolean selectorEnabled = true;
    /** Master pause. Individual trap.enabled values are not changed when this is toggled. */
    public boolean globalMonitoringEnabled = true;
    /** Cooldown is independent for each trap. */
    public int alertCooldownSeconds = 15;
    /** Normalized right/bottom anchor for the alert stack. */
    public float hudAnchorX = 0.98F;
    public float hudAnchorY = 0.95F;

    public void normalizeAfterLoad() {
        if (selectorItem == null || selectorItem.isBlank()) selectorItem = "minecraft:stick";
        alertCooldownSeconds = Math.max(1, Math.min(300, alertCooldownSeconds));
        if (!Float.isFinite(hudAnchorX)) hudAnchorX = 0.98F;
        if (!Float.isFinite(hudAnchorY)) hudAnchorY = 0.95F;
        hudAnchorX = Math.max(0.05F, Math.min(0.98F, hudAnchorX));
        hudAnchorY = Math.max(0.10F, Math.min(0.98F, hudAnchorY));
        if (schemaVersion < 2) schemaVersion = 2;
    }
}
