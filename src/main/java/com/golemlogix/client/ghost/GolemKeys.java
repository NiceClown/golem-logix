package com.golemlogix.client.ghost;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Keys that control the build projection (rebindable in the controls settings).
 * Y fixes it (Yes), N cancels it (No). While the projection is on, these keys
 * take priority over the same keys of other mods (see {@link GhostEvents#onKey}).
 */
public final class GolemKeys {
    public static final String CATEGORY = "key.categories.golem_logix";
    public static final KeyMapping GHOST_CONFIRM = new KeyMapping("key.golem_logix.ghost_confirm",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);
    public static final KeyMapping GHOST_CANCEL = new KeyMapping("key.golem_logix.ghost_cancel",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);
    /** Opens the Portable Chronometer worn in the Curios slot or carried anywhere in the inventory (Ctrl+E). */
    public static final KeyMapping OPEN_CHRONOMETER = new KeyMapping("key.golem_logix.open_chronometer",
            KeyConflictContext.IN_GAME, net.neoforged.neoforge.client.settings.KeyModifier.CONTROL, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E, CATEGORY);

    private GolemKeys() {
    }
}
