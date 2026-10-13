package com.golemlogix.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Клиентские вызовы, вынесенные из общих классов, чтобы выделенный сервер не пытался загрузить клиентский код.
 */
public final class ClientHooks {
    private ClientHooks() {
    }

    /**
     * Строка поиска JEI (если JEI установлен — плагин подставляет сюда свои функции): терминалы мода зеркалят свой поиск в
     * JEI и подхватывают то, что игрок набрал в JEI.
     */
    public static java.util.function.Consumer<String> jeiSetSearch = s -> {
    };
    public static java.util.function.Supplier<String> jeiGetSearch = () -> null;

    /** Локальный игрок (или null) — для голов атлантов, следящих за ним. */
    public static net.minecraft.world.entity.player.Player localPlayer() {
        return Minecraft.getInstance().player;
    }

    public static void openGuideBook() {
        Minecraft.getInstance().setScreen(new com.golemlogix.client.book.BookScreen());
    }

    /** Уведомление «Заказ готов» и короткий звон. */
    public static void craftDone(List<ItemStack> items) {
        Minecraft mc = Minecraft.getInstance();
        mc.getToasts().addToast(new CraftDoneToast(items));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.6f, 0.45f));
    }
}
