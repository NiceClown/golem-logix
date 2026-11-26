package com.golemlogix.blockentity;

import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModSounds;
import com.golemlogix.items.ForgeHammerItem;
import com.golemlogix.secret.SecretKnowledge;
import com.golemlogix.system.AutoOutput;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.NbtBridge;
import com.golemlogix.system.SaturatorStructure;
import com.golemlogix.system.SourceHelper;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mana Saturator, a conveyor of three places: the left back deck (queue of blanks), the anvil (the iron golem forges) and
 * the right back deck (finished plates). Two copper golems carry independently, each through three zones: it waits at the
 * front corner of its own deck (the waiting zone), hops onto the anvil only for a hand-off (the forging zone: the taker in for the
 * result as the last strike falls, the bringer from the other side as it takes it) and walks back along the deck (the exit). The bringer takes the next blank in advance and waits
 * with it; the taker waits on its corner while the ritual runs and hops in for the result right after the last strike - so the
 * smith is almost never idle and the golems no longer crowd round the anvil on a long order.
 */
public class ManaSaturatorBlockEntity extends BlockEntity {
    /** Anvil: the blank under the hammer or the freshly forged result (the taker will pick it up). */
    private ItemStack heldItem = ItemStack.EMPTY;
    /** Left deck: queue of blanks (one kind, up to DECK_MAX). */
    private ItemStack inDeck = ItemStack.EMPTY;
    /** Right deck: finished plates (one kind, up to a stack). */
    private ItemStack outDeck = ItemStack.EMPTY;
    public static final int DECK_MAX = 4;
    /**
     * Ritual progress in "work units": +10 per tick plus +4 for every Cooling Co-Processor on the roof. A plate takes MAX_PROGRESS
     * (5 s without coolers, ~1.7 s with five); a Source Gem takes half the time of the Ars Nouveau Imbuement Chamber (see
     * {@link #ritualWork}).
     */
    private int ritualProgress = 0;
    public static final int MAX_PROGRESS = 1000;
    public static final int BASE_SPEED = 10;
    public static final int SPEED_PER_COOLER = 4;
    private boolean isCrafting = false;
    private int coolers = 0;
    /** Redstone Module (installed by right-clicking the machine) and its mode. */
    private boolean redstoneModule = false;
    /** Auto-output (on by default; the Forge Hammer switches it): finished items leave the right deck by themselves. */
    private boolean autoOutput = true;
    private final RedstoneControl redstone = new RedstoneControl();
    /** Internal Source buffer: refilled from the network (or neighbouring jars) and spent on saturation. */
    public static final int BUFFER_MAX = 5000;
    /** Source pulled every 5 ticks (times 1 + coolers): 600 a second covers even a Source Gem block (2660 Source in 5 s). */
    private static final int PULL_STEP = 150;
    private int source = 0;
    /** Fill level last sent to clients (in eighths), so nothing extra is sent. */
    private int syncedLevel = 0;

    // ---------------------------------------------------------------------------------- carriers
    /** How many ticks a copper golem walks from its deck to the anvil (and back). */
    public static final int WALK = 18;
    /** Ticks from the deck to the waiting corner (WAIT_AT of the path) and the hop from there onto the anvil. */
    public static final float WAIT_AT = 0.71f;
    public static final int TO_WAIT_TICKS = Math.round(WALK * WAIT_AT), HOP_TICKS = WALK - TO_WAIT_TICKS;
    /**
     * Carrier phases: home, walking the whole path to the anvil (old saves only), at the anvil, walking the whole path back to
     * the deck; walking to the waiting corner, waiting there, hopping from it onto the anvil, going home from it empty-handed.
     */
    public static final int HOME = 0, TO_ANVIL = 1, AT_ANVIL = 2, TO_DECK = 3, TO_WAIT = 4, AT_WAIT = 5, HOP = 6, BACK = 7;

    /** Copper carrier golem: what it holds, its phase and how many ticks of walking are left. */
    public static final class Carrier {
        public ItemStack hands = ItemStack.EMPTY;
        public int phase = HOME, timer = 0;

        void go(int newPhase) {
            phase = newPhase;
            timer = switch (newPhase) {
                case TO_ANVIL, TO_DECK -> WALK;
                case TO_WAIT, BACK -> TO_WAIT_TICKS;
                case HOP -> HOP_TICKS;
                default -> 0;
            };
        }

        /** Walking (the timer counts the steps down on both sides). */
        public boolean walking() {
            return phase == TO_ANVIL || phase == TO_DECK || phase == TO_WAIT || phase == HOP || phase == BACK;
        }

        /** On the anvil or on the way onto it. */
        boolean onAnvil() {
            return phase == TO_ANVIL || phase == HOP || phase == AT_ANVIL;
        }

        void tickClient() {
            if (walking() && timer > 0) {
                timer--;
            }
        }

        CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag t = new CompoundTag();
            t.putInt("Phase", phase);
            t.putInt("Timer", timer);
            if (!hands.isEmpty()) {
                t.put("Hands", NbtBridge.saveItem(hands, registries));
            }
            return t;
        }

        void load(CompoundTag t, HolderLookup.Provider registries) {
            phase = t.getIntOr("Phase", 0);
            timer = t.getIntOr("Timer", 0);
            hands = t.contains("Hands")
                    ? NbtBridge.loadItem(registries, t.getCompoundOrEmpty("Hands")).orElse(ItemStack.EMPTY)
                    : ItemStack.EMPTY;
        }
    }

    /** Bringer (left deck -> anvil) and taker (anvil -> right deck). */
    public final Carrier bringer = new Carrier(), taker = new Carrier();

    public ManaSaturatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MANA_SATURATOR.get(), pos, state);
    }

    /** What is on the anvil. */
    public ItemStack getHeldItem() {
        return heldItem;
    }

    /** Queue of blanks on the left deck. */
    public ItemStack inputDeck() {
        return inDeck;
    }

    /** Finished plates on the right deck. */
    public ItemStack outputDeck() {
        return outDeck;
    }

    private final SnapshotJournal<ItemStack[]> deckJournal = new SnapshotJournal<>() {
        @Override
        protected ItemStack[] createSnapshot() {
            return new ItemStack[]{inDeck.copy(), outDeck.copy()};
        }

        @Override
        protected void revertToSnapshot(ItemStack[] snapshot) {
            inDeck = snapshot[0];
            outDeck = snapshot[1];
        }

        @Override
        protected void onRootCommit(ItemStack[] originalState) {
            changed();
        }
    };

    /**
     * For buses, hoppers and pattern providers: slot 0 is the left deck (insert blanks only, up to DECK_MAX of one
     * kind), slot 1 is the right deck (take finished items only).
     */
    private final ResourceHandler<ItemResource> automationResources = new ResourceHandler<>() {
        @Override
        public int size() {
            return 2;
        }

        @Override
        public ItemResource getResource(int index) {
            Objects.checkIndex(index, 2);
            return ItemResource.of(index == 0 ? inDeck : outDeck);
        }

        @Override
        public long getAmountAsLong(int index) {
            Objects.checkIndex(index, 2);
            return (index == 0 ? inDeck : outDeck).getCount();
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            Objects.checkIndex(index, 2);
            if (index == 0) {
                return DECK_MAX;
            }
            return resource.isEmpty() ? 64 : Math.min(64, resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            Objects.checkIndex(index, 2);
            return index == 0 && !resource.isEmpty() && isInput(level, resource.toStack(1));
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
            Objects.checkIndex(index, 2);
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (index != 0 || amount <= 0 || !formed()) {
                return 0;
            }
            ItemStack probe = resource.toStack(1);
            if (!isInput(level, probe)) {
                return 0;
            }
            int n = Math.min(roomInDeck(probe), amount);
            if (n <= 0) {
                return 0;
            }
            deckJournal.updateSnapshots(tx);
            if (inDeck.isEmpty()) {
                inDeck = resource.toStack(n);
            } else {
                inDeck.grow(n);
            }
            return n;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
            Objects.checkIndex(index, 2);
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (index != 1 || amount <= 0 || outDeck.isEmpty() || !resource.matches(outDeck)) {
                return 0;
            }
            int n = Math.min(amount, outDeck.getCount());
            if (n <= 0) {
                return 0;
            }
            deckJournal.updateSnapshots(tx);
            outDeck.shrink(n);
            if (outDeck.isEmpty()) {
                outDeck = ItemStack.EMPTY;
            }
            return n;
        }
    };

    public ResourceHandler<ItemResource> automationResources() {
        return automationResources;
    }

    private boolean formed() {
        BlockState state = getBlockState();
        return state.hasProperty(ManaSaturatorBlock.FORMED) && state.getValue(ManaSaturatorBlock.FORMED);
    }

    /** How many more of these blanks fit on the left deck. */
    public int roomInDeck(ItemStack stack) {
        int cap = Math.min(DECK_MAX, Math.max(1, stack.getMaxStackSize()));
        if (inDeck.isEmpty()) {
            return cap;
        }
        return ItemStack.isSameItemSameComponents(inDeck, stack) ? Math.max(0, cap - inDeck.getCount()) : 0;
    }

    /** Put blanks on the left deck (check room with roomInDeck). */
    public void addToDeck(ItemStack stack) {
        if (inDeck.isEmpty()) {
            inDeck = stack.copy();
        } else {
            inDeck.grow(stack.getCount());
        }
        changed();
    }

    /** Take from the decks: finished items first, otherwise the queue of blanks (by a player's hand). */
    public ItemStack takeFromDecks() {
        ItemStack out;
        if (!outDeck.isEmpty()) {
            out = outDeck;
            outDeck = ItemStack.EMPTY;
        } else {
            out = inDeck;
            inDeck = ItemStack.EMPTY;
        }
        if (!out.isEmpty()) {
            changed();
        }
        return out;
    }

    /** Put straight onto the anvil (DevShots scenes and old worlds); ritual progress is reset. */
    public void setHeldItem(ItemStack stack) {
        this.heldItem = stack;
        this.ritualProgress = 0;
        this.isCrafting = false;
        changed();
    }

    /** Everything inside the machine and in the carriers' hands (dropped when it is broken). */
    public List<ItemStack> allItems() {
        List<ItemStack> out = new ArrayList<>(5);
        addIfNonEmpty(out, inDeck);
        addIfNonEmpty(out, heldItem);
        addIfNonEmpty(out, outDeck);
        addIfNonEmpty(out, bringer.hands);
        addIfNonEmpty(out, taker.hands);
        return out;
    }

    private static void addIfNonEmpty(List<ItemStack> out, ItemStack stack) {
        if (!stack.isEmpty()) {
            out.add(stack.copy());
        }
    }

    // ------------------------------------------------------------------ forging by hand (the anvil before the machine is built)
    /**
     * Forging by hand on the anvil of an unassembled saturator (an intermediate stage, the machine is its upgrade): a blank is put
     * on the face with right-click and struck with the Forge Hammer (left-click); every strike spends Source from the hammer's charge
     * and a little food; after MANUAL_HITS strikes the blank becomes a plate, just one (the machine gives two). ONLY the three base plates can be forged by hand.
     */
    public static final int MANUAL_HITS = 4, MANUAL_COOLDOWN = 6;
    private int manualHits = 0;
    private long lastStrike = -100;

    public enum Strike { HIT, DONE, COOLDOWN, NO_SOURCE, NOTHING }

    /** What forging by hand gives: the "hand_result" of the blank's Saturator recipe (data packs; by default the three base plates). */
    public static ItemStack manualOutputFor(@Nullable Level level, ItemStack input) {
        return SaturatorRecipes.manualOutputFor(level, input);
    }

    public static boolean isManualInput(@Nullable Level level, ItemStack stack) {
        return SaturatorRecipes.isManualInput(level, stack);
    }

    public int manualHits() {
        return manualHits;
    }

    public ItemStack anvilItem() {
        return heldItem;
    }

    public void placeOnAnvil(ItemStack one) {
        heldItem = one.copyWithCount(1);
        manualHits = 0;
        changed();
    }

    public ItemStack takeFromAnvil() {
        ItemStack s = heldItem;
        heldItem = ItemStack.EMPTY;
        manualHits = 0;
        changed();
        return s;
    }

    /** A hammer strike on the blank on the anvil (server). */
    public Strike strike(ServerLevel level, BlockPos pos, Player player, ItemStack hammer) {
        if (!isManualInput(level, heldItem)) {
            return Strike.NOTHING;
        }
        long now = level.getGameTime();
        if (now - lastStrike < MANUAL_COOLDOWN) {
            return Strike.COOLDOWN;
        }
        if (!ForgeHammerItem.canStrike(hammer)) {
            return Strike.NO_SOURCE;
        }
        ForgeHammerItem.spendStrike(hammer);
        lastStrike = now;
        manualHits++;
        player.causeFoodExhaustion(0.3f);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.97, z = pos.getZ() + 0.5;
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4f, 1.2f + manualHits * 0.06f);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.7f, 1.5f);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 3, 0.15, 0.03, 0.15, 0.1);
        level.sendParticles(ParticleTypes.CRIT, x, y, z, 2, 0.1, 0.02, 0.1, 0.15);
        if (manualHits >= MANUAL_HITS) {
            heldItem = manualOutputFor(level, heldItem);
            manualHits = 0;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 3, 0.15, 0.05, 0.15, 0.02);
            level.playSound(null, pos, ModSounds.ANVIL_CRUSH.get(), SoundSource.BLOCKS, 0.9f, 1.5f);
            changed();
            // the first plate made on an anvil opens the Dispel method
            if (player instanceof ServerPlayer sp) {
                SecretKnowledge.plateMade(sp);
            }
            return Strike.DONE;
        }
        changed();
        return Strike.HIT;
    }

    private void changed() {
        setChanged();
        BlockEntities.sendUpdate(this);
    }

    public float getProgressFraction() {
        return Math.min(1f, (float) ritualProgress / (float) currentWork());
    }

    /** Work units of the ritual on the anvil now. */
    public int currentWork() {
        return ritualWork(level, heldItem);
    }

    /** Work units per tick (counting the coprocessors on the roof). */
    public int speed() {
        return BASE_SPEED + SPEED_PER_COOLER * coolers;
    }

    public int getCoolers() {
        return coolers;
    }

    public boolean hasRedstoneModule() {
        return redstoneModule;
    }

    public RedstoneMode redstoneMode() {
        return redstone.mode();
    }

    /** Right-click with a Redstone Module: install it (true) or, if it is already there, cycle its mode (false). */
    public boolean installOrCycleRedstone() {
        boolean installed = !redstoneModule;
        if (installed) {
            redstoneModule = true;
            redstone.setMode(RedstoneMode.HIGH);
        } else {
            redstone.setMode(redstone.mode().next(true));
        }
        changed();
        return installed;
    }

    public boolean autoOutput() {
        return autoOutput;
    }

    /** Right-click with the Forge Hammer: auto-output on or off; returns the new state. */
    public boolean toggleAutoOutput() {
        autoOutput = !autoOutput;
        setChanged();
        return autoOutput;
    }

    /** Remove the Redstone Module (Shift + right-click with an empty hand); true if it was installed. */
    public boolean removeRedstoneModule() {
        if (!redstoneModule) {
            return false;
        }
        redstoneModule = false;
        changed();
        return true;
    }

    /** Whether any cell of the machine gets a redstone signal (iterates cell positions without allocating a list). */
    private static boolean powered(Level level, BlockPos pos, Direction facing) {
        for (int i = 0; i < SaturatorStructure.CELLS; i++) {
            BlockPos p = SaturatorStructure.cellPos(pos, facing, i);
            if (level.isLoaded(p) && level.hasNeighborSignal(p)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCrafting() {
        return isCrafting;
    }

    public int getSource() {
        return source;
    }

    /** Bowl fill level from 0 to 1. */
    public float getSourceFraction() {
        return Math.min(1f, (float) source / BUFFER_MAX);
    }

    private int levelEighths() {
        return source <= 0 ? 0 : 1 + Math.min(7, source * 8 / (BUFFER_MAX + 1));
    }

    /** The network connected to any loaded socket of the machine (one that has a core), otherwise the first loaded socket's network. */
    @Nullable
    public static GolemNetwork network(Level level, BlockPos controller, Direction facing) {
        GolemNetwork first = null;
        for (int port : SaturatorStructure.PORTS) {
            BlockPos portPos = SaturatorStructure.cellPos(controller, facing, port);
            if (!level.isLoaded(portPos)) {
                continue;
            }
            GolemNetwork net = GolemNetwork.get(level, portPos);
            if (net.hasCore()) {
                return net;
            }
            if (first == null) {
                first = net;
            }
        }
        return first;
    }

    /** Refills the buffer: from the network through any socket (if it has a core), otherwise from the jars around the machine. */
    private void refill(ServerLevel serverLevel, BlockPos pos, Direction facing) {
        if (source >= BUFFER_MAX) {
            return;
        }
        // the pull grows with the speed-up: every cooler speeds up the ritual, so Source is spent faster too
        int want = Math.min(PULL_STEP * (1 + coolers), BUFFER_MAX - source);
        int got = 0;
        GolemNetwork net = network(serverLevel, pos, facing);
        if (!SourceHelper.sourceRequired()) {
            // without Ars Nouveau there is nowhere to take Source from: the machine works for free (see SourceHelper.sourceRequired)
            got = want;
        } else if (net != null && net.hasCore() && net.isOnline()) {
            // taking from the network is all or nothing: if there is not that much, take smaller portions (whatever there is)
            for (int ask = want; ask >= 10 && got == 0; ask /= 2) {
                if (net.consumeSource(ask)) {
                    got = ask;
                }
            }
        } else {
            got = SourceHelper.drainAround(serverLevel, SaturatorStructure.cells(pos, facing), want);
        }
        if (got > 0) {
            source += got;
            setChanged();
        }
    }

    /** True when the quantized bowl fill level changed since the last client sync. */
    private boolean levelChanged() {
        int lv = levelEighths();
        if (lv != syncedLevel) {
            syncedLevel = lv;
            return true;
        }
        return false;
    }

    private static boolean allCellsLoaded(Level level, BlockPos pos, Direction facing) {
        return BlockEntities.allLoaded(level, SaturatorStructure.CELLS, i -> SaturatorStructure.cellPos(pos, facing, i));
    }

    /** Assembles the machine when the blocks around it stand correctly. */
    private void tryForm(ServerLevel serverLevel, BlockPos pos, Direction facing) {
        if (SaturatorStructure.check(serverLevel, pos, facing).valid()) {
            SaturatorStructure.form(serverLevel, pos, facing);
        }
    }

    // ------------------------------------------------------------------ carrier step

    /** Advances bringer (left deck -> anvil): takes a blank in advance and waits on its corner until hand-off. */
    private boolean stepBringer() {
        return switch (bringer.phase) {
            case HOME -> {
                if (inDeck.isEmpty()) {
                    yield false;
                }
                bringer.hands = inDeck.split(1);
                if (inDeck.isEmpty()) {
                    inDeck = ItemStack.EMPTY;
                }
                bringer.go(TO_WAIT);
                yield true;
            }
            case TO_WAIT -> {
                if (--bringer.timer <= 0) {
                    bringer.go(AT_WAIT);
                    yield true;
                }
                yield false;
            }
            case AT_WAIT -> {
                // the anvil is free, or the taker is on its way in for what lies there: a hand-off from both sides
                if (heldItem.isEmpty() || taker.onAnvil()) {
                    bringer.go(HOP);
                    yield true;
                }
                yield false;
            }
            case TO_ANVIL, HOP -> {
                if (--bringer.timer <= 0) {
                    bringer.go(AT_ANVIL);
                    yield true;
                }
                yield false;
            }
            case AT_ANVIL -> {
                if (heldItem.isEmpty()) {
                    heldItem = bringer.hands;
                    bringer.hands = ItemStack.EMPTY;
                    ritualProgress = 0;
                    isCrafting = false;
                    bringer.go(TO_DECK);
                    yield true;
                }
                yield false;
            }
            default -> {
                if (--bringer.timer <= 0) {
                    bringer.go(HOME);
                    yield true;
                }
                yield false;
            }
        };
    }

    /** Advances taker (anvil -> right deck): waits on its corner during a ritual and hops in as the last strike falls. */
    private boolean stepTaker(@Nullable SaturatorRecipes.Info heldRecipe) {
        boolean heldIsInput = heldRecipe != null;
        boolean result = !heldItem.isEmpty() && !heldIsInput;
        int work = heldRecipe != null ? heldRecipe.work() : MAX_PROGRESS;
        return switch (taker.phase) {
            case HOME -> {
                if (isCrafting || result) {
                    taker.go(TO_WAIT);
                    yield true;
                }
                yield false;
            }
            case TO_WAIT -> {
                if (--taker.timer <= 0) {
                    taker.go(AT_WAIT);
                    yield true;
                }
                yield false;
            }
            case AT_WAIT -> {
                boolean nearlyDone = isCrafting && (work - ritualProgress) <= HOP_TICKS * speed();
                if ((result || nearlyDone) && !(bringer.onAnvil() && heldItem.isEmpty())) {
                    taker.go(HOP);
                    yield true;
                }
                if (!isCrafting && !result) {
                    // nothing coming (the anvil was emptied by hand): home from the corner
                    taker.go(BACK);
                    yield true;
                }
                yield false;
            }
            case BACK -> {
                if (--taker.timer <= 0) {
                    taker.go(HOME);
                    yield true;
                }
                yield false;
            }
            case TO_ANVIL, HOP -> {
                if (--taker.timer <= 0) {
                    taker.go(AT_ANVIL);
                    yield true;
                }
                yield false;
            }
            case AT_ANVIL -> {
                if (result) {
                    taker.hands = heldItem;
                    heldItem = ItemStack.EMPTY;
                    taker.go(TO_DECK);
                    yield true;
                }
                if (!isCrafting && (heldItem.isEmpty() || !heldIsInput)) {
                    // nothing to take (the result was taken by hand): go home empty-handed
                    taker.go(TO_DECK);
                    yield true;
                }
                yield false;
            }
            default -> {
                if (taker.timer > 0) {
                    taker.timer--;
                    yield false;
                }
                if (taker.hands.isEmpty()) {
                    taker.go(HOME);
                    yield true;
                }
                if (outDeck.isEmpty() || (ItemStack.isSameItemSameComponents(outDeck, taker.hands)
                        && outDeck.getCount() + taker.hands.getCount() <= outDeck.getMaxStackSize())) {
                    if (outDeck.isEmpty()) {
                        outDeck = taker.hands;
                    } else {
                        outDeck.grow(taker.hands.getCount());
                    }
                    taker.hands = ItemStack.EMPTY;
                    taker.go(HOME);
                    yield true;
                }
                yield false;
            }
        };
    }

    // ------------------------------------------------------------------ tick

    public static void tick(Level level, BlockPos pos, BlockState state, ManaSaturatorBlockEntity entity) {
        boolean formed = state.getValue(ManaSaturatorBlock.FORMED);
        if (!(level instanceof ServerLevel sl)) {
            if (formed) {
                SaturatorRecipes.Info recipe = SaturatorRecipes.resolve(level, entity.heldItem);
                entity.walkLamp(level, recipe);
                entity.clientTick(level, pos, recipe);
            }
            return;
        }
        long t = level.getGameTime() + pos.asLong();
        Direction facing = state.getValue(ManaSaturatorBlock.FACING);
        if (!formed) {
            entity.tickUnformed(sl, pos, facing, t);
            return;
        }
        if (t % 40 == 0 && allCellsLoaded(level, pos, facing) && !SaturatorStructure.check(level, pos, facing).valid()) {
            SaturatorStructure.disassemble(level, pos, facing, null);
            return;
        }
        entity.tickFormed(sl, pos, state, facing, t);
    }

    private void tickUnformed(ServerLevel sl, BlockPos pos, Direction facing, long t) {
        if (t % 20 == 0) {
            tryForm(sl, pos, facing);
            if (sl.getBlockState(pos).getValue(ManaSaturatorBlock.FORMED)) {
                manualHits = 0;
            }
        }
        ritualProgress = 0;
        isCrafting = false;
    }

    private void tickFormed(ServerLevel sl, BlockPos pos, BlockState state, Direction facing, long t) {
        boolean dirty = false;
        boolean needSync = false;
        if (t % 5 == 0) {
            refill(sl, pos, facing);
        }
        if (t % 20 == 0) {
            int n = SaturatorStructure.coolers(sl, pos, facing);
            if (n != coolers) {
                coolers = n;
                dirty = true;
                needSync = true;
            }
        }
        boolean powered = false;
        if (redstoneModule) {
            powered = powered(sl, pos, facing);
            redstone.observe(powered);
        }
        needSync |= levelChanged();

        ItemStack heldBefore = heldItem;
        SaturatorRecipes.Info recipe = SaturatorRecipes.resolve(sl, heldBefore);
        boolean changed = stepBringer();
        if (heldItem != heldBefore) {
            heldBefore = heldItem;
            recipe = SaturatorRecipes.resolve(sl, heldItem);
        }
        changed |= stepTaker(recipe);
        if (heldItem != heldBefore) {
            recipe = SaturatorRecipes.resolve(sl, heldItem);
        }
        changed |= tickRitual(sl, pos, recipe, powered);
        changed |= tickAutoOutput(sl, pos, facing, t);

        if (changed) {
            dirty = true;
            needSync = true;
        }
        if (dirty) {
            setChanged();
        }
        if (needSync) {
            syncedLevel = levelEighths();
            sl.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private boolean tickRitual(ServerLevel sl, BlockPos pos, @Nullable SaturatorRecipes.Info recipe, boolean powered) {
        if (heldItem.isEmpty() || recipe == null) {
            if (ritualProgress != 0 || isCrafting) {
                ritualProgress = 0;
                isCrafting = false;
                return true;
            }
            return false;
        }
        boolean changed = false;
        int work = recipe.work();
        // start when the bowl has enough Source (and the Redstone Module allows it), spending Source up front
        if (!isCrafting && source >= recipe.source() && redstone.allows(redstoneModule, powered, true)) {
            source -= recipe.source();
            isCrafting = true;
            ritualProgress = 0;
            changed = true;
        }
        if (!isCrafting) {
            return changed;
        }
        int before = ritualProgress;
        ritualProgress = Math.min(work, ritualProgress + speed());
        if (before / 200 != ritualProgress / 200) {
            sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), ModSounds.VACUUM_HUM.get(), SoundSource.BLOCKS, 0.6f, 1.2f);
        }
        // the golem strikes the blank five times per ritual: clang and sparks
        int hitsBefore = (before * 5 + work / 4) / work;
        int hitsNow = (ritualProgress * 5 + work / 4) / work;
        if (hitsNow != hitsBefore && ritualProgress < work) {
            sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.BLOCKS, 0.5f, 1.3f);
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.getX() + 0.5, pos.getY() - 0.05, pos.getZ() + 0.5, 2, 0.12, 0.03, 0.12, 0.08);
            sl.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() - 0.05, pos.getZ() + 0.5, 2, 0.1, 0.02, 0.1, 0.12);
        }
        if (ritualProgress >= work) {
            completeRitual(sl, pos, recipe.output());
            changed = true;
        }
        return changed;
    }

    private void completeRitual(ServerLevel sl, BlockPos pos, ItemStack recipeOutput) {
        heldItem.shrink(1);
        if (!heldItem.isEmpty()) {
            // in old worlds a stack could lie on the anvil: drop extra results beside it
            Containers.dropItemStack(sl, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, recipeOutput.copy());
        } else {
            heldItem = recipeOutput.copy();
        }
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() - 0.05, pos.getZ() + 0.5, 6, 0.35, 0.05, 0.35, 0.03);
        sl.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 6, 0.4, 0.1, 0.4, 0.02);
        sl.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 3, 0.3, 0.05, 0.3, 0.03);
        sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), ModSounds.ANVIL_CRUSH.get(), SoundSource.BLOCKS, 1.0f, 1.4f);
        ritualProgress = 0;
        isCrafting = false;
    }

    /** Auto-output: pushes finished items to a neighbouring Pattern Provider first, otherwise any neighbouring inventory. */
    private boolean tickAutoOutput(ServerLevel sl, BlockPos pos, Direction facing, long t) {
        if (!autoOutput || outDeck.isEmpty() || t % AutoOutput.INTERVAL != 0) {
            return false;
        }
        ItemStack rest = AutoOutput.push(sl, SaturatorStructure.cells(pos, facing), outDeck);
        if (rest.getCount() != outDeck.getCount()) {
            outDeck = rest;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ client visuals

    /** Client: ritual progress between packets, sparks at the anvil, carrier steps (phase changes come from the server). */
    private void clientTick(Level level, BlockPos pos, @Nullable SaturatorRecipes.Info recipe) {
        bringer.tickClient();
        taker.tickClient();
        int work = recipe != null ? recipe.work() : MAX_PROGRESS;
        if (isCrafting && ritualProgress < work) {
            ritualProgress = Math.min(work, ritualProgress + speed());
        }
        // while it works: occasional small particle at the chamber edges, never obscuring the smith
        if (isCrafting && level.getGameTime() % 4 == 0) {
            var r = level.getRandom();
            double cx = pos.getX() + 0.5, cy = pos.getY() - 0.1, cz = pos.getZ() + 0.5;
            double side = r.nextBoolean() ? 0.42 : -0.42, depth = (r.nextDouble() - 0.5) * 0.7;
            switch (r.nextInt(4)) {
                case 0 -> level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, cx + side, cy, cz + depth, 0, 0.01, 0);
                case 1 -> level.addParticle(ParticleTypes.SNOWFLAKE, cx + side, cy + 0.5, cz + depth, 0, -0.02, 0);
                case 2 -> level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx + side * 0.8, cy + 0.05, cz + depth, 0, 0.02, 0);
                default -> level.addParticle(ParticleTypes.END_ROD, cx + side, cy - 0.3, cz + depth, 0, 0.015, 0);
            }
        }
    }

    /**
     * Client: copper golem with a lamp on the roof (above the beam, owner cell X 16..32, Z 0..16). When idle it walks in a circle
     * around the crystal base (19.5..28.5 x 3.5..12.5) over eight points; during a ritual or when Source is short it stands in front.
     */
    public final LampWalker walker = new LampWalker(24f, 1.8f);
    private static final float[][] RING = {
            {17.6f, 1.8f}, {24f, 1.8f}, {30.4f, 1.8f}, {30.4f, 8f},
            {30.4f, 14.2f}, {24f, 14.2f}, {17.6f, 14.2f}, {17.6f, 8f}
    };
    private final LampWalker.GoalPicker ringGoal = this::pickRingGoal;

    private void pickRingGoal(RandomSource random, float[] out) {
        // step clockwise or counterclockwise around the crystal so the golem never walks through it
        walker.ring = Math.floorMod(walker.ring + (random.nextInt(4) == 0 ? -1 : 1), RING.length);
        out[0] = RING[walker.ring][0];
        out[1] = RING[walker.ring][1];
    }

    private void walkLamp(Level level, @Nullable SaturatorRecipes.Info recipe) {
        walker.tick(lampState(recipe) == 0, 24f, 1.8f, level.getRandom(), ringGoal);
    }

    /** Roof lamp signal: 0 = idle, 1 = ritual running, 2 = a blank is waiting but Source is short. */
    public int lampState() {
        return lampState(SaturatorRecipes.resolve(level, heldItem));
    }

    private int lampState(@Nullable SaturatorRecipes.Info recipe) {
        if (isCrafting) {
            return 1;
        }
        return recipe != null && source < recipe.source() ? 2 : 0;
    }

    // ------------------------------------------------------------------ recipe lookups (public API)

    /** Whether the item can be pressed (otherwise clicking the machine with it is a normal block placement). */
    public static boolean isInput(@Nullable Level level, ItemStack stack) {
        return SaturatorRecipes.isInput(level, stack);
    }

    /**
     * What one ritual makes: the mod's own plates and parts, and, with Ars Nouveau, the Imbuement Chamber's Source Gem recipes
     * (amethyst, an amethyst block, lapis), read from the level's recipes.
     */
    public static ItemStack outputFor(@Nullable Level level, ItemStack input) {
        return SaturatorRecipes.outputFor(level, input);
    }

    /** Source of one ritual: as its recipe says (1000 for a plate, 2500 for the Occult Matrix), a Source Gem a third more than in the chamber. */
    public static int requiredSource(@Nullable Level level, ItemStack input) {
        return SaturatorRecipes.requiredSource(level, input);
    }

    /** Work units of one ritual: the recipe's ticks at the base speed (MAX_PROGRESS for the plates); a Source Gem takes half the chamber's ticks. */
    public static int ritualWork(@Nullable Level level, ItemStack input) {
        return SaturatorRecipes.ritualWork(level, input);
    }

    // ------------------------------------------------------------------ persistence

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (!heldItem.isEmpty()) {
            tag.put("HeldItem", NbtBridge.saveItem(heldItem, registries));
        }
        if (!inDeck.isEmpty()) {
            tag.put("InDeck", NbtBridge.saveItem(inDeck, registries));
        }
        if (!outDeck.isEmpty()) {
            tag.put("OutDeck", NbtBridge.saveItem(outDeck, registries));
        }
        tag.put("Bringer", bringer.save(registries));
        tag.put("Taker", taker.save(registries));
        tag.putInt("Progress", ritualProgress);
        tag.putInt("ManualHits", manualHits);
        tag.putInt("Source", source);
        tag.putBoolean("IsCrafting", isCrafting);
        tag.putInt("Coolers", coolers);
        tag.putBoolean("RedstoneModule", redstoneModule);
        tag.putBoolean("AutoOutput", autoOutput);
        redstone.save(tag);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        heldItem = readOptionalItem(tag, "HeldItem", registries);
        inDeck = readOptionalItem(tag, "InDeck", registries);
        outDeck = readOptionalItem(tag, "OutDeck", registries);
        bringer.load(tag.getCompoundOrEmpty("Bringer"), registries);
        taker.load(tag.getCompoundOrEmpty("Taker"), registries);
        ritualProgress = tag.getIntOr("Progress", 0);
        manualHits = tag.getIntOr("ManualHits", 0);
        source = tag.getIntOr("Source", 0);
        syncedLevel = levelEighths();
        isCrafting = tag.getBooleanOr("IsCrafting", false);
        coolers = tag.getIntOr("Coolers", 0);
        redstoneModule = tag.getBooleanOr("RedstoneModule", false);
        autoOutput = tag.getBooleanOr("AutoOutput", true);
        redstone.load(tag);
    }

    private static ItemStack readOptionalItem(CompoundTag tag, String key, HolderLookup.Provider registries) {
        return tag.contains(key)
                ? NbtBridge.loadItem(registries, tag.getCompoundOrEmpty(key)).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveTag(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        BlockEntities.save(this, output, this::saveTag);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        BlockEntities.load(input, this::loadTag);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        BlockEntities.onPreRemove(this, pos, state);
        if (level != null && !level.isClientSide()) {
            // clear dropped items so nothing can be re-dropped if preRemoveSideEffects or removal runs twice
            inDeck = ItemStack.EMPTY;
            heldItem = ItemStack.EMPTY;
            outDeck = ItemStack.EMPTY;
            bringer.hands = ItemStack.EMPTY;
            taker.hands = ItemStack.EMPTY;
            redstoneModule = false;
        }
    }
}
