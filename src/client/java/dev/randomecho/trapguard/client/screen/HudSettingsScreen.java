package dev.randomecho.trapguard.client.screen;

import dev.randomecho.trapguard.client.TrapGuardController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Alert cooldown and freely movable HUD anchor settings. */
public final class HudSettingsScreen extends Screen {
    private static final int PREVIEW_W = 190;
    private static final int PREVIEW_H = 34;

    private final TrapGuardController controller;
    private final Screen parent;
    private EditBox cooldownBox;
    private String status = "";
    private boolean draggingPreview;
    private float dragStartX;
    private float dragStartY;
    private double dragOffsetX;
    private double dragOffsetY;
    private int resetButtonY;
    private int backButtonY;

    public HudSettingsScreen(TrapGuardController controller, Screen parent) {
        super(Component.literal("TrapGuard HUD / Alerts"));
        this.controller = controller;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int w = Math.min(360, Math.max(260, width - 32));
        int left = center - w / 2;

        cooldownBox = new EditBox(font, left, 48, 110, 20, Component.literal("Cooldown seconds"));
        cooldownBox.setMaxLength(3);
        cooldownBox.setValue(Integer.toString(controller.config().alertCooldownSeconds));
        addRenderableWidget(cooldownBox);
        addRenderableWidget(Button.builder(Component.literal("Apply Cooldown"), button -> {
            try {
                int seconds = Integer.parseInt(cooldownBox.getValue().trim());
                apply(controller.setAlertCooldownSeconds(seconds));
            } catch (NumberFormatException exception) {
                status = "Cooldown must be a whole number from 1 to 300.";
            }
        }).tooltip(Tooltip.create(Component.literal("Sets both the on-screen lifetime and per-trap anti-spam cooldown. Default: 15 seconds.")))
                .bounds(left + 115, 48, w - 115, 20).build());

        int arrowY = 98;
        addRenderableWidget(Button.builder(Component.literal("↑"), button -> nudge(0, -0.05F)).bounds(center - 15, arrowY, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("←"), button -> nudge(-0.05F, 0)).bounds(center - 50, arrowY + 24, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("→"), button -> nudge(0.05F, 0)).bounds(center + 20, arrowY + 24, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↓"), button -> nudge(0, 0.05F)).bounds(center - 15, arrowY + 48, 30, 20).build());

        resetButtonY = Math.max(154, Math.min(218, height - 44));
        backButtonY = resetButtonY;
        addRenderableWidget(Button.builder(Component.literal("Reset Bottom Right"), button -> apply(controller.moveHud(0.98F, 0.95F)))
                .tooltip(Tooltip.create(Component.literal("Restores the default HUD location.")))
                .bounds(center - 130, resetButtonY, 160, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> minecraft.setScreen(parent))
                .bounds(center + 35, backButtonY, 95, 20).build());
    }

    private void nudge(float dx, float dy) {
        apply(controller.moveHud(controller.config().hudAnchorX + dx, controller.config().hudAnchorY + dy));
    }

    private void apply(TrapGuardController.OperationResult result) {
        status = result.message();
        rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int right = clamp(Math.round(controller.config().hudAnchorX * width), PREVIEW_W + 4, width - 4);
            int bottom = clamp(Math.round(controller.config().hudAnchorY * height), PREVIEW_H + 4, height - 4);
            int left = right - PREVIEW_W;
            int top = bottom - PREVIEW_H;
            if (event.x() >= left && event.x() <= right && event.y() >= top && event.y() <= bottom) {
                draggingPreview = true;
                dragStartX = controller.config().hudAnchorX;
                dragStartY = controller.config().hudAnchorY;
                dragOffsetX = right - event.x();
                dragOffsetY = bottom - event.y();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!draggingPreview) return super.mouseDragged(event, deltaX, deltaY);
        float x = (float) ((event.x() + dragOffsetX) / Math.max(1.0, width));
        float y = (float) ((event.y() + dragOffsetY) / Math.max(1.0, height));
        controller.previewHudPosition(x, y);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!draggingPreview) return super.mouseReleased(event);
        draggingPreview = false;
        status = controller.commitHudPosition(dragStartX, dragStartY).message();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0101010);
        graphics.drawCenteredString(font, "TrapGuard HUD / Alerts", width / 2, 14, 0xFFFFFFFF);
        int center = width / 2;
        graphics.drawCenteredString(font, "Per-trap alert lifetime / cooldown (seconds)", center, 34, 0xFFB8B8B8);
        graphics.drawCenteredString(font, "Drag the HUD preview directly, or use the arrow buttons", center, 80, 0xFFFFFFFF);
        graphics.drawCenteredString(font,
                "X " + Math.round(controller.config().hudAnchorX * 100) + "%   Y " + Math.round(controller.config().hudAnchorY * 100) + "%",
                center, resetButtonY - 38, 0xFFA0D8FF);
        String fadeText = controller.config().alertCooldownSeconds <= 3
                ? "Alerts vanish at the end with no fade when cooldown is 3s or less."
                : "Alerts fade during the final 2.5 seconds.";
        graphics.drawCenteredString(font, fadeText, center, resetButtonY - 26, 0xFFB8B8B8);
        graphics.drawCenteredString(font, "F3 + D clears visible TrapGuard alerts too.", center, resetButtonY - 14, 0xFFB8B8B8);
        controller.alerts().renderPreview(graphics, controller.config());
        if (!status.isBlank()) graphics.drawCenteredString(font, status, center, Math.max(2, resetButtonY - 4), 0xFFFFD166);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void onClose() {
        if (draggingPreview) {
            controller.previewHudPosition(dragStartX, dragStartY);
            draggingPreview = false;
        }
        minecraft.setScreen(parent);
    }
}
