package com.golemlogix.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client settings (golem_logix-client.toml): remembers the modes the player chose in windows.
 */
public final class GolemClientConfig {
    public static final ModConfigSpec SPEC;

    /** Height of the terminal windows: normal, tall or full screen (the "Size" button in the terminal window). */
    public static final ModConfigSpec.EnumValue<TerminalSize> TERMINAL_SIZE;

    /** Terminal and JEI search: not linked, what is typed in a terminal goes to JEI, or both searches follow each other. */
    public static final ModConfigSpec.EnumValue<JeiSync> JEI_SYNC;

    public enum JeiSync {
        OFF, TO_JEI, TWO_WAY;

        public JeiSync next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public boolean sendsToJei() {
            return this != OFF;
        }

        public boolean readsFromJei() {
            return this == TWO_WAY;
        }
    }

    public enum TerminalSize {
        NORMAL, TALL, FULL;

        public TerminalSize next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        TERMINAL_SIZE = b.comment("Height of the terminal windows (NORMAL, TALL, FULL). Changed by the size button in the window.")
                .defineEnum("terminalSize", TerminalSize.NORMAL);
        JEI_SYNC = b.comment("Terminal search and JEI search: OFF (independent), TO_JEI (typing in a terminal fills JEI), TWO_WAY (both follow each other).",
                        "Changed by the JEI button in the terminal window.")
                .defineEnum("jeiSearchSync", JeiSync.TWO_WAY);
        SPEC = b.build();
    }

    private GolemClientConfig() {
    }
}
