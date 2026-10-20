package com.golemlogix.blockentity;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A SimpleContainer that tells listeners about changes (since 1.21.2 SimpleContainer has no listeners of its own). */
public class ListeningContainer extends SimpleContainer {
    private final List<Consumer<Container>> listeners = new ArrayList<>();

    public ListeningContainer(int size) {
        super(size);
    }

    public void addListener(Consumer<Container> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<Container> listener) {
        listeners.remove(listener);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        for (Consumer<Container> l : List.copyOf(listeners)) {
            l.accept(this);
        }
    }
}
