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
 * Стенд проверки горячих клавиш призрачной стройки: «чужая» привязка на клавише Y (как у другого мода) и эмуляция нажатия
 * ВНУТРИ игры (та же последовательность, что у KeyboardHandler: счётчик привязок + событие InputEvent.Key). Ввод ОС не затрагивается.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DevKeys {
    /** Чужая привязка на Y; считает, сколько раз она «сработала». */
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

    /** Нажать и отпустить клавишу с кодом GLFW key. */
    public static void press(int glfwKey) {
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(glfwKey);
        KeyMapping.set(key, true);
        KeyMapping.click(key);
        NeoForge.EVENT_BUS.post(new InputEvent.Key(glfwKey, GLFW.glfwGetKeyScancode(glfwKey), GLFW.GLFW_PRESS, 0));
        // в тике игра опросит привязки: считаем срабатывания чужой привязки здесь, как это сделал бы её владелец
        mc.execute(() -> {
        });
    }

    /** Вызывается каждый тик клиента: чужая привязка забирает свои нажатия, как это делает чужой мод. */
    public static void pollConflict() {
        while (CONFLICT.consumeClick()) {
            conflictClicks++;
        }
    }

    public static void release(int glfwKey) {
        KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(glfwKey), false);
    }
}
