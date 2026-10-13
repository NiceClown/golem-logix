package com.golemlogix.devtest;

import com.golemlogix.GolemLogixMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * Rig for checking the ghost build hotkeys: a "foreign" binding on the Y key (like another mod's) and emulation of a press
 * INSIDE the game (the same sequence as KeyboardHandler: the binding counter + an InputEvent.Key event). OS input is not touched.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DevKeys {
    /** Foreign binding on Y; counts how many times it "fired". */
    public static final KeyMapping CONFLICT = new KeyMapping("key.devshots.conflict", GLFW.GLFW_KEY_Y, "key.categories.devshots");
    public static int conflictClicks;

    private DevKeys() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        if (System.getProperty("golemlogix.devshots") != null) {
            event.register(CONFLICT);
        }
    }

    /** Press and release the key with GLFW code key. */
    public static void press(int glfwKey) {
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(glfwKey);
        KeyMapping.set(key, true);
        KeyMapping.click(key);
        NeoForge.EVENT_BUS.post(new InputEvent.Key(glfwKey, GLFW.glfwGetKeyScancode(glfwKey), GLFW.GLFW_PRESS, 0));
        // in the tick the game polls the bindings: count the foreign binding's fires here, as its owner would
        mc.execute(() -> {
        });
    }

    /** Called every client tick: the foreign binding takes its presses, as another mod does. */
    public static void pollConflict() {
        while (CONFLICT.consumeClick()) {
            conflictClicks++;
        }
    }

    public static void release(int glfwKey) {
        KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(glfwKey), false);
    }
}
