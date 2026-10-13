package com.golemlogix.system;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Hands out the channels of one network the way Applied Energistics 2 does (credited in README, "Credits and license"):
 * <ul>
 * <li>A breadth-first walk starts at the controller (the Atlas Core with its conduits) and gives every node one route
 * towards it: the shortest one. Carriers are walked in tiers, as in AE2: dense carriers (dense pipes, Cable Bushings,
 * Channel Expanders) first, then basic pipes, then devices. A node found from a slower tier stays in that tier, so channels
 * prefer dense runs, the way dense cables extend the faces of an AE2 controller.</li>
 * <li>A device that needs a channel asks for it the moment the walk reaches it. It gets one only if the network and every
 * node on its route still have room; otherwise it stays without a channel and remembers what stopped it.</li>
 * <li>Every node, devices included, carries a limited number of channels (a basic pipe 16, a dense pipe 64, a bushing its
 * line, a device as much as a basic pipe), and the channels a node carries include its own.</li>
 * </ul>
 * This is an independent implementation written for Golem Logix: it contains no AE2 code. Nodes are array indices and every
 * node on a route is checked and counted, so the loads are exact after the walk and need no second pass.
 */
public final class ChannelCalculation {
    /** Parent of the nodes that touch the controller. */
    public static final int ROOT = -1;
    /** Parent of the nodes the walk never reached. */
    public static final int UNREACHED = -2;
    /** No limit for a node. */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    public static final int TIER_DENSE = 0;
    public static final int TIER_PIPE = 1;
    public static final int TIER_DEVICE = 2;

    /** Why a device has no channel. */
    public enum Missing {
        NONE, NETWORK_FULL, BOTTLENECK, NO_ROUTE
    }

    private final int[][] links;
    private final int[] capacity;
    private final int[] tier;
    private final boolean[] needs;
    private final int rootCapacity;

    private final int[] parent;
    private final int[] load;
    private final boolean[] granted;
    private final boolean[] reached;
    private final Missing[] missing;
    private final int[] bottleneck;
    private int used;

    /**
     * @param links        neighbours of every node, in a fixed order (the order decides who comes first)
     * @param capacity     how many channels each node carries (its own included)
     * @param tier         TIER_DENSE, TIER_PIPE or TIER_DEVICE
     * @param needs        the node is a device that takes a channel
     * @param controllers  the controller nodes in walk order (their neighbours are the first nodes of the tree)
     * @param rootCapacity how many channels the whole network has
     */
    public ChannelCalculation(int[][] links, int[] capacity, int[] tier, boolean[] needs, int[] controllers, int rootCapacity) {
        int n = links.length;
        this.links = links;
        this.capacity = capacity;
        this.tier = tier;
        this.needs = needs;
        this.rootCapacity = rootCapacity;
        this.parent = new int[n];
        this.load = new int[n];
        this.granted = new boolean[n];
        this.reached = new boolean[n];
        this.missing = new Missing[n];
        this.bottleneck = new int[n];
        Arrays.fill(parent, UNREACHED);
        Arrays.fill(missing, Missing.NONE);
        Arrays.fill(bottleneck, -1);
        run(controllers);
    }

    private void run(int[] controllers) {
        @SuppressWarnings("unchecked")
        ArrayDeque<Integer>[] queues = new ArrayDeque[]{new ArrayDeque<Integer>(), new ArrayDeque<Integer>(), new ArrayDeque<Integer>()};
        for (int c : controllers) {
            reached[c] = true;
        }
        for (int c : controllers) {
            for (int next : links[c]) {
                if (!reached[next]) {
                    visit(next, ROOT, TIER_DENSE, queues);
                }
            }
        }
        for (int t = 0; t < queues.length; t++) {
            ArrayDeque<Integer> queue = queues[t];
            while (!queue.isEmpty()) {
                int node = queue.poll();
                for (int next : links[node]) {
                    if (!reached[next]) {
                        visit(next, node, t, queues);
                    }
                }
            }
        }
        for (int i = 0; i < needs.length; i++) {
            if (needs[i] && !reached[i]) {
                missing[i] = Missing.NO_ROUTE;
            }
        }
    }

    private void visit(int node, int from, int currentTier, ArrayDeque<Integer>[] queues) {
        reached[node] = true;
        parent[node] = from;
        if (needs[node]) {
            claim(node);
        }
        queues[Math.max(tier[node], currentTier)].add(node);
    }

    /** One channel along the whole route of the device, or nothing at all. */
    private void claim(int device) {
        if (used >= rootCapacity) {
            missing[device] = Missing.NETWORK_FULL;
            return;
        }
        for (int i = device; i != ROOT; i = parent[i]) {
            if (load[i] >= capacity[i]) {
                missing[device] = Missing.BOTTLENECK;
                bottleneck[device] = i;
                return;
            }
        }
        for (int i = device; i != ROOT; i = parent[i]) {
            load[i]++;
        }
        used++;
        granted[device] = true;
    }

    /** Channels in use in the whole network. */
    public int used() {
        return used;
    }

    /** Channels that pass through the node (its own included). */
    public int load(int node) {
        return load[node];
    }

    public boolean granted(int node) {
        return granted[node];
    }

    /** The walk from the controller reached the node (controller nodes count as reached). */
    public boolean reached(int node) {
        return reached[node];
    }

    /** The next node towards the controller: ROOT next to the controller, UNREACHED if the walk never got here. */
    public int parent(int node) {
        return parent[node];
    }

    public Missing missing(int node) {
        return missing[node];
    }

    /** The full node that stopped the device (-1 if something else did). */
    public int bottleneck(int node) {
        return bottleneck[node];
    }
}
