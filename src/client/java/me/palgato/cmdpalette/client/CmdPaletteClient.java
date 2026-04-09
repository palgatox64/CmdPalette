package me.palgato.cmdpalette.client;

import me.palgato.cmdpalette.client.palette.CommandPaletteScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class CmdPaletteClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KeyBinding.Category cmdPaletteCategory = KeyBinding.Category.create(Identifier.of("cmdpalette", "keybinds"));

        KeyBinding openPalette = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cmdpalette.open_palette",
                InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_ENTER,
            cmdPaletteCategory
        ));

        KeyBinding openPaletteModifier = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cmdpalette.open_palette_modifier",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            cmdPaletteCategory
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openPalette.wasPressed()) {
            boolean modifierSatisfied = openPaletteModifier.isUnbound() || openPaletteModifier.isPressed();
            if (modifierSatisfied && client.currentScreen == null) {
                    client.setScreen(new CommandPaletteScreen());
                }
            }
        });
    }
}
