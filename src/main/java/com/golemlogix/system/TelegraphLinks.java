package com.golemlogix.system;

import com.golemlogix.blockentity.TelegraphBlockEntity;
import com.golemlogix.items.PortableChronometerItem;
import com.golemlogix.items.TelegraphLinkCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Links between Cryo-Source Telegraphs (server). A telegraph with a written Aetherial Linkage card in its Memory slot is a
 * receiver: it links to the telegraph the card names (the host), if both are loaded, the host's seal is still the one on the card
 * and the distance is within the better range of the two (no card: localWirelessRange blocks in one dimension, the Aether Resonance
 * Core: the whole dimension, the Void-Glass Prism: any dimension). A host may have any number of receivers. The network walk asks
 * this registry for a telegraph's partners, so a link joins both sides into one network.
 * <p>
 * Only the receivers are registered (on load and on card changes): the host finds them here. Nothing is saved; the registry
 * fills again as the telegraphs load.
 */
public final class TelegraphLinks {
    /** Receiver -> the host its card names (loaded receivers with a written card). */
    private static final Map<GlobalPos, GlobalPos> HOST_OF = new HashMap<>();
    /** Host -> its receivers, sorted (the walk order must not depend on the order the chunks loaded in). */
    private static final Map<GlobalPos, Set<GlobalPos>> RECEIVERS = new HashMap<>();
    private static final Comparator<GlobalPos> ORDER = Comparator.<GlobalPos, String>comparing(g -> g.dimension().location().toString())
            .thenComparing(GlobalPos::pos);

    /** What a telegraph's Memory slot amounts to. */
    public enum Status {
        /** No card in the Memory slot. */
        NO_CARD,
        /** A blank card: nothing written on it. */
        BLANK,
        LINKED,
        /** No telegraph stands where the card points (or its dimension does not exist). */
        HOST_MISSING,
        /** The named telegraph is not loaded right now. */
        HOST_UNLOADED,
        /** The named telegraph was reset since the card was written. */
        OUTDATED,
        /** The card names this very telegraph. */
        SELF,
        OUT_OF_RANGE
    }

    /** A partner of a telegraph in the network walk; toHost = this telegraph is the receiver of the link (each link counted once). */
    public record Partner(GolemNetwork.Node node, boolean toHost) {
    }

    private TelegraphLinks() {
    }

    public static void clear() {
        HOST_OF.clear();
        RECEIVERS.clear();
    }

    /** The telegraph loaded or its Memory card changed: (re)register it as a receiver. */
    public static void track(TelegraphBlockEntity t) {
        Level level = t.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        GlobalPos self = GlobalPos.of(level.dimension(), t.getBlockPos());
        TelegraphLinkCardItem.Link link = TelegraphLinkCardItem.link(t.memoryCard());
        GlobalPos host = link == null ? null : link.target();
        GlobalPos old = HOST_OF.get(self);
        if (java.util.Objects.equals(old, host)) {
            return;
        }
        untrack(self);
        if (host != null) {
            HOST_OF.put(self, host);
            RECEIVERS.computeIfAbsent(host, h -> new TreeSet<>(ORDER)).add(self);
        }
    }

    /** The telegraph unloaded or was removed. */
    public static void untrack(TelegraphBlockEntity t) {
        if (t.getLevel() != null && !t.getLevel().isClientSide()) {
            untrack(GlobalPos.of(t.getLevel().dimension(), t.getBlockPos()));
        }
    }

    private static void untrack(GlobalPos self) {
        GlobalPos old = HOST_OF.remove(self);
        if (old != null) {
            Set<GlobalPos> set = RECEIVERS.get(old);
            if (set != null) {
                set.remove(self);
                if (set.isEmpty()) {
                    RECEIVERS.remove(old);
                }
            }
        }
    }

    /** The loaded receivers that name this telegraph (whatever their status), sorted. */
    public static List<TelegraphBlockEntity> receiversOf(TelegraphBlockEntity host) {
        Level level = host.getLevel();
        if (!(level instanceof ServerLevel sl)) {
            return List.of();
        }
        Set<GlobalPos> set = RECEIVERS.get(GlobalPos.of(level.dimension(), host.getBlockPos()));
        if (set == null) {
            return List.of();
        }
        List<TelegraphBlockEntity> out = new ArrayList<>();
        for (GlobalPos r : set) {
            ServerLevel rl = sl.getServer().getLevel(r.dimension());
            if (rl != null && rl.isLoaded(r.pos()) && rl.getBlockEntity(r.pos()) instanceof TelegraphBlockEntity rt) {
                out.add(rt);
            }
        }
        return out;
    }

    /** What the receiver's Memory card amounts to right now. */
    public static Status status(TelegraphBlockEntity receiver) {
        return resolve(receiver).status;
    }

    /** The host the receiver is linked to, or null. */
    @Nullable
    public static TelegraphBlockEntity hostOf(TelegraphBlockEntity receiver) {
        Resolved r = resolve(receiver);
        return r.status == Status.LINKED ? r.host : null;
    }

    private record Resolved(Status status, @Nullable TelegraphBlockEntity host) {
    }

    private static Resolved resolve(TelegraphBlockEntity t) {
        if (t.memoryCard().isEmpty()) {
            return new Resolved(Status.NO_CARD, null);
        }
        TelegraphLinkCardItem.Link link = TelegraphLinkCardItem.link(t.memoryCard());
        if (link == null) {
            return new Resolved(Status.BLANK, null);
        }
        if (!(t.getLevel() instanceof ServerLevel level)) {
            return new Resolved(Status.HOST_UNLOADED, null);
        }
        MinecraftServer server = level.getServer();
        ServerLevel hostLevel = server.getLevel(link.target().dimension());
        BlockPos hostPos = link.target().pos();
        if (hostLevel == null) {
            return new Resolved(Status.HOST_MISSING, null);
        }
        if (hostLevel == level && hostPos.equals(t.getBlockPos())) {
            return new Resolved(Status.SELF, null);
        }
        if (!hostLevel.isLoaded(hostPos)) {
            return new Resolved(Status.HOST_UNLOADED, null);
        }
        if (!(hostLevel.getBlockEntity(hostPos) instanceof TelegraphBlockEntity host)) {
            return new Resolved(Status.HOST_MISSING, null);
        }
        if (host.seal() != link.seal()) {
            return new Resolved(Status.OUTDATED, host);
        }
        int tier = Math.max(t.rangeTier(), host.rangeTier());
        BlockPos p = t.getBlockPos();
        if (!PortableChronometerItem.inRange(tier, level, new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), hostLevel, hostPos)) {
            return new Resolved(Status.OUT_OF_RANGE, host);
        }
        return new Resolved(Status.LINKED, host);
    }

    /** The telegraphs a telegraph is linked to: its host, then the receivers that are linked to it. */
    public static List<Partner> partners(Level level, BlockPos pos) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof TelegraphBlockEntity t)) {
            return List.of();
        }
        List<Partner> out = new ArrayList<>(2);
        TelegraphBlockEntity host = hostOf(t);
        if (host != null) {
            out.add(new Partner(new GolemNetwork.Node(host.getLevel(), host.getBlockPos()), true));
        }
        for (TelegraphBlockEntity r : receiversOf(t)) {
            if (hostOf(r) == t) {
                out.add(new Partner(new GolemNetwork.Node(r.getLevel(), r.getBlockPos()), false));
            }
        }
        return out;
    }

    /** How many receivers are linked to this telegraph now. */
    public static int linkedReceivers(TelegraphBlockEntity host) {
        int n = 0;
        for (TelegraphBlockEntity r : receiversOf(host)) {
            if (hostOf(r) == host) {
                n++;
            }
        }
        return n;
    }
}
