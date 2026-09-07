package dev.randomecho.trapguard.client.hud;

import dev.randomecho.trapguard.client.config.TrapGuardConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Compact non-chat alert stack with per-trap anti-spam cooldowns. */
public final class HudAlertManager {
    private static final long SYSTEM_LIFETIME_MILLIS = 15_000L;
    private static final long FADE_MILLIS = 2_500L;
    private static final int MAX_VISIBLE = 4;
    private static final int PANEL_WIDTH = 190;
    private static final int PANEL_HEIGHT = 34;
    private static final int GAP = 4;

    private final List<Alert> alerts = new ArrayList<>();
    private final Map<String, Long> lastShownAtByTrap = new HashMap<>();

    public void trapChanged(String trapName, int activeCount, TrapGuardConfig config) {
        long now = System.currentTimeMillis();
        Alert existing = findVisibleTrapAlert(trapName, now);
        if (existing != null) {
            existing.title = trapName;
            existing.message = activeCount + " changed watched block" + (activeCount == 1 ? "" : "s");
            existing.kind = Kind.CHANGED;
            return; // Update the existing panel without restarting its configured lifetime.
        }

        long lifetime = trapLifetimeMillis(config.alertCooldownSeconds);
        long lastShown = lastShownAtByTrap.getOrDefault(trapName, Long.MIN_VALUE);
        if (lastShown != Long.MIN_VALUE && now - lastShown < lifetime) return;
        lastShownAtByTrap.put(trapName, now);
        add(new Alert(trapName, trapName,
                activeCount + " changed watched block" + (activeCount == 1 ? "" : "s"),
                Kind.CHANGED, now, now + lifetime, trapFadeMillis(config.alertCooldownSeconds)));
    }

    public void trapRestored(String trapName, TrapGuardConfig config) {
        long now = System.currentTimeMillis();
        Alert existing = findVisibleTrapAlert(trapName, now);
        if (existing != null) {
            existing.title = trapName;
            existing.message = "Structure restored";
            existing.kind = Kind.RESTORED;
            return; // Keep the original expiry so restoration does not extend screen occupation.
        }

        long lifetime = trapLifetimeMillis(config.alertCooldownSeconds);
        long lastShown = lastShownAtByTrap.getOrDefault(trapName, Long.MIN_VALUE);
        if (lastShown != Long.MIN_VALUE && now - lastShown < lifetime) return;
        lastShownAtByTrap.put(trapName, now);
        add(new Alert(trapName, trapName, "Structure restored", Kind.RESTORED,
                now, now + lifetime, trapFadeMillis(config.alertCooldownSeconds)));
    }

    public void system(String message) {
        long now = System.currentTimeMillis();
        add(new Alert(null, "TrapGuard", message, Kind.INFO, now,
                now + SYSTEM_LIFETIME_MILLIS, FADE_MILLIS));
    }

    /** Clears only panels currently shown. Per-trap cooldown clocks are intentionally preserved. */
    public void clearVisible() {
        alerts.clear();
    }

    /** Clears both visible panels and anti-spam state for one trap. */
    public void clearTrap(String trapName) {
        if (trapName == null) return;
        alerts.removeIf(alert -> trapName.equals(alert.trapName));
        lastShownAtByTrap.remove(trapName);
    }

    /** Clears both visible panels and anti-spam state, used when changing worlds/servers. */
    public void clear() {
        alerts.clear();
        lastShownAtByTrap.clear();
    }

    /** Applies a newly configured cooldown immediately to already-visible trap alerts. */
    public void applyTrapCooldownSeconds(int seconds) {
        long lifetime = trapLifetimeMillis(seconds);
        long fade = trapFadeMillis(seconds);
        for (Alert alert : alerts) {
            if (alert.trapName == null) continue;
            alert.expiresAt = alert.createdAt + lifetime;
            alert.fadeMillis = fade;
        }
    }

    public void render(GuiGraphics graphics, TrapGuardConfig config) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;

        long now = System.currentTimeMillis();
        alerts.removeIf(alert -> alert.expiresAt <= now);
        if (alerts.isEmpty()) return;

        List<Alert> visible = alerts.stream()
                .filter(alert -> alert.expiresAt > now)
                .sorted(Comparator.comparingLong((Alert alert) -> alert.createdAt).reversed())
                .limit(MAX_VISIBLE)
                .toList();
        if (visible.isEmpty()) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int stackHeight = visible.size() * PANEL_HEIGHT + Math.max(0, visible.size() - 1) * GAP;
        int right = clamp(Math.round(config.hudAnchorX * screenWidth), PANEL_WIDTH + 4, screenWidth - 4);
        int bottom = clamp(Math.round(config.hudAnchorY * screenHeight), stackHeight + 4, screenHeight - 4);
        int x = right - PANEL_WIDTH;
        int y = bottom - PANEL_HEIGHT;
        Font font = client.font;

        for (Alert alert : visible) {
            int alpha = alphaFor(alert, now);
            int background = argb(alpha * 190 / 255, 16, 16, 16);
            int accent = switch (alert.kind) {
                case CHANGED -> argb(alpha, 255, 76, 76);
                case RESTORED -> argb(alpha, 103, 220, 120);
                case INFO -> argb(alpha, 96, 190, 255);
            };
            int text = argb(alpha, 240, 240, 240);
            int subtext = argb(alpha, 200, 200, 200);

            graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, background);
            graphics.fill(x, y, x + 3, y + PANEL_HEIGHT, accent);
            graphics.drawString(font, trim(font, alert.title, PANEL_WIDTH - 14), x + 8, y + 6, text, false);
            graphics.drawString(font, trim(font, alert.message, PANEL_WIDTH - 14), x + 8, y + 19, subtext, false);
            y -= PANEL_HEIGHT + GAP;
        }
    }

    /** Used by the HUD settings screen so position changes can be seen without generating a real alert. */
    public void renderPreview(GuiGraphics graphics, TrapGuardConfig config) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int right = clamp(Math.round(config.hudAnchorX * screenWidth), PANEL_WIDTH + 4, screenWidth - 4);
        int bottom = clamp(Math.round(config.hudAnchorY * screenHeight), PANEL_HEIGHT + 4, screenHeight - 4);
        int x = right - PANEL_WIDTH;
        int y = bottom - PANEL_HEIGHT;
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xD0101010);
        graphics.fill(x, y, x + 3, y + PANEL_HEIGHT, 0xFF60BEFF);
        graphics.drawString(client.font, "TrapGuard HUD preview", x + 8, y + 6, 0xFFF0F0F0, false);
        graphics.drawString(client.font, "Alerts appear here", x + 8, y + 19, 0xFFC8C8C8, false);
    }

    private void add(Alert alert) {
        alerts.add(alert);
        while (alerts.size() > 16) alerts.removeFirst();
    }

    private Alert findVisibleTrapAlert(String trapName, long now) {
        for (Iterator<Alert> iterator = alerts.iterator(); iterator.hasNext();) {
            Alert alert = iterator.next();
            if (alert.expiresAt <= now) {
                iterator.remove();
                continue;
            }
            if (trapName.equals(alert.trapName)) return alert;
        }
        return null;
    }

    private static long trapLifetimeMillis(int seconds) {
        return Math.max(1L, seconds) * 1000L;
    }

    private static long trapFadeMillis(int seconds) {
        return seconds <= 3 ? 0L : FADE_MILLIS;
    }

    private static int alphaFor(Alert alert, long now) {
        if (alert.fadeMillis <= 0L) return 255;
        long remaining = alert.expiresAt - now;
        if (remaining >= alert.fadeMillis) return 255;
        return clamp((int) Math.round(255.0 * remaining / alert.fadeMillis), 0, 255);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return ((alpha & 0xFF) << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }

    private static String trim(Font font, String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "...";
        int allowed = Math.max(0, maxWidth - font.width(ellipsis));
        return font.plainSubstrByWidth(value, allowed) + ellipsis;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Kind { CHANGED, RESTORED, INFO }

    private static final class Alert {
        private final String trapName;
        private String title;
        private String message;
        private Kind kind;
        private final long createdAt;
        private long expiresAt;
        private long fadeMillis;

        private Alert(String trapName, String title, String message, Kind kind, long createdAt, long expiresAt, long fadeMillis) {
            this.trapName = trapName;
            this.title = title;
            this.message = message;
            this.kind = kind;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.fadeMillis = fadeMillis;
        }
    }
}
