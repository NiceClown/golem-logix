package com.golemlogix.system;

import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.CryoDiodeBlockEntity;
import com.golemlogix.blockentity.CryoMainframeBlockEntity;
import com.golemlogix.blockentity.EternalFrostResonatorBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.PeriscopeBlockEntity;
import com.golemlogix.blockentity.PipeBlockEntity;
import com.golemlogix.blockentity.SecurityTerminalBlockEntity;
import com.golemlogix.blocks.AtlasConduitBlock;
import com.golemlogix.blocks.AtlasCoreBlock;
import com.golemlogix.blocks.CableBushingBlock;
import com.golemlogix.blocks.ChannelExpanderBlock;
import com.golemlogix.blocks.CryoCalculatorBlock;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.blocks.PeriscopeStorageBusBlock;
import com.golemlogix.blocks.SnowPipeBlock;
import com.golemlogix.blocks.TelegraphBlock;
import com.golemlogix.config.GolemConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Graph walk, Source peripheral discovery, core selection, channel allocation, and active-visual syncing for {@link GolemNetwork}.
 */
final class NetworkScanner {
    private record SourceCandidate(Level level, BlockPos pos, Direction fromSide) {
    }

    private NetworkScanner() {
    }

    static GolemNetwork scan(Level level, BlockPos start) {
        GolemNetwork net = new GolemNetwork(level, start.immutable());
        List<List<GolemNetwork.Node>> linkedTo = new ArrayList<>();
        List<GolemNetwork.Node> cores = new ArrayList<>();
        List<SourceCandidate> sourceCandidates = new ArrayList<>();

        walkGraph(net, linkedTo, cores, sourceCandidates);
        int[][] links = buildLinkTable(net, linkedTo);
        collectSources(net, sourceCandidates);
        configureCore(net, cores, links);
        return net;
    }

    private static void walkGraph(GolemNetwork net, List<List<GolemNetwork.Node>> linkedTo,
                                  List<GolemNetwork.Node> cores, List<SourceCandidate> sourceCandidates) {
        ArrayDeque<GolemNetwork.Node> queue = new ArrayDeque<>();
        GolemNetwork.Node first = net.node(net.start);
        queue.add(first);
        Set<GolemNetwork.Node> seen = new HashSet<>();
        seen.add(first);

        while (!queue.isEmpty() && net.nodeList.size() < GolemNetwork.MAX_NODES) {
            GolemNetwork.Node node = queue.poll();
            Level lvl = node.level();
            BlockPos pos = node.pos();
            if (!lvl.isLoaded(pos)) {
                continue;
            }
            BlockState state = lvl.getBlockState(pos);
            Block block = state.getBlock();
            if (!(block instanceof GolemNode)) {
                continue;
            }
            net.nodeIndex.put(node, net.nodeList.size());
            net.nodeList.add(node);
            net.nodeStates.add(state);

            if (block instanceof AtlasCoreBlock) {
                cores.add(node);
            }
            PipeBlockEntity selfPipe = recordNodeBlockEntity(net, lvl, pos, state, block);

            List<GolemNetwork.Node> links = new ArrayList<>(6);
            for (Direction dir : GolemNetwork.DIRECTIONS) {
                BlockPos next = pos.relative(dir);
                if (!lvl.isLoaded(next)) {
                    continue;
                }
                BlockState nextState = lvl.getBlockState(next);
                // A node gets "seen" only if there is a link: a multiblock socket may be reached from another side
                boolean linked = GolemNode.linked(state, nextState, dir)
                        && (selfPipe == null || !selfPipe.isPlugged(dir))
                        && (!(nextState.getBlock() instanceof SnowPipeBlock)
                        || !PipeBlockEntity.plugged(lvl, next, dir.getOpposite()));
                if (linked) {
                    links.add(new GolemNetwork.Node(lvl, next));
                } else if (nextState.hasBlockEntity()) {
                    sourceCandidates.add(new SourceCandidate(lvl, next, dir.getOpposite()));
                }
            }
            // A telegraph also links to its distant partners: the host its memory card names and the telegraphs naming it
            if (block instanceof TelegraphBlock && TelegraphBlock.isMaster(state)) {
                for (TelegraphLinks.Partner p : TelegraphLinks.partners(lvl, pos)) {
                    links.add(p.node());
                    if (p.toHost()) {
                        net.telegraphLinks++;
                    }
                }
            }
            for (GolemNetwork.Node next : links) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
            linkedTo.add(links);
        }
    }

    /** Registers devices and periscope externals for one visited node; returns the node's PipeBlockEntity if it is a snow pipe. */
    @Nullable
    private static PipeBlockEntity recordNodeBlockEntity(GolemNetwork net, Level lvl, BlockPos pos, BlockState state, Block block) {
        PipeBlockEntity selfPipe = null;
        BlockEntity be = null;
        if (state.hasBlockEntity()) {
            if (block instanceof SnowPipeBlock) {
                selfPipe = lvl.getBlockEntity(pos) instanceof PipeBlockEntity p ? p : null;
            } else {
                be = lvl.getBlockEntity(pos);
            }
        }
        if (be instanceof CopperDriveBayBlockEntity bay) {
            net.bays.add(bay);
        } else if (be instanceof BusBlockEntity bus) {
            net.buses.add(bus);
        } else if (be instanceof PatternProviderBlockEntity provider) {
            net.providers.add(provider);
        } else if (be instanceof CardCatalogBlockEntity catalog) {
            net.catalogs.add(catalog);
        } else if (be instanceof CryoCalculatorBlockEntity cpu) {
            net.cpus.add(cpu);
        } else if (be instanceof CryoMainframeBlockEntity mainframe) {
            // Mainframe cores are separate processors, but they share one channel (the owner block's)
            net.cpus.addAll(mainframe.cores());
        } else if (be instanceof SecurityTerminalBlockEntity sec && net.security == null) {
            net.security = sec;
        }
        if (block instanceof PeriscopeStorageBusBlock) {
            Direction facing = state.getValue(PeriscopeStorageBusBlock.FACING);
            BlockPos target = pos.relative(facing);
            if (lvl.isLoaded(target)) {
                BlockState ts = lvl.getBlockState(target);
                if (!(ts.getBlock() instanceof GolemNode)) {
                    net.peripherals.put(new GolemNetwork.Node(lvl, target), ts.getBlock());
                    PeriscopeBlockEntity pb = be instanceof PeriscopeBlockEntity p ? p : null;
                    // The periscope's Redstone Module switches the storage off by signal
                    if (pb == null || pb.storageEnabled()) {
                        net.externals.add(new GolemNetwork.External(lvl, target, facing.getOpposite(),
                                pb == null ? 0 : pb.getPriority(), pos, pb));
                    }
                }
            }
        }
        return selfPipe;
    }

    /** Converts link targets to node indices in walk order (order decides who gets a channel first). */
    private static int[][] buildLinkTable(GolemNetwork net, List<List<GolemNetwork.Node>> linkedTo) {
        int[][] links = new int[net.nodeList.size()][];
        for (int i = 0; i < links.length; i++) {
            List<GolemNetwork.Node> targets = linkedTo.get(i);
            int[] idx = new int[targets.size()];
            int n = 0;
            for (GolemNetwork.Node t : targets) {
                Integer j = net.nodeIndex.get(t);
                if (j != null) {
                    idx[n++] = j;
                }
            }
            links[i] = n == idx.length ? idx : Arrays.copyOf(idx, n);
        }
        return links;
    }

    /** Resolves Source suppliers and Resonators adjacent to the network's nodes (each supplier counted once by owner). */
    private static void collectSources(GolemNetwork net, List<SourceCandidate> sourceCandidates) {
        Set<BlockEntity> seenSources = new HashSet<>();
        for (SourceCandidate cand : sourceCandidates) {
            GolemNetwork.Node candNode = new GolemNetwork.Node(cand.level, cand.pos);
            if (net.nodeIndex.containsKey(candNode)) {
                continue;
            }
            BlockEntity be = cand.level.getBlockEntity(cand.pos);
            if (be == null) {
                continue;
            }
            BlockEntity owner = SourceHelper.owner(be);
            if (seenSources.contains(owner)) {
                continue;
            }
            if (be instanceof EternalFrostResonatorBlockEntity) {
                // Resonator is not a Source supplier for the network; it powers Chronometers bound to it
                seenSources.add(owner);
                net.resonators++;
                net.peripherals.put(candNode, be.getBlockState().getBlock());
                continue;
            }
            SourceHelper.Supplier s = be instanceof CryoDiodeBlockEntity diode
                    ? diode.asSupplier(cand.fromSide)
                    : SourceHelper.asSupplier(be);
            if (s != null) {
                seenSources.add(owner);
                net.sources.add(s);
                net.peripherals.put(candNode, be.getBlockState().getBlock());
            }
        }
    }

    private static void configureCore(GolemNetwork net, List<GolemNetwork.Node> cores, int[][] links) {
        GolemNetwork.Node primary = primaryCore(net, cores);
        if (primary == null) {
            return;
        }
        net.coreLevel = primary.level();
        net.corePos = primary.pos();
        net.core = net.coreLevel.getBlockEntity(net.corePos) instanceof AtlasCoreBlockEntity c ? c : null;
        if (net.structure.valid()) {
            net.lineCapacity = GolemConfig.LINE_CHANNELS.get() + GolemConfig.EXPANDER_BONUS.get() * net.structure.expanders()
                    + GolemConfig.QUANTUM_BONUS.get() * net.structure.quantumExpanders();
            net.channelCapacity = net.lineCapacity * AtlasStructure.BUSHINGS;
        } else {
            net.tower = AtlasStructure.isTower(net.coreLevel, net.corePos);
            net.lineCapacity = net.tower ? GolemNetwork.TOWER_CHANNELS : GolemNetwork.BARE_CORE_CHANNELS;
            net.channelCapacity = net.lineCapacity;
        }
        allocateChannels(net, links, net.nodeIndex.get(primary));
        // Storages and providers without a channel stay silent, like terminals and buses
        net.bays.removeIf(b -> !net.activeDevices.contains(new GolemNetwork.Node(b.getLevel(), b.getBlockPos())));
        net.externals.removeIf(e -> !net.activeDevices.contains(new GolemNetwork.Node(e.level(), e.periscope())));
        net.providers.removeIf(p -> !net.activeDevices.contains(new GolemNetwork.Node(p.getLevel(), p.getBlockPos())));
        net.catalogs.removeIf(p -> !net.activeDevices.contains(new GolemNetwork.Node(p.getLevel(), p.getBlockPos())));
        // Two periscopes on one chest must not double its contents
        Set<GolemNetwork.Node> targets = new HashSet<>();
        net.externals.removeIf(e -> !targets.add(new GolemNetwork.Node(e.level(), e.target())));
    }

    /**
     * The core that runs the network when there are several: a complete array before a tower, then the lowest position (by dimension
     * name, then position). The choice does not depend on where the walk started, so every device agrees on it; the other cores stay idle.
     */
    @Nullable
    private static GolemNetwork.Node primaryCore(GolemNetwork net, List<GolemNetwork.Node> cores) {
        if (cores.isEmpty()) {
            return null;
        }
        if (cores.size() == 1) {
            GolemNetwork.Node only = cores.get(0);
            net.structure = AtlasStructure.check(only.level(), only.pos());
            return only;
        }
        GolemNetwork.Node best = null;
        Comparator<GolemNetwork.Node> order = Comparator
                .<GolemNetwork.Node, String>comparing(n -> n.level().dimension().location().toString())
                .thenComparing(GolemNetwork.Node::pos);
        for (GolemNetwork.Node c : cores) {
            AtlasStructure.Result r = AtlasStructure.check(c.level(), c.pos());
            boolean better = best == null || (r.valid() && !net.structure.valid())
                    || (r.valid() == net.structure.valid() && order.compare(c, best) < 0);
            if (better) {
                best = c;
                net.structure = r;
            }
        }
        return best;
    }

    /** How many channels the outer face of a Channel Expander gives out (only in a complete array). */
    static int expanderExit(GolemNetwork net, ChannelExpanderBlock ex) {
        if (net.structure == null || !net.structure.valid()) {
            return 0;
        }
        return ex.isQuantum() ? GolemConfig.QUANTUM_EXIT.get() : GolemConfig.EXPANDER_EXIT.get();
    }

    /** The node is one of the array cells (relative to the core on top). */
    private static boolean inArray(GolemNetwork net, GolemNetwork.Node n) {
        if (n.level() != net.coreLevel) {
            return false;
        }
        BlockPos pos = n.pos();
        int dy = net.corePos.getY() - pos.getY();
        return Math.abs(pos.getX() - net.corePos.getX()) <= 1
                && Math.abs(pos.getZ() - net.corePos.getZ()) <= 1
                && dy >= 0 && dy <= 3;
    }

    /**
     * Hands out the channels (see {@link ChannelCalculation}). The controller is the core with the conduits of a complete array,
     * or, in a starter tower, with the conduits joined to it; its neighbours (the bushings, the expanders) are the exits. Channels
     * never pass from one exit to another through the array, so every line counts only its own devices.
     */
    private static void allocateChannels(GolemNetwork net, int[][] links, int coreIdx) {
        int n = net.nodeList.size();
        boolean formed = net.structure.valid();
        net.controller = new boolean[n];
        net.capacities = new int[n];
        int[] tiers = new int[n];
        boolean[] needs = new boolean[n];

        int[] controllerNodes = findControllerNodes(net, links, coreIdx, formed);

        int deviceChannels = GolemConfig.DEVICE_CHANNELS.get();
        for (int i = 0; i < n; i++) {
            BlockState state = net.nodeStates.get(i);
            Block b = state.getBlock();
            needs[i] = ((GolemNode) b).channelCost(state) > 0;
            if (needs[i]) {
                net.deviceCount++;
            }
            if (net.controller[i]) {
                net.capacities[i] = ChannelCalculation.UNLIMITED;
                tiers[i] = ChannelCalculation.TIER_DENSE;
            } else if (b instanceof AtlasCoreBlock) {
                // Another core is idle and carries nothing
                net.capacities[i] = 0;
                tiers[i] = ChannelCalculation.TIER_DEVICE;
            } else if (b instanceof CableBushingBlock || b instanceof AtlasConduitBlock) {
                net.capacities[i] = net.lineCapacity;
                tiers[i] = ChannelCalculation.TIER_DENSE;
            } else if (b instanceof ChannelExpanderBlock ex) {
                net.capacities[i] = expanderExit(net, ex);
                tiers[i] = ChannelCalculation.TIER_DENSE;
            } else if (b instanceof SnowPipeBlock pipe) {
                net.capacities[i] = pipe.channelCapacity();
                tiers[i] = pipe.isDense() ? ChannelCalculation.TIER_DENSE : ChannelCalculation.TIER_PIPE;
            } else if (b instanceof TelegraphBlock) {
                net.capacities[i] = TelegraphBlock.CHANNELS;
                tiers[i] = ChannelCalculation.TIER_DENSE;
            } else {
                net.capacities[i] = deviceChannels;
                tiers[i] = ChannelCalculation.TIER_DEVICE;
            }
        }
        net.channels = new ChannelCalculation(links, net.capacities, tiers, needs, controllerNodes, net.channelCapacity);
        net.channelsUsed = net.channels.used();
        for (int i = 0; i < n; i++) {
            if (net.channels.granted(i)) {
                net.activeDevices.add(net.nodeList.get(i));
            }
        }
    }

    /** Controller nodes in walk order from the core: all array cells when formed, or only the conduits of a tower. */
    private static int[] findControllerNodes(GolemNetwork net, int[][] links, int coreIdx, boolean formed) {
        int n = net.nodeList.size();
        int[] controllerNodes = new int[n];
        int controllerCount = 0;
        boolean[] seen = new boolean[n];
        int[] queue = new int[n];
        int qHead = 0, qTail = 0;
        queue[qTail++] = coreIdx;
        seen[coreIdx] = true;
        net.controller[coreIdx] = true;
        while (qHead < qTail) {
            int i = queue[qHead++];
            if (net.controller[i]) {
                controllerNodes[controllerCount++] = i;
            }
            for (int j : links[i]) {
                Block b = net.nodeStates.get(j).getBlock();
                boolean conduit = b instanceof AtlasConduitBlock && net.nodeList.get(j).level() == net.coreLevel;
                boolean inside = formed ? inArray(net, net.nodeList.get(j)) && b instanceof AtlasPart && !(b instanceof AtlasCoreBlock) : conduit;
                if (!seen[j] && inside) {
                    seen[j] = true;
                    net.controller[j] = conduit;
                    queue[qTail++] = j;
                }
            }
        }
        return Arrays.copyOf(controllerNodes, controllerCount);
    }

    /**
     * Turns the pipe, cabinet and terminal animations on/off (the active property): the network is powered and, for devices that need
     * a channel, they got one; pipes and other carriers light up when the walk from the core reaches them. The calculator sets its
     * own flag (it means "a job is running").
     */
    static void syncActiveVisuals(GolemNetwork net, boolean active) {
        for (int i = 0; i < net.nodeList.size(); i++) {
            GolemNetwork.Node n = net.nodeList.get(i);
            if (!n.level().isLoaded(n.pos())) {
                continue;
            }
            BlockState state = n.level().getBlockState(n.pos());
            if (state.getBlock() instanceof SnowPipeBlock pipe && SnowPipeBlock.hasLamp(state, n.pos())
                    && n.level().getBlockEntity(n.pos()) instanceof PipeBlockEntity be) {
                // A heat lamp on the pipe reads the channels running through it
                be.setLamp(active && net.channels != null ? net.channels.load(i) : 0, pipe.channelCapacity());
            }
            if (!state.hasProperty(GolemProperties.ACTIVE) || !(state.getBlock() instanceof GolemNode node)
                    || state.getBlock() instanceof CryoCalculatorBlock) {
                continue;
            }
            boolean on = active && net.channels != null && (node.channelCost(state) > 0 ? net.channels.granted(i) : net.channels.reached(i));
            if (state.getValue(GolemProperties.ACTIVE) != on) {
                n.level().setBlock(n.pos(), state.setValue(GolemProperties.ACTIVE, on), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }
}
