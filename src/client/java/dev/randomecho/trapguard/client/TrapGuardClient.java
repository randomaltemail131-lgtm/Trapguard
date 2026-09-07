package dev.randomecho.trapguard.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.randomecho.trapguard.client.command.TrapGuardCommands;
import dev.randomecho.trapguard.client.input.SelectorInput;
import dev.randomecho.trapguard.client.screen.TrapGuardScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class TrapGuardClient implements ClientModInitializer {
    private TrapGuardController controller;
    private KeyMapping openGuiKey;
    private KeyMapping globalMonitoringKey;
    private boolean clearHudComboWasDown;

    @Override
    public void onInitializeClient() {
        controller = new TrapGuardController();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("trapguard", "general")
        );
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.trapguard.open_gui",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_G,
                category
        ));
        globalMonitoringKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.trapguard.toggle_global_monitoring",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_H,
                category
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                TrapGuardCommands.register(dispatcher, controller));
        SelectorInput.register(controller);

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("trapguard", "alerts"),
                (graphics, tickCounter) -> controller.alerts().render(graphics, controller.config())
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                if (client.screen == null) client.setScreen(new TrapGuardScreen(controller));
            }
            while (globalMonitoringKey.consumeClick()) {
                if (client.screen == null) {
                    controller.setGlobalMonitoringEnabled(!controller.config().globalMonitoringEnabled);
                }
            }

            boolean clearHudComboDown = InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_F3)
                    && InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_D);
            if (clearHudComboDown && !clearHudComboWasDown) controller.alerts().clearVisible();
            clearHudComboWasDown = clearHudComboDown;

            controller.tick(client);
        });
    }
}
