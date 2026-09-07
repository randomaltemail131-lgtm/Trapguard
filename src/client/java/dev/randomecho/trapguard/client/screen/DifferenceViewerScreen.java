package dev.randomecho.trapguard.client.screen;

import dev.randomecho.trapguard.client.TrapGuardController;
import dev.randomecho.trapguard.core.monitor.Difference;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Paginated discrepancy viewer. Clicking an entry highlights that position for eight seconds. */
public final class DifferenceViewerScreen extends Screen {
    private final TrapGuardController controller;
    private final String trapName;
    private final Screen parent;
    private int page;

    public DifferenceViewerScreen(TrapGuardController controller, String trapName, Screen parent) {
        this(controller, trapName, parent, 0);
    }

    private DifferenceViewerScreen(TrapGuardController controller, String trapName, Screen parent, int page) {
        super(Component.literal("Differences - " + trapName));
        this.controller = controller;
        this.trapName = trapName;
        this.parent = parent;
        this.page = Math.max(0, page);
    }

    @Override
    protected void init() {
        int center = width / 2;
        int w = Math.min(430, Math.max(280, width - 32));
        int left = center - w / 2;
        int rows = Math.max(1, Math.min(10, (height - 92) / 24));
        List<Difference> differences = controller.activeDifferencesSorted(trapName);
        int pages = Math.max(1, (differences.size() + rows - 1) / rows);
        if (page >= pages) page = pages - 1;
        int start = page * rows;
        int end = Math.min(differences.size(), start + rows);
        int y = 42;
        for (int i = start; i < end; i++) {
            Difference difference = differences.get(i);
            String summary = controller.differenceSummary(difference);
            String full = difference.blockChanged()
                    ? "Expected: " + difference.expected().blockId() + " | Actual: " + difference.actual().blockId()
                    : "Changed properties: " + difference.changedProperties().stream()
                    .map(property -> property + ": "
                            + difference.expected().properties().getOrDefault(property, "<unset>")
                            + " -> " + difference.actual().properties().getOrDefault(property, "<unset>"))
                    .collect(java.util.stream.Collectors.joining(", "));
            addRenderableWidget(Button.builder(Component.literal(summary), button -> {
                controller.highlightDifference(difference.pos());
                minecraft.setScreen(null);
            }).tooltip(Tooltip.create(Component.literal(full + "\nClick to close the GUI and highlight this block for 8 seconds.")))
                    .bounds(left, y, w, 20).build());
            y += 24;
        }

        int footer = Math.min(height - 26, y + 2);
        addRenderableWidget(Button.builder(Component.literal("<"), button -> minecraft.setScreen(
                new DifferenceViewerScreen(controller, trapName, parent, Math.max(0, page - 1))))
                .bounds(center - 100, footer, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Page " + (page + 1) + "/" + pages), button -> {})
                .bounds(center - 65, footer, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> minecraft.setScreen(
                new DifferenceViewerScreen(controller, trapName, parent, Math.min(pages - 1, page + 1))))
                .bounds(center + 20, footer, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> minecraft.setScreen(parent))
                .bounds(center + 55, footer, 70, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0101010);
        graphics.drawCenteredString(font, "Differences: " + trapName + " (" + controller.activeDifferenceCount(trapName) + ")", width / 2, 14, 0xFFFFFFFF);
        if (controller.activeDifferenceCount(trapName) == 0) graphics.drawCenteredString(font, "No active differences.", width / 2, 43, 0xFF8FE388);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
