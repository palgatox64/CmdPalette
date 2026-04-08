package me.palgato.cmdpalette.client;

import me.palgato.cmdpalette.client.palette.CommandPaletteScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

public class CmdPaletteClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KeyBinding openPalette = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cmdpalette.open_palette",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openPalette.wasPressed()) {
                Window window = client.getWindow();
                boolean ctrlHeld = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                        || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
                if (ctrlHeld && client.currentScreen == null) {
                    client.setScreen(new CommandPaletteScreen());
                }
            }
        });
    }
}
