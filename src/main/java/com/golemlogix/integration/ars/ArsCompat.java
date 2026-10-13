package com.golemlogix.integration.ars;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.secret.GolemMinds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Field;

/**
 * Ars Nouveau integration without a compile-time dependency: a listener for its EffectResolveEvent.Pre (looked up by name),
 * whose public fields say which glyph resolved, who cast it and what it hit. A Dispel on a golem of the rituals goes to
 * {@link GolemMinds#dispel}; then the event is cancelled so the Dispel's own effect is skipped.
 */
public final class ArsCompat {
    private static final String EVENT = "com.hollingsworth.arsnouveau.api.event.EffectResolveEvent$Pre";
    private static Field shooter, hit, effect;

    private ArsCompat() {
    }

    @SuppressWarnings("unchecked")
    public static void init() {
        if (!ModList.get().isLoaded("ars_nouveau")) {
            return;
        }
        try {
            Class<? extends Event> type = (Class<? extends Event>) Class.forName(EVENT);
            shooter = type.getField("shooter");
            hit = type.getField("rayTraceResult");
            effect = type.getField("resolveEffect");
            listen(type);
        } catch (ReflectiveOperationException | ClassCastException ex) {
            GolemLogixMod.LOGGER.warn("Ars Nouveau found, but its spell events are not as expected: the Dispel method is off", ex);
        }
    }

    private static <T extends Event> void listen(Class<T> type) {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, type, (T e) -> onEffect(e));
    }

    private static void onEffect(Event event) {
        try {
            Object glyph = effect.get(event);
            if (glyph == null || !glyph.getClass().getSimpleName().equals("EffectDispel")
                    || !(hit.get(event) instanceof EntityHitResult target)) {
                return;
            }
            ServerPlayer caster = shooter.get(event) instanceof ServerPlayer sp ? sp : null;
            if (GolemMinds.dispel(caster, target.getEntity()) && event instanceof ICancellableEvent c) {
                c.setCanceled(true);
            }
        } catch (IllegalAccessException ex) {
            GolemLogixMod.LOGGER.warn("Could not read an Ars Nouveau spell event", ex);
        }
    }
}
