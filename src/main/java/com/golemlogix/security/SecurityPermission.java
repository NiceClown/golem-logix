package com.golemlogix.security;

/**
 * Access permissions enforced by a network's Security Terminal.
 */
public enum SecurityPermission {
    INSERT(1),
    EXTRACT(2),
    CRAFT(4),
    BUILD(8);

    public final int mask;

    SecurityPermission(int mask) {
        this.mask = mask;
    }

    public boolean test(int permissions) {
        return (permissions & mask) != 0;
    }

    public static final int ALL = INSERT.mask | EXTRACT.mask | CRAFT.mask | BUILD.mask;
}
