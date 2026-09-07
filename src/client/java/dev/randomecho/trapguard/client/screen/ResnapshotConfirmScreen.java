package dev.randomecho.trapguard.client.screen;

import dev.randomecho.trapguard.client.TrapGuardController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Prevents accidentally accepting a griefed structure as the new baseline. */
public final class ResnapshotConfirmScreen extends Screen {
    private final TrapGuardController controller;
    private final String trapName;
    private final Screen parent;
    private String status = "";

    public ResnapshotConfirmScreen(TrapGuardController controller, String trapName, Screen parent) {
        super(Component.literal("Confirm Resnapshot"));
        this.controller = controller;
        this.trapName = trapName;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int y = height / 2 + 24;
        addRenderableWidget(Button.builder(Component.literal("Resnapshot"), button -> {
            TrapGuardController.OperationResult result = controller.resnapshot(minecraft, trapName);
            if (result.ok()) {
                Screen backParent = parent instanceof TrapDetailsScreen details ? details.parentScreen() : parent;
                minecraft.setScreen(new TrapDetailsScreen(controller, trapName, backParent, result.message()));
            }
            else status = result.message();
        }).bounds(center - 105, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> minecraft.setScreen(parent))
                .bounds(center + 5, y, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0101010);
        int center = width / 2;
        int y = height / 2 - 38;
        graphics.drawCenteredString(font, "Replace the saved snapshot for '" + trapName + "'?", center, y, 0xFFFFFFFF);
        graphics.drawCenteredString(font, "Current known differences: " + controller.activeDifferenceCount(trapName), center, y + 16,
                controller.activeDifferenceCount(trapName) == 0 ? 0xFF8FE388 : 0xFFFF6B6B);
        graphics.drawCenteredString(font, "This accepts the structure exactly as it exists now.", center, y + 32, 0xFFFFD166);
        if (!status.isBlank()) graphics.drawCenteredString(font, status, center, y + 48, 0xFFFF6B6B);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
