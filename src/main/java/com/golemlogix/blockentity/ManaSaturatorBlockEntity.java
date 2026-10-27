package com.golemlogix.blockentity;

import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModItems;
import com.golemlogix.init.ModSounds;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.SaturatorStructure;
import com.golemlogix.system.SourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mana Saturator, a conveyor of three places: the left back deck (queue of blanks), the anvil (the iron golem forges) and
 * the right back deck (finished plates). Two copper golems carry independently: the bringer takes the next blank in advance
 * and waits at the anvil, the taker walks to the anvil while the ritual runs and picks up the result right after the last
 * strike, so the smith is almost never idle.
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
    private final com.golemlogix.upgrade.RedstoneControl redstone = new com.golemlogix.upgrade.RedstoneControl();
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
    /** Carrier phases: home, walking to the anvil, waiting at the anvil, walking to the deck. */
    public static final int HOME = 0, TO_ANVIL = 1, AT_ANVIL = 2, TO_DECK = 3;

    /** Copper carrier golem: what it holds, its phase and how many ticks of walking are left. */
    public static final class Carrier {
        public ItemStack hands = ItemStack.EMPTY;
        public int phase = HOME, timer = 0;

        void go(int newPhase) {
            phase = newPhase;
            timer = newPhase == TO_ANVIL || newPhase == TO_DECK ? WALK : 0;
        }

        CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag t = new CompoundTag();
            t.putInt("Phase", phase);
            t.putInt("Timer", timer);
            if (!hands.isEmpty()) {
                t.put("Hands", com.golemlogix.system.NbtBridge.saveItem(hands, registries));
            }
            return t;
        }

        void load(CompoundTag t, HolderLookup.Provider registries) {
            phase = t.getIntOr("Phase", 0);
            timer = t.getIntOr("Timer", 0);
            hands = t.contains("Hands") ? com.golemlogix.system.NbtBridge.loadItem(registries, t.getCompoundOrEmpty("Hands")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
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

    /**
     * For buses, hoppers and pattern providers: slot 0 is the left deck (insert blanks only, up to DECK_MAX of one
     * kind), slot 1 is the right deck (take finished items only).
     */
    private final net.neoforged.neoforge.items.IItemHandler automation = new net.neoforged.neoforge.items.IItemHandler() {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? inDeck : outDeck;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !isInput(level, stack) || !formed()) {
                return stack;
            }
            int room = roomInDeck(stack);
            if (room <= 0) {
                return stack;
            }
            int n = Math.min(room, stack.getCount());
            if (!simulate) {
                addToDeck(stack.copyWithCount(n));
            }
            return stack.copyWithCount(stack.getCount() - n);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 1 || amount <= 0 || outDeck.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int n = Math.min(amount, outDeck.getCount());
            ItemStack out = outDeck.copyWithCount(n);
            if (!simulate) {
                outDeck.shrink(n);
                if (outDeck.isEmpty()) {
                    outDeck = ItemStack.EMPTY;
                }
                changed();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? DECK_MAX : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && isInput(level, stack);
        }
    };

    public net.neoforged.neoforge.items.IItemHandler automation() {
        return automation;
    }

    /** What pipes and other mods see: the decks as a NeoForge resource handler. */
    private final net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> automationResources =
            com.golemlogix.system.LegacyResources.offerItems(automation, () -> new ItemStack[]{inDeck.copy(), outDeck.copy()}, s -> {
                inDeck = s[0];
                outDeck = s[1];
                changed();
            });

    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> automationResources() {
        return automationResources;
    }

    private boolean formed() {
        BlockState state = getBlockState();
        return state.hasProperty(ManaSaturatorBlock.FORMED) && state.getValue(ManaSaturatorBlock.FORMED);
    }

    /** How many more of these blanks fit on the left deck. */
    public int roomInDeck(ItemStack stack) {
        if (inDeck.isEmpty()) {
            return DECK_MAX;
        }
        return ItemStack.isSameItemSameComponents(inDeck, stack) ? DECK_MAX - inDeck.getCount() : 0;
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
    public java.util.List<ItemStack> allItems() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (ItemStack s : new ItemStack[]{inDeck, heldItem, outDeck, bringer.hands, taker.hands}) {
            if (!s.isEmpty()) {
                out.add(s.copy());
            }
        }
        return out;
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
    public static ItemStack manualOutputFor(@javax.annotation.Nullable Level level, ItemStack input) {
        com.golemlogix.recipe.SaturatorRecipe r = com.golemlogix.recipe.SaturatorRecipe.find(level, input);
        return r == null ? ItemStack.EMPTY : r.handResult().copy();
    }

    public static boolean isManualInput(@javax.annotation.Nullable Level level, ItemStack stack) {
        return !manualOutputFor(level, stack).isEmpty();
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
        if (!com.golemlogix.items.ForgeHammerItem.canStrike(hammer)) {
            return Strike.NO_SOURCE;
        }
        com.golemlogix.items.ForgeHammerItem.spendStrike(hammer);
        lastStrike = now;
        manualHits++;
        player.causeFoodExhaustion(0.3f);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.97, z = pos.getZ() + 0.5;
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4f, 1.2f + manualHits * 0.06f);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.7f, 1.5f);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 3, 0.15, 0.03, 0.15, 0.1);
        level.sendParticles(ParticleTypes.CRIT, x, y, z, 2, 0.1, 0.02, 0.1, 0.15);
        if (manualHits >= MANUAL_HITS) {
            heldItem = manualOutputFor(level, heldItem);
            manualHits = 0;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 3, 0.15, 0.05, 0.15, 0.02);
            level.playSound(null, pos, ModSounds.ANVIL_CRUSH.get(), SoundSource.BLOCKS, 0.9f, 1.5f);
            changed();
            // the first plate made on an anvil opens the Dispel method
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.golemlogix.secret.SecretKnowledge.plateMade(sp);
            }
            return Strike.DONE;
        }
        changed();
        return Strike.HIT;
    }
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
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

    public com.golemlogix.upgrade.RedstoneMode redstoneMode() {
        return redstone.mode();
    }

    /** Right-click with a Redstone Module: install it (true) or, if it is already there, cycle its mode (false). */
    public boolean installOrCycleRedstone() {
        boolean installed = !redstoneModule;
        if (installed) {
            redstoneModule = true;
            redstone.setMode(com.golemlogix.upgrade.RedstoneMode.HIGH);
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

    /** Whether any cell of the machine gets a redstone signal. */
    private boolean powered(Level level, BlockPos pos, Direction facing) {
        for (BlockPos p : SaturatorStructure.cells(pos, facing)) {
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

    /** The network connected to any socket of the machine (one that has a core), otherwise the network of the front socket. */
    public static GolemNetwork network(Level level, BlockPos controller, Direction facing) {
        GolemNetwork first = null;
        for (int port : SaturatorStructure.PORTS) {
            GolemNetwork net = GolemNetwork.get(level, SaturatorStructure.cellPos(controller, facing, port));
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
    private void refill(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        if (source >= BUFFER_MAX) {
            return;
        }
        // the pull grows with the speed-up: every cooler speeds up the ritual, so Source is spent faster too
        int want = Math.min(PULL_STEP * (1 + coolers), BUFFER_MAX - source);
        int got = 0;
        Direction facing = state.getValue(ManaSaturatorBlock.FACING);
        GolemNetwork net = network(serverLevel, pos, facing);
        if (!SourceHelper.sourceRequired()) {
            // without Ars Nouveau there is nowhere to take Source from: the machine works for free (see SourceHelper.sourceRequired)
            got = want;
        } else if (net.hasCore() && net.isOnline()) {
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

    private void syncLevel(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        int lv = levelEighths();
        if (lv != syncedLevel) {
            syncedLevel = lv;
            serverLevel.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /** Assembles the machine when the blocks around it stand correctly. */
    private void tryForm(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(ManaSaturatorBlock.FACING);
        if (SaturatorStructure.check(serverLevel, pos, facing).valid()) {
            SaturatorStructure.form(serverLevel, pos, facing);
        }
    }

    /**
     * Carrier step (server). Bringer: at home with a queue, it takes a blank and walks to the anvil, waits until the anvil
     * is free, puts the blank down and goes back. Taker: walks to the anvil as soon as a ritual or a finished result is there, takes
     * the result and carries it to the right deck (if foreign plates are there or there is no room, it waits with its load).
     */
    private boolean stepCarriers() {
        boolean changed = false;
        // bringer
        switch (bringer.phase) {
            case HOME -> {
                if (!inDeck.isEmpty()) {
                    bringer.hands = inDeck.split(1);
                    if (inDeck.isEmpty()) {
                        inDeck = ItemStack.EMPTY;
                    }
                    bringer.go(TO_ANVIL);
                    changed = true;
                }
            }
            case TO_ANVIL -> {
                if (--bringer.timer <= 0) {
                    bringer.go(AT_ANVIL);
                    changed = true;
                }
            }
            case AT_ANVIL -> {
                if (heldItem.isEmpty()) {
                    heldItem = bringer.hands;
                    bringer.hands = ItemStack.EMPTY;
                    ritualProgress = 0;
                    isCrafting = false;
                    bringer.go(TO_DECK);
                    changed = true;
                }
            }
            default -> {
                if (--bringer.timer <= 0) {
                    bringer.go(HOME);
                    changed = true;
                }
            }
        }
        // taker
        boolean result = !heldItem.isEmpty() && !isInput(level, heldItem);
        switch (taker.phase) {
            case HOME -> {
                if (isCrafting || result) {
                    taker.go(TO_ANVIL);
                    changed = true;
                }
            }
            case TO_ANVIL -> {
                if (--taker.timer <= 0) {
                    taker.go(AT_ANVIL);
                    changed = true;
                }
            }
            case AT_ANVIL -> {
                if (result) {
                    taker.hands = heldItem;
                    heldItem = ItemStack.EMPTY;
                    taker.go(TO_DECK);
                    changed = true;
                } else if (!isCrafting && (heldItem.isEmpty() || !isInput(level, heldItem))) {
                    // nothing to take (the result was taken by hand): go home empty-handed
                    taker.go(TO_DECK);
                    changed = true;
                }
            }
            default -> {
                if (taker.timer > 0) {
                    taker.timer--;
                } else if (taker.hands.isEmpty()) {
                    taker.go(HOME);
                    changed = true;
                } else if (outDeck.isEmpty() || ItemStack.isSameItemSameComponents(outDeck, taker.hands)
                        && outDeck.getCount() + taker.hands.getCount() <= outDeck.getMaxStackSize()) {
                    if (outDeck.isEmpty()) {
                        outDeck = taker.hands;
                    } else {
                        outDeck.grow(taker.hands.getCount());
                    }
                    taker.hands = ItemStack.EMPTY;
                    taker.go(HOME);
                    changed = true;
                }
            }
        }
        return changed;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ManaSaturatorBlockEntity entity) {
        boolean formed = state.getValue(ManaSaturatorBlock.FORMED);
        boolean powered = false;
        if (!(level instanceof ServerLevel sl)) {
            if (formed) {
                entity.walkLamp(level);
                entity.clientTick(level, pos);
            }
            return;
        }
        long t = level.getGameTime() + pos.asLong();
        if (!formed) {
            if (t % 20 == 0) {
                entity.tryForm(sl, pos, state);
                if (sl.getBlockState(pos).getValue(ManaSaturatorBlock.FORMED)) {
                    entity.manualHits = 0;
                }
            }
            entity.ritualProgress = 0;
            entity.isCrafting = false;
            return;
        }
        Direction facing = state.getValue(ManaSaturatorBlock.FACING);
        if (t % 40 == 0 && !SaturatorStructure.check(level, pos, facing).valid()) {
            SaturatorStructure.disassemble(level, pos, facing, null);
            return;
        }
        if (t % 5 == 0) {
            entity.refill(sl, pos, state);
        }
        if (t % 20 == 0) {
            int n = SaturatorStructure.coolers(level, pos, facing);
            if (n != entity.coolers) {
                entity.coolers = n;
                entity.setChanged();
                sl.sendBlockUpdated(pos, state, state, 3);
            }
        }
        if (entity.redstoneModule) {
            powered = entity.powered(level, pos, facing);
            entity.redstone.observe(powered);
        }
        entity.syncLevel(sl, pos, state);
        boolean changed = entity.stepCarriers();

        ItemStack held = entity.heldItem;
        if (held.isEmpty() || !isInput(level, held)) {
            entity.ritualProgress = 0;
            entity.isCrafting = false;
        } else {
            ItemStack recipeOutput = outputFor(level, held);
            int requiredSource = requiredSource(level, held);
            int work = ritualWork(level, held);
            // start when the bowl has enough Source (and the Redstone Module allows it), and spend it right away
            if (!entity.isCrafting && entity.source >= requiredSource && entity.redstone.allows(entity.redstoneModule, powered, true)) {
                entity.source -= requiredSource;
                entity.isCrafting = true;
                entity.ritualProgress = 0;
                changed = true;
            }
            if (entity.isCrafting) {
                int before = entity.ritualProgress;
                entity.ritualProgress = Math.min(work, entity.ritualProgress + entity.speed());
                // a hum every second of the ritual
                if (before / 200 != entity.ritualProgress / 200) {
                    sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), ModSounds.VACUUM_HUM.get(), SoundSource.BLOCKS, 0.6f, 1.2f);
                }
                // the golem strikes the blank five times per ritual: the clang of the strike and sparks
                int hitsBefore = (before * 5 + work / 4) / work;
                int hitsNow = (entity.ritualProgress * 5 + work / 4) / work;
                if (hitsNow != hitsBefore && entity.ritualProgress < work) {
                    sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), net.minecraft.sounds.SoundEvents.IRON_GOLEM_ATTACK,
                            SoundSource.BLOCKS, 0.5f, 1.3f);
                    sl.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.getX() + 0.5, pos.getY() - 0.05, pos.getZ() + 0.5,
                            2, 0.12, 0.03, 0.12, 0.08);
                    sl.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() - 0.05, pos.getZ() + 0.5, 2, 0.1, 0.02, 0.1, 0.12);
                }
                if (entity.ritualProgress >= work) {
                    held.shrink(1);
                    if (!held.isEmpty()) {
                        // in old worlds a stack could lie on the anvil: the extra results go next to it
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, recipeOutput.copy());
                    } else {
                        entity.heldItem = recipeOutput.copy();
                    }
                    // a plate is done: a short burst, low and wide, so the smith stays in sight
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() - 0.05, pos.getZ() + 0.5, 6, 0.35, 0.05, 0.35, 0.03);
                    sl.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 6, 0.4, 0.1, 0.4, 0.02);
                    sl.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 3, 0.3, 0.05, 0.3, 0.03);
                    sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), ModSounds.ANVIL_CRUSH.get(), SoundSource.BLOCKS, 1.0f, 1.4f);
                    entity.ritualProgress = 0;
                    entity.isCrafting = false;
                    changed = true;
                }
            }
        }
        // auto-output: the finished items go to a neighbouring Pattern Provider first, otherwise into any neighbouring inventory
        if (entity.autoOutput && !entity.outDeck.isEmpty() && t % com.golemlogix.system.AutoOutput.INTERVAL == 0) {
            ItemStack rest = com.golemlogix.system.AutoOutput.push(level, SaturatorStructure.cells(pos, facing), entity.outDeck);
            if (rest.getCount() != entity.outDeck.getCount()) {
                entity.outDeck = rest;
                changed = true;
            }
        }
        if (changed) {
            entity.changed();
        }
    }

    /** Client: ritual progress between packets, sparks at the anvil, carrier steps (phase changes come from the server). */
    private void clientTick(Level level, BlockPos pos) {
        for (Carrier c : new Carrier[]{bringer, taker}) {
            if ((c.phase == TO_ANVIL || c.phase == TO_DECK) && c.timer > 0) {
                c.timer--;
            }
        }
        int work = currentWork();
        if (isCrafting && ritualProgress < work) {
            ritualProgress = Math.min(work, ritualProgress + speed());
        }
        // while it works: now and then one small particle at the chamber's edges, never a cloud over the smith - a cold flame
        // licking the anvil's corner, a snowflake drifting down a corner post, a spark off the plate, a mote rising from the rod
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
    private static final float[][] RING = {{17.6f, 1.8f}, {24f, 1.8f}, {30.4f, 1.8f}, {30.4f, 8f}, {30.4f, 14.2f}, {24f, 14.2f},
            {17.6f, 14.2f}, {17.6f, 8f}};

    private void walkLamp(Level level) {
        var r = level.getRandom();
        walker.tick(lampState() == 0, 24f, 1.8f, r, () -> {
            // to the next point of the circle (clockwise or counterclockwise): the golem never walks straight through the crystal
            walker.ring = Math.floorMod(walker.ring + (r.nextInt(4) == 0 ? -1 : 1), RING.length);
            return RING[walker.ring];
        });
    }

    /** Roof lamp signal: 0 = idle, 1 = ritual running, 2 = a blank is waiting but Source is short. */
    public int lampState() {
        if (isCrafting) {
            return 1;
        }
        if (!heldItem.isEmpty() && isInput(level, heldItem) && source < requiredSource(level, heldItem)) {
            return 2;
        }
        return 0;
    }

    /** Whether the item can be pressed (otherwise clicking the machine with it is a normal block placement). */
    public static boolean isInput(@javax.annotation.Nullable Level level, ItemStack stack) {
        return !outputFor(level, stack).isEmpty();
    }

    /**
     * What one ritual makes: the mod's own plates and parts, and, with Ars Nouveau, the Imbuement Chamber's Source Gem recipes
     * (amethyst, an amethyst block, lapis), read from the level's recipes.
     */
    public static ItemStack outputFor(@javax.annotation.Nullable Level level, ItemStack input) {
        com.golemlogix.recipe.SaturatorRecipe own = com.golemlogix.recipe.SaturatorRecipe.find(level, input);
        if (own != null) {
            return own.result().copy();
        }
        com.golemlogix.integration.ars.ArsImbuement.Entry gem = com.golemlogix.integration.ars.ArsImbuement.find(level, input);
        return gem == null ? ItemStack.EMPTY : gem.output().copy();
    }

    /** Source of one ritual: as its recipe says (1000 for a plate, 2500 for the Occult Matrix), a Source Gem a third more than in the chamber. */
    public static int requiredSource(@javax.annotation.Nullable Level level, ItemStack input) {
        com.golemlogix.recipe.SaturatorRecipe own = com.golemlogix.recipe.SaturatorRecipe.find(level, input);
        if (own != null) {
            return own.source();
        }
        com.golemlogix.integration.ars.ArsImbuement.Entry gem = com.golemlogix.integration.ars.ArsImbuement.find(level, input);
        return gem != null ? gem.source() : com.golemlogix.recipe.SaturatorRecipe.DEFAULT_SOURCE;
    }

    /** Work units of one ritual: the recipe's ticks at the base speed (MAX_PROGRESS for the plates); a Source Gem takes half the chamber's ticks. */
    public static int ritualWork(@javax.annotation.Nullable Level level, ItemStack input) {
        com.golemlogix.recipe.SaturatorRecipe own = com.golemlogix.recipe.SaturatorRecipe.find(level, input);
        if (own != null) {
            return own.ticks() * BASE_SPEED;
        }
        if (!input.isEmpty()) {
            com.golemlogix.integration.ars.ArsImbuement.Entry gem = com.golemlogix.integration.ars.ArsImbuement.find(level, input);
            if (gem != null) {
                return gem.ticks() * BASE_SPEED;
            }
        }
        return MAX_PROGRESS;
    }

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (!heldItem.isEmpty()) {
            tag.put("HeldItem", com.golemlogix.system.NbtBridge.saveItem(heldItem, registries));
        }
        if (!inDeck.isEmpty()) {
            tag.put("InDeck", com.golemlogix.system.NbtBridge.saveItem(inDeck, registries));
        }
        if (!outDeck.isEmpty()) {
            tag.put("OutDeck", com.golemlogix.system.NbtBridge.saveItem(outDeck, registries));
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
        heldItem = tag.contains("HeldItem") ? com.golemlogix.system.NbtBridge.loadItem(registries, tag.getCompoundOrEmpty("HeldItem")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        inDeck = tag.contains("InDeck") ? com.golemlogix.system.NbtBridge.loadItem(registries, tag.getCompoundOrEmpty("InDeck")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        outDeck = tag.contains("OutDeck") ? com.golemlogix.system.NbtBridge.loadItem(registries, tag.getCompoundOrEmpty("OutDeck")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        bringer.load(tag.getCompoundOrEmpty("Bringer"), registries);
        taker.load(tag.getCompoundOrEmpty("Taker"), registries);
        this.ritualProgress = tag.getIntOr("Progress", 0);
        this.manualHits = tag.getIntOr("ManualHits", 0);
        this.source = tag.getIntOr("Source", 0);
        this.isCrafting = tag.getBooleanOr("IsCrafting", false);
        this.coolers = tag.getIntOr("Coolers", 0);
        this.redstoneModule = tag.getBooleanOr("RedstoneModule", false);
        this.autoOutput = tag.getBooleanOr("AutoOutput", true);
        redstone.load(tag);
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

    // ---- 1.21.6+: the game's ValueInput/ValueOutput over the CompoundTag code above

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag tag = new CompoundTag();
        saveTag(tag, com.golemlogix.system.NbtBridge.registries(this));
        com.golemlogix.system.NbtBridge.write(output, tag);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        loadTag(com.golemlogix.system.NbtBridge.read(input), input.lookup());
    }

    /** Since 1.21.5 the block's removal side effects run from here, while this entity and its contents are still in place. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && state.getBlock() instanceof com.golemlogix.blocks.RemovalAware r) {
            r.onRemoved(state, level, pos);
        }
    }
}
