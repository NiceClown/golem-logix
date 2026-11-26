package com.golemlogix.blocks;

import com.golemlogix.security.SecurityPermission;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Shared Security Terminal permission checks for network block interactions. */
final class BlockSecurity {
    private BlockSecurity() {
    }

    /** Scans the network at pos and notifies the player if they lack BUILD permission. */
    static boolean allowBuild(Level level, BlockPos pos, Player player) {
        return allow(GolemNetwork.scan(level, pos), player, SecurityPermission.BUILD);
    }

    static boolean allow(GolemNetwork net, Player player, SecurityPermission perm) {
        if (net.hasPermission(player, perm)) {
            return true;
        }
        deny(player);
        return false;
    }

    /** Terminal UI access requires at least one of INSERT, EXTRACT, or CRAFT. */
    static boolean allowTerminal(GolemNetwork net, Player player) {
        if (net.hasPermission(player, SecurityPermission.INSERT)
                || net.hasPermission(player, SecurityPermission.EXTRACT)
                || net.hasPermission(player, SecurityPermission.CRAFT)) {
            return true;
        }
        deny(player);
        return false;
    }

    private static void deny(Player player) {
        if (player instanceof ServerPlayer sp) {
            sp.sendOverlayMessage(Component.translatable("message.golem_logix.security_denied"));
        }
    }
}
