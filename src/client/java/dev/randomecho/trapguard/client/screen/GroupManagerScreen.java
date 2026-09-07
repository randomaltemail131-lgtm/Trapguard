package dev.randomecho.trapguard.client.screen;

import dev.randomecho.trapguard.client.TrapGuardController;
import dev.randomecho.trapguard.core.model.TrapDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Create/rename/delete custom TrapGuard folders. */
public final class GroupManagerScreen extends Screen {
    private final TrapGuardController controller;
    private final Screen parent;
    private EditBox nameBox;
    private String selected;
    private String status = "";
    private boolean deleteArmed;
    private int page;

    public GroupManagerScreen(TrapGuardController controller, Screen parent) {
        super(Component.literal("TrapGuard Folders"));
        this.controller = controller;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int w = Math.min(360, Math.max(260, width - 32));
        int left = center - w / 2;

        nameBox = new EditBox(font, left, 40, w - 105, 20, Component.literal("Folder name"));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.literal("Folder name"));
        if (selected != null) nameBox.setValue(selected);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("Create"), button -> {
            TrapGuardController.OperationResult result = controller.createGroup(nameBox.getValue());
            if (result.ok()) selected = null;
            apply(result);
        }).tooltip(Tooltip.create(Component.literal("Create a new custom folder.")))
                .bounds(left + w - 100, 40, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Rename Selected"), button -> {
            if (selected == null) { status = "Select a custom folder first."; return; }
            TrapGuardController.OperationResult result = controller.renameGroup(selected, nameBox.getValue());
            if (result.ok()) selected = nameBox.getValue().trim();
            apply(result);
        }).tooltip(Tooltip.create(Component.literal("Rename the selected folder and update every trap inside it.")))
                .bounds(left, 64, (w - 5) / 2, 20).build());

        addRenderableWidget(Button.builder(Component.literal(deleteArmed ? "CONFIRM DELETE" : "Delete Selected"), button -> {
            if (selected == null) { status = "Select a custom folder first."; return; }
            if (!deleteArmed) { deleteArmed = true; rebuildWidgets(); return; }
            TrapGuardController.OperationResult result = controller.deleteGroup(selected);
            if (result.ok()) selected = null;
            apply(result);
        }).tooltip(Tooltip.create(Component.literal("Deletes only the folder. Its traps are moved to Ungrouped. A second click confirms.")))
                .bounds(left + (w - 5) / 2 + 5, 64, (w - 5) / 2, 20).build());

        List<String> groups = controller.groupsSorted().stream()
                .filter(group -> !TrapDefinition.UNGROUPED.equalsIgnoreCase(group)).toList();
        int rows = Math.max(1, Math.min(7, (height - 138) / 24));
        int pages = Math.max(1, (groups.size() + rows - 1) / rows);
        if (page >= pages) page = pages - 1;
        int start = page * rows;
        int end = Math.min(groups.size(), start + rows);
        int y = 100;
        for (int i = start; i < end; i++) {
            String group = groups.get(i);
            addRenderableWidget(Button.builder(Component.literal(group + "  (" + controller.trapCountInGroup(group) + " traps)"), button -> {
                selected = group;
                deleteArmed = false;
                rebuildWidgets();
            }).tooltip(Tooltip.create(Component.literal("Select this folder to rename or delete it.")))
                    .bounds(left, y, w, 20).build());
            y += 24;
        }

        int footer = Math.min(height - 26, y + 2);
        addRenderableWidget(Button.builder(Component.literal("<"), button -> { page = Math.max(0, page - 1); rebuildWidgets(); })
                .bounds(center - 100, footer, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Page " + (page + 1) + "/" + pages), button -> {})
                .bounds(center - 65, footer, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> { page = Math.min(pages - 1, page + 1); rebuildWidgets(); })
                .bounds(center + 20, footer, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> minecraft.setScreen(parent))
                .bounds(center + 55, footer, 70, 20).build());
    }

    private void apply(TrapGuardController.OperationResult result) {
        status = result.message();
        deleteArmed = false;
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0101010);
        graphics.drawCenteredString(font, "TrapGuard Folders", width / 2, 14, 0xFFFFFFFF);
        if (controller.groupsSorted().size() == 1) graphics.drawCenteredString(font, "No custom folders yet.", width / 2, 101, 0xFFB8B8B8);
        if (selected != null) graphics.drawCenteredString(font, "Selected: " + selected, width / 2, 88, 0xFFA0D8FF);
        if (!status.isBlank()) graphics.drawCenteredString(font, status, width / 2, Math.max(2, height - 46), 0xFFFFD166);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
