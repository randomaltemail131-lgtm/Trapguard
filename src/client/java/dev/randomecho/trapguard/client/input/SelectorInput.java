package dev.randomecho.trapguard.client.input;

import dev.randomecho.trapguard.client.TrapGuardController;
import dev.randomecho.trapguard.client.compat.MinecraftBlockAdapter;
import dev.randomecho.trapguard.core.model.IntPos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.HashSet;
import java.util.Set;

/**
 * 1.21.11-specific mouse integration for the configurable selector item.
 * This lives outside the portable core because interaction callbacks are version-sensitive.
 */
public final class SelectorInput {
    private static final Set<IntPos> LEFT_DRAG_VISITED = new HashSet<>();
    private static final Set<IntPos> RIGHT_DRAG_VISITED = new HashSet<>();
    private static Boolean leftDragAdding;
    private static Boolean rightDragAdding;

    private SelectorInput() {}

    public static void register(TrapGuardController controller) {
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (client.screen != null || !controller.isSelectorStack(player.getMainHandItem())) return false;

            var target = MinecraftBlockAdapter.targetedBlock(client);

            // Sneak-left drag is handled once per client tick below so holding the button can
            // paint across many blocks. The pre-attack callback still consumes the attack so
            // no block/entity attack packet is sent.
            if (player.isShiftKeyDown()) return true;

            if (target.isPresent() && clickCount != 0) {
                controller.selection().setPos1(target.get());
                feedback(client, "Corner 1 = " + target.get().compact());
            }
            return true;
        });

        // Fabric's pre-attack callback can fire while held, but polling the attack key here keeps the
        // drag state isolated from clickCount and lets us track visited blocks while held so sneak-left-click behaves like a paint/drag tool. The first touched block decides
        // whether the whole drag adds or removes; revisiting a block during the same drag is ignored.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.screen != null || client.player == null ||
                    !controller.isSelectorStack(client.player.getMainHandItem()) ||
                    !client.player.isShiftKeyDown() || !client.options.keyAttack.isDown()) {
                resetLeftDrag();
                return;
            }

            var target = MinecraftBlockAdapter.targetedBlock(client);
            if (target.isEmpty()) return;
            IntPos pos = target.get();
            if (!LEFT_DRAG_VISITED.add(pos)) return;

            if (leftDragAdding == null) {
                leftDragAdding = !controller.selection().containsExtra(pos);
            }

            boolean changed = leftDragAdding
                    ? controller.selection().addExtra(pos)
                    : controller.selection().removeExtra(pos);
            if (changed) {
                feedback(client, (leftDragAdding ? "Added" : "Removed") + " extra block " + pos.compact());
            }
        });

        // Right-click block callbacks can repeat while the use key is held. Keep a drag session so
        // the same block cannot oscillate between added/removed and every crossed block gets the
        // same operation chosen by the first block in the drag.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.screen != null || client.player == null ||
                    !controller.isSelectorStack(client.player.getMainHandItem()) ||
                    !client.player.isShiftKeyDown() || !client.options.keyUse.isDown()) {
                resetRightDrag();
            }
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != player || client.screen != null || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (!controller.isSelectorStack(player.getMainHandItem())) return InteractionResult.PASS;

            IntPos pos = MinecraftBlockAdapter.toCore(hitResult.getBlockPos());
            if (player.isShiftKeyDown()) {
                if (RIGHT_DRAG_VISITED.add(pos)) {
                    if (rightDragAdding == null) {
                        rightDragAdding = !controller.selection().containsExtra(pos);
                    }
                    boolean changed = rightDragAdding
                            ? controller.selection().addExtra(pos)
                            : controller.selection().removeExtra(pos);
                    if (changed) {
                        feedback(client, (rightDragAdding ? "Added" : "Removed") + " extra block " + pos.compact());
                    }
                }
            } else {
                if (!pos.equals(controller.selection().pos2())) {
                    controller.selection().setPos2(pos);
                    feedback(client, "Corner 2 = " + pos.compact());
                }
            }

            // FAIL cancels vanilla use and does not send a use-block packet to a public server.
            return InteractionResult.FAIL;
        });

        // A selector can be configured to any item. Consume right-click-in-air and entity use too,
        // otherwise selecting e.g. food or an interactable item could still perform its vanilla action.
        UseItemCallback.EVENT.register((player, level, hand) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != player || client.screen != null || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            return controller.isSelectorStack(player.getMainHandItem()) ? InteractionResult.FAIL : InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != player || client.screen != null || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            return controller.isSelectorStack(player.getMainHandItem()) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
    }

    private static void resetLeftDrag() {
        LEFT_DRAG_VISITED.clear();
        leftDragAdding = null;
    }

    private static void resetRightDrag() {
        RIGHT_DRAG_VISITED.clear();
        rightDragAdding = null;
    }

    private static void feedback(Minecraft client, String message) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("[TrapGuard] " + message), true);
        }
    }
}
