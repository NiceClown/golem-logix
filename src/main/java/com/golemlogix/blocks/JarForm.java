package com.golemlogix.blocks;

import net.minecraft.util.StringRepresentable;

/** What a Cryo-Source Jar is part of: itself alone, a Giga Jar of 2 by 2 jars or an Antigravity Reactor of 3 by 3. */
public enum JarForm implements StringRepresentable {
    SINGLE("single", 1), GIGA("giga", 2), SUPER("super", 3);

    private final String name;
    private final int width;

    JarForm(String name, int width) {
        this.name = name;
        this.width = width;
    }

    /** Jars along a side. */
    public int width() {
        return width;
    }

    public int jars() {
        return width * width;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
