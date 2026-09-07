package dev.randomecho.trapguard.client.screen;

import dev.randomecho.trapguard.client.TrapGuardController;
import dev.randomecho.trapguard.core.model.ComparisonMode;
import dev.randomecho.trapguard.core.model.Region;
import dev.randomecho.trapguard.core.model.TrapDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Optional;

/** Main player-facing TrapGuard screen. Commands remain available as a fallback. */
public final class TrapGuardScreen extends Screen {
    private final TrapGuardController controller;
    private int page;
    private String status;
    private String groupFilter;
    private EditBox nameBox;
    private int rowsPerPage = 3;
    private int folderDropX;
    private int folderDropY;
    private int folderDropW;
    private List<String> folderDropValues = List.of();
    private int folderDropOffset;
    private boolean folderDropOpen;

    public TrapGuardScreen(TrapGuardController controller) {
        this(controller, 0, "", "*");
    }

    TrapGuardScreen(TrapGuardController controller, int page, String status, String groupFilter) {
        super(Component.literal("TrapGuard"));
        this.controller = controller;
        this.page = Math.max(0, page);
        this.status = status == null ? "" : status;
        this.groupFilter = groupFilter == null ? "*" : groupFilter;
    }

    @Override
    protected void init() {
        int center = width / 2;
        if (!"*".equals(groupFilter) && controller.groupsSorted().stream().noneMatch(group -> group.equalsIgnoreCase(groupFilter))) {
            groupFilter = "*";
            page = 0;
        }
        int panelWidth = Math.min(420, Math.max(300, width - 24));
        int left = center - panelWidth / 2;

        int third = (panelWidth - 10) / 3;
        addRenderableWidget(Button.builder(
                Component.literal("Use Held Item"),
                button -> applyAndRefresh(controller.setSelectorFromHeld(minecraft))
        ).tooltip(Tooltip.create(Component.literal("Makes the item currently in your main hand the TrapGuard selector.")))
                .bounds(left, 40, third, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal(controller.config().selectorEnabled ? "Selector: ON" : "Selector: OFF"),
                button -> applyAndRefresh(controller.setSelectorEnabled(!controller.config().selectorEnabled))
        ).tooltip(Tooltip.create(Component.literal("Turns selector clicks on or off without changing which item is configured.")))
                .bounds(left + third + 5, 40, third, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Reset to Stick"),
                button -> applyAndRefresh(controller.resetSelectorItem())
        ).tooltip(Tooltip.create(Component.literal("Changes the selector item back to minecraft:stick.")))
                .bounds(left + (third + 5) * 2, 40, panelWidth - (third + 5) * 2, 20).build());

        int globalW = Math.max(125, panelWidth / 3);
        addRenderableWidget(Button.builder(
                Component.literal(controller.config().globalMonitoringEnabled ? "All Monitoring: ON" : "All Monitoring: PAUSED"),
                button -> applyAndRefresh(controller.setGlobalMonitoringEnabled(!controller.config().globalMonitoringEnabled))
        ).tooltip(Tooltip.create(Component.literal("Master pause/resume for every trap. Individual trap ON/OFF settings are preserved.")))
                .bounds(left, 64, globalW, 20).build());

        folderDropX = left + globalW + 5;
        folderDropY = 64;
        folderDropW = panelWidth - globalW - 120;
        folderDropValues = new java.util.ArrayList<>();
        folderDropValues.add("*");
        folderDropValues.addAll(controller.groupsSorted());
        folderDropOffset = Math.max(0, Math.min(folderDropOffset, Math.max(0, folderDropValues.size() - folderDropVisibleCount())));
        addRenderableWidget(Button.builder(
                Component.literal("Folder: " + displayFilter() + " ▼"),
                button -> {}
        ).tooltip(Tooltip.create(Component.literal("Hover to open the folder filter, then click a folder. The list closes when the pointer leaves it.")))
                .bounds(folderDropX, folderDropY, folderDropW, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Folders"),
                button -> minecraft.setScreen(new GroupManagerScreen(controller, this))
        ).tooltip(Tooltip.create(Component.literal("Create, rename, or delete custom folders. Deleting a folder moves its traps to Ungrouped.")))
                .bounds(left + panelWidth - 110, 64, 110, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("HUD / Alerts"),
                button -> minecraft.setScreen(new HudSettingsScreen(controller, this))
        ).tooltip(Tooltip.create(Component.literal("Change the per-trap alert cooldown and move the TrapGuard HUD alert stack.")))
                .bounds(left, 88, 120, 20).build());

        nameBox = new EditBox(font, left, 158, panelWidth - 105, 20, Component.literal("Trap name"));
        nameBox.setMaxLength(48);
        String editingTrap = controller.selection().editingTrapName().orElse(null);
        if (editingTrap != null) {
            nameBox.setValue(editingTrap);
            nameBox.setEditable(false);
        } else {
            nameBox.setHint(Component.literal("Trap name"));
        }
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(
                Component.literal(editingTrap == null ? "Save Trap" : "Apply Changes"),
                button -> {
                    TrapGuardController.OperationResult result = editingTrap == null
                            ? controller.saveSelection(minecraft, nameBox.getValue())
                            : controller.applySelection(minecraft, editingTrap);
                    if (result.ok()) minecraft.setScreen(new TrapGuardScreen(controller, page, result.message(), groupFilter));
                    else status = result.message();
                }
        ).tooltip(Tooltip.create(Component.literal(editingTrap == null
                ? "Snapshots the selected area and extra blocks using the mode shown below. New traps start in Ungrouped."
                : "Commits the edited area, extra blocks, current block snapshot, and mode back to this saved trap.")))
                .bounds(left + panelWidth - 100, 158, 100, 20).build());

        int actionW = (panelWidth - 80) / 2;
        addRenderableWidget(Button.builder(
                Component.literal("Mode: " + controller.selection().draftMode()),
                button -> {
                    ComparisonMode mode = controller.selection().toggleDraftMode();
                    status = "Editor mode set to " + mode + ".";
                    button.setMessage(Component.literal("Mode: " + mode));
                }
        ).tooltip(Tooltip.create(Component.literal("STRUCTURAL ignores temporary redstone states. EXACT compares every block-state property.")))
                .bounds(left, 182, actionW, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Clear Selection"),
                button -> {
                    controller.selection().clear();
                    status = "Editor selection cleared.";
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("Clears the current editor corners and extra blocks. Saved traps are untouched.")))
                .bounds(left + actionW + 5, 182, actionW, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + panelWidth - 70, 182, 70, 20).build());

        rowsPerPage = Math.max(1, Math.min(7, (height - 244) / 22));
        List<TrapDefinition> traps = controller.trapsSorted(groupFilter);
        int pageCount = Math.max(1, (traps.size() + rowsPerPage - 1) / rowsPerPage);
        if (page >= pageCount) page = pageCount - 1;

        int start = page * rowsPerPage;
        int end = Math.min(traps.size(), start + rowsPerPage);
        int rowY = 224;
        for (int i = start; i < end; i++) {
            TrapDefinition trap = traps.get(i);
            TrapGuardController.TrapStatus trapStatus = controller.trapStatus(minecraft, trap);
            Component label = statusPrefix(trapStatus)
                    .append(Component.literal("  " + trap.name + "  |  " + trap.mode + "  |  " + trap.group));
            addRenderableWidget(Button.builder(
                    label,
                    button -> minecraft.setScreen(new TrapDetailsScreen(
                            controller, trap.name, new TrapGuardScreen(controller, page, status, groupFilter)))
            ).tooltip(Tooltip.create(Component.literal(
                    trapStatus.label() + " — click to manage this trap, view differences, change its folder, resnapshot, or load it into the editor."
            ))).bounds(left, rowY, panelWidth, 20).build());
            rowY += 22;
        }

        int footerY = Math.min(height - 24, rowY + 2);
        addRenderableWidget(Button.builder(Component.literal("<"),
                button -> minecraft.setScreen(new TrapGuardScreen(controller, Math.max(0, page - 1), status, groupFilter)))
                .bounds(center - 70, footerY, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Page " + (page + 1) + "/" + pageCount), button -> {})
                .bounds(center - 35, footerY, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),
                button -> minecraft.setScreen(new TrapGuardScreen(controller, Math.min(pageCount - 1, page + 1), status, groupFilter)))
                .bounds(center + 40, footerY, 30, 20).build());
    }

    private MutableComponent statusPrefix(TrapGuardController.TrapStatus status) {
        ChatFormatting color = switch (status) {
            case SAFE -> ChatFormatting.GREEN;
            case CHANGED -> ChatFormatting.RED;
            case PARTIAL, SCANNING -> ChatFormatting.YELLOW;
            case PAUSED -> ChatFormatting.GOLD;
            case UNLOADED, DISABLED, OTHER_WORLD, EMPTY -> ChatFormatting.GRAY;
        };
        String symbol = switch (status) {
            case SAFE -> "✓ SAFE";
            case CHANGED -> "! CHANGED";
            case PARTIAL -> "◐ PARTIAL";
            case SCANNING -> "… SCANNING";
            case PAUSED -> "Ⅱ PAUSED";
            case UNLOADED -> "○ UNLOADED";
            case DISABLED -> "× DISABLED";
            case OTHER_WORLD -> "◇ OTHER WORLD";
            case EMPTY -> "? EMPTY";
        };
        return Component.literal(symbol).withStyle(color);
    }

    private String displayFilter() { return "*".equals(groupFilter) ? "All" : groupFilter; }

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
            String value = folderDropValues.get(folderDropOffset + i);
            int y = folderDropY + 20 + i * 20;
            boolean hovered = mouseX >= folderDropX && mouseX <= folderDropX + folderDropW
                    && mouseY >= y && mouseY <= y + 20;
            graphics.fill(folderDropX, y, folderDropX + folderDropW, y + 20, hovered ? 0xF05A5A5A : 0xF0252525);
            String label = "*".equals(value) ? "All" : value;
            graphics.drawString(font, label, folderDropX + 6, y + 6, 0xFFFFFFFF, false);
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
                String value = folderDropValues.get(folderDropOffset + index);
                minecraft.setScreen(new TrapGuardScreen(controller, 0, status, value));
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

    private void applyAndRefresh(TrapGuardController.OperationResult result) {
        minecraft.setScreen(new TrapGuardScreen(controller, page, result.message(), groupFilter));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateFolderDropdownState(mouseX, mouseY);
        graphics.fill(0, 0, width, height, 0xB0101010);
        int center = width / 2;
        int panelWidth = Math.min(420, Math.max(300, width - 24));
        int left = center - panelWidth / 2;

        graphics.drawCenteredString(font, title, center, 12, 0xFFFFFFFF);
        graphics.drawString(font, "Selector item: " + controller.selectorItemId(), left, 27, 0xFFD0D0D0);
        graphics.drawString(font, "Alert cooldown: " + controller.config().alertCooldownSeconds + "s per trap", left + 126, 94, 0xFFA0D8FF);
        graphics.drawString(font, "Left click = corner 1    Right click = corner 2", left, 114, 0xFFB8B8B8);
        graphics.drawString(font, "Sneak + click/drag = add or remove extra watched blocks", left, 126, 0xFFB8B8B8);

        String p1 = controller.selection().pos1() == null ? "unset" : controller.selection().pos1().compact();
        String p2 = controller.selection().pos2() == null ? "unset" : controller.selection().pos2().compact();
        Optional<Region> region = controller.selection().mainRegion();
        String count = region.map(r -> Long.toString(r.blockCount())).orElse("0");
        graphics.drawString(font, "Selection: " + p1 + " -> " + p2, left, 139, 0xFFFFFFFF);
        graphics.drawString(font, "Main blocks: " + count + "    Extras: " + controller.selection().extraBlocks().size(), left, 148, 0xFFA0D8FF);
        graphics.drawString(font, controller.selection().editingTrapName().isPresent()
                ? "Editing saved trap (Apply Changes to commit)"
                : "Saved traps — status shown at the left of each row", left, 214, 0xFFFFFFFF);

        if (!status.isBlank()) graphics.drawCenteredString(font, status, center, Math.max(2, height - 36), 0xFFFFD166);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderFolderDropdown(graphics, mouseX, mouseY);
    }

    @Override
    public void onClose() { minecraft.setScreen(null); }
}
