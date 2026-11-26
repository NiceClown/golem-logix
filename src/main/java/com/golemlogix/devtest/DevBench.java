package com.golemlogix.devtest;

import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Benchmarks of the rig:
 * <pre>
 *   craftbench x y z item count timeoutTicks [label]   an autocraft order on the network at (x, y, z), waited for until the
 *                                                     network holds the result: game ticks, real seconds, server tick times
 *   perf seconds [label]                              client FPS (frame limit lifted) and server tick times over a window
 * </pre>
 * Both wait (the scenario goes on when they are done) and log one result line.
 */
final class DevBench {
    private DevBench() {
    }

    /** Average and maximum of the server's last 100 tick times, in milliseconds. */
    private static double[] tickTimes(MinecraftServer server) {
        long[] t = server.getTickTimesNanos();
        double sum = 0, max = 0;
        int n = 0;
        for (long v : t) {
            if (v > 0) {
                sum += v;
                max = Math.max(max, v);
                n++;
            }
        }
        return new double[]{n == 0 ? 0 : sum / n / 1e6, max / 1e6};
    }

    static Predicate<Minecraft> craftBench(String[] t, Consumer<String> log) {
        BlockPos pos = new BlockPos(Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3]));
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(t[4]));
        long count = Long.parseLong(t[5]);
        long timeout = Long.parseLong(t[6]);
        String label = t.length > 7 ? t[7] : t[4];
        ItemKey key = ItemKey.of(new ItemStack(item));
        long[] st = {-1, 0, 0, 0};          // start game time, start nanos, count before, checks
        double[] mspt = {0, 0, 0};         // sum of averages, samples, max
        boolean[] flags = {false, false, false};   // started, check pending, finished
        return mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            if (flags[2]) {
                return true;
            }
            if (!flags[0]) {
                flags[0] = true;
                flags[1] = true;
                server.execute(() -> {
                    ServerLevel level = server.overworld();
                    GolemNetwork net = GolemNetwork.scan(level, pos);
                    st[2] = net.itemCountOf(key);
                    var player = server.getPlayerList().getPlayers().get(0);
                    var r = net.startCrafting(Map.of(key, count), player.getUUID(), null);
                    long execs = r.plan() == null ? -1 : r.plan().totalExecutions();
                    log.accept(String.format(Locale.ROOT, "craftbench %s: %s x%d -> %s, %d executions planned", label, t[4], count,
                            r.type(), execs));
                    if (r.type() != GolemNetwork.StartType.STARTED) {
                        flags[2] = true;
                    }
                    st[0] = level.getGameTime();
                    st[1] = System.nanoTime();
                    flags[1] = false;
                });
                return false;
            }
            if (flags[1]) {
                return false;
            }
            flags[1] = true;
            server.execute(() -> {
                ServerLevel level = server.overworld();
                long ticks = level.getGameTime() - st[0];
                double[] tt = tickTimes(server);
                mspt[0] += tt[0];
                mspt[1]++;
                mspt[2] = Math.max(mspt[2], tt[1]);
                long have = GolemNetwork.get(level, pos).itemCountOf(key) - st[2];
                if (++st[3] % 400 == 0) {
                    log.accept(String.format(Locale.ROOT, "craftbench %s: %d / %d after %d ticks", label, have, count, ticks));
                }
                boolean done = have >= count;
                if (done || ticks > timeout) {
                    log.accept(String.format(Locale.ROOT,
                            "craftbench %s: %s after %d ticks (%.1f game s), real %.1f s, server tick avg %.2f ms, max %.2f ms",
                            label, done ? "DONE" : "TIMEOUT " + have + "/" + count, ticks, ticks / 20.0,
                            (System.nanoTime() - st[1]) / 1e9, mspt[1] == 0 ? 0 : mspt[0] / mspt[1], mspt[2]));
                    flags[2] = true;
                }
                flags[1] = false;
            });
            return false;
        };
    }

    static Predicate<Minecraft> perf(String[] t, Consumer<String> log) {
        int seconds = Integer.parseInt(t[1]);
        String label = t.length > 2 ? t[2] : "";
        List<Integer> fps = new ArrayList<>();
        double[] server = {0, 0, 0};       // sum of averages, samples, max
        long[] next = {-1, 0};             // next sample (nanos), the original frame limit
        return mc -> {
            long now = System.nanoTime();
            if (next[0] < 0) {
                next[1] = mc.options.framerateLimit().get();
                mc.options.framerateLimit().set(260);
                next[0] = now + 2_000_000_000L;    // let the counter settle without the limit
                return false;
            }
            if (now < next[0]) {
                return false;
            }
            next[0] = now + 1_000_000_000L;
            fps.add(mc.getFps());
            double[] tt = tickTimes(mc.getSingleplayerServer());
            server[0] += tt[0];
            server[1]++;
            server[2] = Math.max(server[2], tt[1]);
            if (fps.size() < seconds) {
                return false;
            }
            mc.options.framerateLimit().set((int) next[1]);
            double avg = fps.stream().mapToInt(Integer::intValue).average().orElse(0);
            int min = fps.stream().mapToInt(Integer::intValue).min().orElse(0);
            log.accept(String.format(Locale.ROOT, "perf %s: fps avg %.0f min %d; server tick avg %.2f ms max %.2f ms (%d s)",
                    label, avg, min, server[0] / server[1], server[2], seconds));
            return true;
        };
    }

    // ---- stress test of a full network
    static boolean churn;
    static com.golemlogix.system.GolemNetwork stressNet;
    static long stamp;
    static int serialBase;
    static boolean wide;
    static java.util.List<net.minecraft.world.item.Item> wideItems;
    private static boolean listening;

    static void stressFill(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos b, Consumer<String> log) {
        var bay2 = b.offset(8, 0, 0);
        com.golemlogix.blocks.CopperDriveBayBlock.place(level, bay2, net.minecraft.core.Direction.SOUTH);
        for (var pos : List.of(b.offset(12, 0, -1), bay2)) {
            var drive = (com.golemlogix.blockentity.CopperDriveBayBlockEntity) level.getBlockEntity(pos);
            for (int i = 0; i < 10; i++) {
                drive.getContainer().setItem(i, new net.minecraft.world.item.ItemStack(com.golemlogix.init.ModItems.COPPER_DISK_256K.get()));
            }
        }
        var n = com.golemlogix.system.GolemNetwork.scan(level, b);
        stressNet = n;
        long t0 = System.nanoTime();
        int plain = 0, gear = 0, gearRefused = 0, failed = 0;
        java.util.Map<String, Integer> byMod = new java.util.TreeMap<>();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (item == net.minecraft.world.item.Items.AIR) continue;
            try {
                var s = new net.minecraft.world.item.ItemStack(item);
                if (n.insertItem(s.copy(), false).isEmpty()) {
                    plain++;
                    byMod.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace(), 1, Integer::sum);
                }
                if (s.isDamageableItem()) {
                    var worn = s.copy();
                    worn.setDamageValue(1);
                    if (com.golemlogix.system.Gear.is(worn)) {
                        if (n.insertItem(worn, false).isEmpty()) gear++; else gearRefused++;
                    }
                }
            } catch (Throwable e) {
                failed++;
            }
        }
        long left = n.itemCapacity() - n.itemsStoredOnDisks();
        while (left > 0) {
            int c = (int) Math.min(left, 1_000_000);
            n.insertItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, c), false);
            left -= c;
        }
        log.accept("stress fill: plain " + plain + " worn " + gear + " worn refused " + gearRefused + " failed " + failed + " in " + (System.nanoTime() - t0) / 1000000
                + " ms; used " + n.itemsStoredOnDisks() + " / " + n.itemCapacity() + "; by mod " + byMod);
        stressMeasure(level, log);
    }

    static void stressMeasure(net.minecraft.server.level.ServerLevel level, Consumer<String> log) {
        var n = stressNet;
        long a = 0;
        for (int i = 0; i < 20; i++) {
            long t = System.nanoTime();
            a += n.listItems().size();
            a += System.nanoTime() - t > 0 ? 0 : 0;
        }
        long t1 = System.nanoTime();
        var items = n.listItems();
        long one = System.nanoTime() - t1;
        t1 = System.nanoTime();
        var copy = new java.util.LinkedHashMap<>(items);
        boolean same = items.equals(copy);
        long cmp = System.nanoTime() - t1;
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess(), net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        var entries = new ArrayList<com.golemlogix.network.TerminalListPayload.Entry>();
        items.forEach((k, v) -> entries.add(new com.golemlogix.network.TerminalListPayload.Entry(k.stack(), v, false)));
        t1 = System.nanoTime();
        com.golemlogix.network.TerminalListPayload.STREAM_CODEC.encode(buf, new com.golemlogix.network.TerminalListPayload(0, true, entries));
        long enc = System.nanoTime() - t1;
        log.accept(String.format(Locale.ROOT, "stress types %d: list %.2f ms, compare %.2f ms (same %b), full list %d KB encoded in %.2f ms", items.size(), one / 1e6, cmp / 1e6, same, buf.readableBytes() / 1024, enc / 1e6));
        buf.release();
    }

    static void stressMore(net.minecraft.server.level.ServerLevel level, int extra, Consumer<String> log) {
        var n = stressNet;
        n.extractItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE), extra + 1000, false);
        for (int i = 0; i < extra; i++) {
            var s = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE);
            s.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("t" + i));
            n.insertItem(s, false);
        }
        stressMeasure(level, log);
    }

    /** One type's count changes every tick, as with a working farm. */
    static void setChurn(boolean on) {
        churn = on;
        if (!listening) {
            listening = true;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> {
                stressNet = null;
                churn = false;
                wide = false;
            });
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> {
                if (wide && stressNet != null) {
                    if (wideItems == null) {
                        wideItems = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(i -> i != net.minecraft.world.item.Items.AIR && i != net.minecraft.world.item.Items.COBBLESTONE && new net.minecraft.world.item.ItemStack(i).getMaxStackSize() == 64).limit(200).toList();
                    }
                    boolean add = e.getServer().getTickCount() % 2 == 0;
                    if (add) stressNet.extractItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE), 200, false);
                    for (var it : wideItems) {
                        if (add) stressNet.insertItem(new net.minecraft.world.item.ItemStack(it), false);
                        else stressNet.extractItem(new net.minecraft.world.item.ItemStack(it), 1, false);
                    }
                    if (!add) stressNet.insertItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 200), false);
                }
                if (churn && stressNet != null) {
                    var c = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE);
                    stressNet.extractItem(c, 64, false);
                    stressNet.insertItem(c.copyWithCount(1 + (int) (e.getServer().getTickCount() % 64)), false);
                }
            });
        }
    }

    /** How long a new item in the network takes to show in the open terminal, several times. */
    static Predicate<Minecraft> termLatency(int times, Consumer<String> log) {
        List<Long> ms = new ArrayList<>();
        long[] started = {0};
        int[] serial = {serialBase += 100};
        return mc -> {
            if (!(mc.player.containerMenu instanceof com.golemlogix.menu.TerminalMenu menu)) {
                throw new IllegalStateException("no terminal open");
            }
            if (started[0] == 0) {
                serial[0]++;
                String name = "marker" + serial[0];
                started[0] = System.nanoTime();
                mc.getSingleplayerServer().execute(() -> {
                    stressNet.extractItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE), 10, false);
                    var s = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
                    s.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
                    stressNet.insertItem(s, false);
                });
                return false;
            }
            String name = "marker" + serial[0];
            for (var e : menu.clientEntries) {
                if (e.key().getHoverName().getString().equals(name)) {
                    ms.add((System.nanoTime() - started[0]) / 1000000);
                    started[0] = 0;
                    break;
                }
            }
            if (ms.size() >= times) {
                log.accept("stress terminal latency ms: " + ms + " avg " + ms.stream().mapToLong(Long::longValue).average().orElse(0));
                return true;
            }
            return false;
        };
    }

    /** The open terminal has its whole list: how long since the stamp, and how many entries. */
    static Predicate<Minecraft> termReady(int atLeast, Consumer<String> log) {
        return mc -> {
            if (mc.player.containerMenu instanceof com.golemlogix.menu.TerminalMenu menu && menu.clientEntries.size() >= atLeast) {
                log.accept("stress terminal open: " + (System.nanoTime() - stamp) / 1000000 + " ms to " + menu.clientEntries.size() + " entries");
                return true;
            }
            return false;
        };
    }
}
