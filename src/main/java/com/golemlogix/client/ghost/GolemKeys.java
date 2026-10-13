package com.golemlogix.client.ghost;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиши управления проекцией стройки (переназначаются в настройках управления).
 * Y — зафиксировать (Yes), N — отменить (No). Пока проекция включена, эти клавиши
 * имеют приоритет над одноимёнными клавишами других модов (см. {@link GhostEvents#onKey}).
 */
public final class GolemKeys {
    public static final String CATEGORY = "key.categories.golem_logix";
    public static final KeyMapping GHOST_CONFIRM = new KeyMapping("key.golem_logix.ghost_confirm",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);
    public static final KeyMapping GHOST_CANCEL = new KeyMapping("key.golem_logix.ghost_cancel",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);

    private GolemKeys() {
    }
}
