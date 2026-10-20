package com.golemlogix.items;

import net.minecraft.network.chat.Component;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The tooltip as a list again (since 1.21.5 the lines go to a consumer): every added line is passed on at once. */
public final class TooltipLines extends AbstractList<Component> {
    private final Consumer<Component> sink;
    private final List<Component> lines = new ArrayList<>();

    public TooltipLines(Consumer<Component> sink) {
        this.sink = sink;
    }

    @Override
    public void add(int index, Component line) {
        lines.add(index, line);
        sink.accept(line);
    }

    @Override
    public Component get(int index) {
        return lines.get(index);
    }

    @Override
    public int size() {
        return lines.size();
    }
}
