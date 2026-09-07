package dev.randomecho.trapguard.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.randomecho.trapguard.client.TrapGuardController;
import dev.randomecho.trapguard.client.screen.TrapGuardScreen;
import dev.randomecho.trapguard.core.model.ComparisonMode;
import dev.randomecho.trapguard.core.model.IntPos;
import dev.randomecho.trapguard.core.model.Region;
import dev.randomecho.trapguard.core.model.TrapDefinition;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Optional;

public final class TrapGuardCommands {
    private TrapGuardCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, TrapGuardController controller) {
        dispatcher.register(ClientCommandManager.literal("trapguard")
                .executes(ctx -> help(ctx.getSource()))
                .then(ClientCommandManager.literal("help").executes(ctx -> help(ctx.getSource())))
                .then(ClientCommandManager.literal("gui").executes(ctx -> openGui(controller)))
                .then(ClientCommandManager.literal("global")
                        .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> result(ctx.getSource(), controller.setGlobalMonitoringEnabled(
                                        BoolArgumentType.getBool(ctx, "enabled")
                                )))))
                .then(ClientCommandManager.literal("cooldown")
                        .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1, 300))
                                .executes(ctx -> result(ctx.getSource(), controller.setAlertCooldownSeconds(
                                        IntegerArgumentType.getInteger(ctx, "seconds")
                                )))))
                .then(ClientCommandManager.literal("group")
                        .then(ClientCommandManager.literal("create")
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> result(ctx.getSource(), controller.createGroup(
                                                StringArgumentType.getString(ctx, "name")
                                        )))))
                        .then(ClientCommandManager.literal("delete")
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> result(ctx.getSource(), controller.deleteGroup(
                                                StringArgumentType.getString(ctx, "name")
                                        )))))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("trap", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("folder", StringArgumentType.greedyString())
                                                .executes(ctx -> result(ctx.getSource(), controller.setGroup(
                                                        StringArgumentType.getString(ctx, "trap"),
                                                        StringArgumentType.getString(ctx, "folder")
                                                )))))))
                .then(ClientCommandManager.literal("selector")
                        .then(ClientCommandManager.literal("held")
                                .executes(ctx -> result(ctx.getSource(), controller.setSelectorFromHeld(Minecraft.getInstance()))))
                        .then(ClientCommandManager.literal("stick")
                                .executes(ctx -> result(ctx.getSource(), controller.resetSelectorItem())))
                        .then(ClientCommandManager.literal("enabled")
                                .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> result(ctx.getSource(), controller.setSelectorEnabled(
                                                BoolArgumentType.getBool(ctx, "enabled")
                                        ))))))
                .then(ClientCommandManager.literal("pos1").executes(ctx -> setCorner(ctx.getSource(), controller, true)))
                .then(ClientCommandManager.literal("pos2").executes(ctx -> setCorner(ctx.getSource(), controller, false)))
                .then(ClientCommandManager.literal("add").executes(ctx -> toggleExtra(ctx.getSource(), controller)))
                .then(ClientCommandManager.literal("remove").executes(ctx -> removeExtra(ctx.getSource(), controller)))
                .then(ClientCommandManager.literal("clear").executes(ctx -> clearSelection(ctx.getSource(), controller)))
                .then(ClientCommandManager.literal("selection").executes(ctx -> selectionStatus(ctx.getSource(), controller)))
                .then(ClientCommandManager.literal("save")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .executes(ctx -> result(ctx.getSource(), controller.saveSelection(
                                        Minecraft.getInstance(), StringArgumentType.getString(ctx, "name")
                                )))))
                .then(ClientCommandManager.literal("resnapshot")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .executes(ctx -> result(ctx.getSource(), controller.resnapshot(
                                        Minecraft.getInstance(), StringArgumentType.getString(ctx, "name")
                                )))))
                .then(ClientCommandManager.literal("select")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .executes(ctx -> result(ctx.getSource(), controller.loadSelection(
                                        Minecraft.getInstance(), StringArgumentType.getString(ctx, "name")
                                )))))
                .then(ClientCommandManager.literal("apply")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .executes(ctx -> result(ctx.getSource(), controller.applySelection(
                                        Minecraft.getInstance(), StringArgumentType.getString(ctx, "name")
                                )))))
                .then(ClientCommandManager.literal("mode")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .then(ClientCommandManager.literal("structural")
                                        .executes(ctx -> result(ctx.getSource(), controller.setMode(
                                                StringArgumentType.getString(ctx, "name"), ComparisonMode.STRUCTURAL
                                        ))))
                                .then(ClientCommandManager.literal("exact")
                                        .executes(ctx -> result(ctx.getSource(), controller.setMode(
                                                StringArgumentType.getString(ctx, "name"), ComparisonMode.EXACT
                                        ))))))
                .then(ClientCommandManager.literal("enable")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> result(ctx.getSource(), controller.setEnabled(
                                                StringArgumentType.getString(ctx, "name"),
                                                BoolArgumentType.getBool(ctx, "enabled")
                                        ))))))
                .then(ClientCommandManager.literal("overlay")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> result(ctx.getSource(), controller.setOverlay(
                                                StringArgumentType.getString(ctx, "name"),
                                                BoolArgumentType.getBool(ctx, "enabled")
                                        ))))))
                .then(ClientCommandManager.literal("delete")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .executes(ctx -> result(ctx.getSource(), controller.delete(
                                        StringArgumentType.getString(ctx, "name")
                                )))))
                .then(ClientCommandManager.literal("list").executes(ctx -> list(ctx.getSource(), controller)))
                .then(ClientCommandManager.literal("status")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .executes(ctx -> status(ctx.getSource(), controller, StringArgumentType.getString(ctx, "name")))))
        );
    }

    private static int openGui(TrapGuardController controller) {
        Minecraft.getInstance().setScreen(new TrapGuardScreen(controller));
        return 1;
    }

    private static int help(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("[TrapGuard] /trapguard gui | global <true|false> | cooldown <1-300>"));
        source.sendFeedback(Component.literal("[TrapGuard] /trapguard group create|delete <name> | group set <trap> <folder>"));
        source.sendFeedback(Component.literal("[TrapGuard] /trapguard selector held|stick|enabled <true|false>"));
        source.sendFeedback(Component.literal("[TrapGuard] /trapguard pos1 | pos2 | add | remove | clear | selection"));
        source.sendFeedback(Component.literal("[TrapGuard] /trapguard save <name> | select <name> | apply <name> | resnapshot <name>"));
        source.sendFeedback(Component.literal("[TrapGuard] /trapguard mode <name> structural|exact | enable <name> <true|false>"));
        source.sendFeedback(Component.literal("[TrapGuard] /trapguard overlay <name> <true|false> | status <name> | list | delete <name>"));
        return 1;
    }

    private static int setCorner(FabricClientCommandSource source, TrapGuardController controller, boolean first) {
        Optional<IntPos> target = controller.targetedBlock(Minecraft.getInstance());
        if (target.isEmpty()) return error(source, "Look directly at a block first.");
        if (first) controller.selection().setPos1(target.get());
        else controller.selection().setPos2(target.get());
        source.sendFeedback(Component.literal("[TrapGuard] pos" + (first ? "1" : "2") + " = " + target.get().compact()));
        return 1;
    }

    private static int toggleExtra(FabricClientCommandSource source, TrapGuardController controller) {
        Optional<IntPos> target = controller.targetedBlock(Minecraft.getInstance());
        if (target.isEmpty()) return error(source, "Look directly at a block first.");
        boolean added = controller.selection().toggleExtra(target.get());
        source.sendFeedback(Component.literal("[TrapGuard] " + (added ? "Added" : "Removed") + " extra watched block " + target.get().compact() + "."));
        return 1;
    }

    private static int removeExtra(FabricClientCommandSource source, TrapGuardController controller) {
        Optional<IntPos> target = controller.targetedBlock(Minecraft.getInstance());
        if (target.isEmpty()) return error(source, "Look directly at a block first.");
        boolean removed = controller.selection().removeExtra(target.get());
        if (!removed) return error(source, "That block is not in the extra-block selection.");
        source.sendFeedback(Component.literal("[TrapGuard] Removed extra watched block " + target.get().compact() + "."));
        return 1;
    }

    private static int clearSelection(FabricClientCommandSource source, TrapGuardController controller) {
        controller.selection().clear();
        source.sendFeedback(Component.literal("[TrapGuard] Editor selection cleared."));
        return 1;
    }

    private static int selectionStatus(FabricClientCommandSource source, TrapGuardController controller) {
        Optional<Region> region = controller.selection().mainRegion();
        String regionText = region.map(r -> r.min().compact() + " -> " + r.max().compact() + " (" + r.blockCount() + " blocks)")
                .orElse("not complete");
        source.sendFeedback(Component.literal("[TrapGuard] Main selection: " + regionText + "; extras: " + controller.selection().extraBlocks().size() + "."));
        return 1;
    }

    private static int list(FabricClientCommandSource source, TrapGuardController controller) {
        if (controller.database().traps.isEmpty()) {
            source.sendFeedback(Component.literal("[TrapGuard] No saved traps."));
            return 1;
        }
        source.sendFeedback(Component.literal("[TrapGuard] Saved traps:"));
        for (TrapDefinition trap : controller.trapsSorted()) {
            source.sendFeedback(Component.literal("  " + trap.name + " — " + trap.mode + ", " + trap.snapshot.size() + " blocks, " +
                    (trap.enabled ? "enabled" : "disabled") + ", changed=" + controller.activeDifferenceCount(trap.name)));
        }
        return 1;
    }

    private static int status(FabricClientCommandSource source, TrapGuardController controller, String name) {
        TrapDefinition trap = controller.findTrap(name).orElse(null);
        if (trap == null) return error(source, "Unknown trap: " + name);
        source.sendFeedback(Component.literal("[TrapGuard] " + trap.name + ": scope=" + trap.serverScope + ", dimension=" + trap.dimension +
                ", mode=" + trap.mode + ", group=" + trap.group + ", enabled=" + trap.enabled + ", overlay=" + trap.overlay +
                ", regions=" + trap.regions.size() + ", extras=" + trap.extraBlocks.size() +
                ", watched=" + trap.snapshot.size() + ", changed=" + controller.activeDifferenceCount(trap.name) + "."));
        return 1;
    }

    private static int result(FabricClientCommandSource source, TrapGuardController.OperationResult result) {
        if (!result.ok()) return error(source, result.message());
        source.sendFeedback(Component.literal("[TrapGuard] " + result.message()));
        return 1;
    }

    private static int error(FabricClientCommandSource source, String message) {
        source.sendError(Component.literal("[TrapGuard] " + message));
        return 0;
    }
}
