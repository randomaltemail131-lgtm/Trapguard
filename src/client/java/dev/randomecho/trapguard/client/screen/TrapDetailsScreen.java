package dev.randomecho.trapguard.client.screen;

import dev.randomecho.trapguard.client.TrapGuardController;
import dev.randomecho.trapguard.core.model.ComparisonMode;
import dev.randomecho.trapguard.core.model.TrapDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Management screen for one saved trap. */
public final class TrapDetailsScreen extends Screen {
    private final TrapGuardController controller;
    private final String trapName;
    private final Screen parent;
    private String status;
    private boolean deleteArmed;
    private int folderDropX;
    private int folderDropY;
    private int folderDropW;
    private List<String> folderDropValues = List.of();
    private int folderDropOffset;
    private boolean folderDropOpen;

    public TrapDetailsScreen(TrapGuardController controller, String trapName, Screen parent) {
        this(controller, trapName, parent, "");
    }

    public TrapDetailsScreen(TrapGuardController controller, String trapName, Screen parent, String status) {
        super(Component.literal("TrapGuard - " + trapName));
        this.controller = controller;
        this.trapName = trapName;
        this.parent = parent;
        this.status = status == null ? "" : status;
    }

    @Override
    protected void init() {
        TrapDefinition trap = controller.findTrap(trapName).orElse(null);
        if (trap == null) {
            minecraft.setScreen(parent);
            return;
        }

        int center = width / 2;
        int w = Math.min(340, Math.max(260, width - 32));
        int left = center - w / 2;
        int half = (w - 6) / 2;
        int y = 76;

        addRenderableWidget(Button.builder(
                Component.literal("Mode: " + trap.mode),
                button -> {
                    ComparisonMode next = trap.mode == ComparisonMode.STRUCTURAL ? ComparisonMode.EXACT : ComparisonMode.STRUCTURAL;
                    apply(controller.setMode(trap.name, next));
                }
        ).tooltip(Tooltip.create(Component.literal("STRUCTURAL ignores temporary redstone states. EXACT compares every property.")))
                .bounds(left, y, half, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Monitoring: " + (trap.enabled ? "ON" : "OFF")),
                button -> apply(controller.setEnabled(trap.name, !trap.enabled))
        ).tooltip(Tooltip.create(Component.literal("Turns live integrity checking for this trap on or off. The global pause remains separate.")))
                .bounds(left + half + 6, y, half, 20).build());
        y += 24;

        addRenderableWidget(Button.builder(
                Component.literal("Overlay: " + (trap.overlay ? "ON" : "OFF")),
                button -> apply(controller.setOverlay(trap.name, !trap.overlay))
        ).tooltip(Tooltip.create(Component.literal("Shows or hides this trap's saved-area outline.")))
                .bounds(left, y, half, 20).build());

        folderDropX = left + half + 6;
        folderDropY = y;
        folderDropW = half;
        folderDropValues = controller.groupsSorted();
        folderDropOffset = Math.max(0, Math.min(folderDropOffset, Math.max(0, folderDropValues.size() - folderDropVisibleCount())));
        addRenderableWidget(Button.builder(
                Component.literal("Folder: " + trap.group + " ▼"),
                button -> {}
        ).tooltip(Tooltip.create(Component.literal("Hover to choose Ungrouped or one of your custom folders.")))
                .bounds(folderDropX, folderDropY, folderDropW, 20).build());
        y += 24;

        addRenderableWidget(Button.builder(
                Component.literal("View Differences (" + controller.activeDifferenceCount(trap.name) + ")"),
                button -> minecraft.setScreen(new DifferenceViewerScreen(controller, trap.name, this))
        ).tooltip(Tooltip.create(Component.literal("Shows every discrepancy TrapGuard currently knows about. Click a row to highlight that block in-world.")))
                .bounds(left, y, half, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Load Into Editor"),
                button -> {
                    TrapGuardController.OperationResult result = controller.loadSelection(minecraft, trap.name);
                    if (result.ok()) minecraft.setScreen(new TrapGuardScreen(controller));
                    else {
                        status = result.message();
                        deleteArmed = false;
                        rebuildWidgets();
                    }
                }
        ).tooltip(Tooltip.create(Component.literal("Copies this saved trap into the main editor. Nothing changes until Apply Changes is pressed.")))
                .bounds(left + half + 6, y, half, 20).build());
        y += 24;

        addRenderableWidget(Button.builder(
                Component.literal("Convert to .Schem"),
                button -> apply(controller.exportSchem(trap.name))
        ).tooltip(Tooltip.create(Component.literal("Exports the saved block-state snapshot to the instance schematics folder as '" + trap.name + ".schem'. Existing same-name files are replaced. Block-entity NBT is not available in TrapGuard snapshots.")))
                .bounds(left, y, w, 20).build());
        y += 24;

        addRenderableWidget(Button.builder(
                Component.literal("Resnapshot Current Trap"),
                button -> minecraft.setScreen(new ResnapshotConfirmScreen(controller, trap.name, this))
        ).tooltip(Tooltip.create(Component.literal("Opens a confirmation screen before replacing the saved expected state with the physical structure.")))
                .bounds(left, y, w, 20).build());
        y += 24;

        addRenderableWidget(Button.builder(
                Component.literal(deleteArmed ? "CONFIRM DELETE" : "Delete Trap"),
                button -> {
                    if (!deleteArmed) {
                        deleteArmed = true;
                        rebuildWidgets();
                        return;
                    }
                    TrapGuardController.OperationResult result = controller.delete(trap.name);
                    if (result.ok()) minecraft.setScreen(parent);
                    else {
                        status = result.message();
                        deleteArmed = false;
                        rebuildWidgets();
                    }
                }
        ).tooltip(Tooltip.create(Component.literal("Deletes this trap. A second click is required.")))
                .bounds(left, y, w, 20).build());
        y += 28;

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> minecraft.setScreen(parent))
                .bounds(center - 50, Math.min(y, height - 24), 100, 20).build());
    }

    Screen parentScreen() {
        return parent;
    }

    private int folderDropVisibleCount() {
        int available = Math.max(1, (height - (folderDropY + 22) - 8) / 20);
        return Math.min(folderDropValues.size(), available);
    }

    private boolean folderDropButtonHovered(double mouseX, double mouseY) {
        return mouseX >= folderDropX && mouseX <= folderDropX + folderDropW
                && mouseY >= folderDropY && mouseY <= folderDropY + 20;
    }

    private boolean folderDropListHovered(double mouseX, double mouseY) {
        int count = folderDropVisibleCount();
        return mouseX >= folderDropX && mouseX <= folderDropX + folderDropW
                && mouseY >= folderDropY + 20 && mouseY <= folderDropY + 20 + count * 20;
    }

    private boolean folderDropHovered(double mouseX, double mouseY) {
        return folderDropOpen && (folderDropButtonHovered(mouseX, mouseY) || folderDropListHovered(mouseX, mouseY));
    }

    private void updateFolderDropdownState(double mouseX, double mouseY) {
        if (!folderDropOpen) {
            if (folderDropButtonHovered(mouseX, mouseY)) folderDropOpen = true;
        } else if (!folderDropButtonHovered(mouseX, mouseY) && !folderDropListHovered(mouseX, mouseY)) {
            folderDropOpen = false;
        }
    }

    private void renderFolderDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!folderDropOpen) return;
        int count = folderDropVisibleCount();
        for (int i = 0; i < count; i++) {
            String group = folderDropValues.get(folderDropOffset + i);
            int y = folderDropY + 20 + i * 20;
            boolean hovered = mouseX >= folderDropX && mouseX <= folderDropX + folderDropW
                    && mouseY >= y && mouseY <= y + 20;
            graphics.fill(folderDropX, y, folderDropX + folderDropW, y + 20, hovered ? 0xF05A5A5A : 0xF0252525);
            graphics.drawString(font, group, folderDropX + 6, y + 6, 0xFFFFFFFF, false);
        }
        if (folderDropOffset > 0) {
            graphics.drawString(font, "↑ more", folderDropX + folderDropW - 42, folderDropY + 26, 0xFFFFD166, false);
        }
        if (folderDropOffset + count < folderDropValues.size()) {
            int y = folderDropY + 20 + count * 20 - 10;
            graphics.drawString(font, "↓ more", folderDropX + folderDropW - 42, y, 0xFFFFD166, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && folderDropHovered(event.x(), event.y())
                && event.y() >= folderDropY + 20) {
            int index = (int) ((event.y() - (folderDropY + 20)) / 20);
            int count = folderDropVisibleCount();
            if (index >= 0 && index < count) {
                TrapGuardController.OperationResult result = controller.setGroup(trapName, folderDropValues.get(folderDropOffset + index));
                status = result.message();
                deleteArmed = false;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (folderDropHovered(mouseX, mouseY) && folderDropValues.size() > folderDropVisibleCount()) {
            int maxOffset = Math.max(0, folderDropValues.size() - folderDropVisibleCount());
            folderDropOffset = Math.max(0, Math.min(maxOffset, folderDropOffset - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void apply(TrapGuardController.OperationResult result) {
        status = result.message();
        deleteArmed = false;
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateFolderDropdownState(mouseX, mouseY);
        graphics.fill(0, 0, width, height, 0xB0101010);
        TrapDefinition trap = controller.findTrap(trapName).orElse(null);
        if (trap != null) {
            int center = width / 2;
            int w = Math.min(340, Math.max(260, width - 32));
            int left = center - w / 2;
            TrapGuardController.TrapStatus runtime = controller.trapStatus(minecraft, trap);
            graphics.drawCenteredString(font, trap.name, center, 14, 0xFFFFFFFF);
            graphics.drawString(font, "Status: " + runtime.label() + "    Folder: " + trap.group, left, 32, 0xFFA0D8FF);
            graphics.drawString(font, "Dimension: " + trap.dimension, left, 44, 0xFFB8B8B8);
            graphics.drawString(font, "Watched: " + trap.snapshot.size() + "   Regions: " + trap.regions.size() +
                    "   Extras: " + trap.extraBlocks.size(), left, 56, 0xFFB8B8B8);
            graphics.drawString(font, "Changed right now: " + controller.activeDifferenceCount(trap.name), left, 68,
                    controller.activeDifferenceCount(trap.name) == 0 ? 0xFF8FE388 : 0xFFFF6B6B);
            if (!status.isBlank()) graphics.drawCenteredString(font, status, center, Math.max(2, height - 28), 0xFFFFD166);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderFolderDropdown(graphics, mouseX, mouseY);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
