package com.golemlogix.blockentity;

import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModSounds;
import com.golemlogix.items.ForgeHammerItem;
import com.golemlogix.secret.SecretKnowledge;
import com.golemlogix.system.AutoOutput;
import com.golemlogix.system.EndRodRitual;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.SaturatorStructure;
import com.golemlogix.system.SourceHelper;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

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

        void tickClient() {
            if ((phase == TO_ANVIL || phase == TO_DECK) && timer > 0) {
                timer--;
            }
        }

        CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag t = new CompoundTag();
            t.putInt("Phase", phase);
            t.putInt("Timer", timer);
            if (!hands.isEmpty()) {
                t.put("Hands", hands.save(registries));
            }
            return t;
        }

        void load(CompoundTag t, HolderLookup.Provider registries) {
            phase = t.getInt("Phase");
            timer = t.getInt("Timer");
            hands = t.contains("Hands") ? ItemStack.parse(registries, t.getCompound("Hands")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
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
    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? inDeck : slot == 1 ? outDeck : ItemStack.EMPTY;
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
            return slot == 0 ? DECK_MAX : slot == 1 ? 64 : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && isInput(level, stack);
        }
    };

    public IItemHandler automation() {
        return automation;
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
        List<ItemStack> out = new ArrayList<>(8);
        addIfNonEmpty(out, inDeck);
        addIfNonEmpty(out, heldItem);
        addIfNonEmpty(out, outDeck);
        addIfNonEmpty(out, bringer.hands);
        addIfNonEmpty(out, taker.hands);
        if (rodStage != EndRodRitual.NONE) {
            if (rodEyes > 0) {
                out.add(new ItemStack(Items.ENDER_EYE, rodEyes));
            }
            if (rodTears > 0) {
                out.add(new ItemStack(Items.GHAST_TEAR, rodTears));
            }
            if (rodNetherite > 0) {
                out.add(new ItemStack(Items.NETHERITE_INGOT, rodNetherite));
            }
        }
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
     * Also hosts the 3-phase Cryo-Forging Ritual ({@link EndRodRitual}) that turns a Diamond Block + reagents + surrounding Blue Ice into an End Rod.
     */
    public static final int MANUAL_HITS = 4, MANUAL_COOLDOWN = 6;
    private int manualHits = 0;
    private long lastStrike = -100;
    private int rodStage = EndRodRitual.NONE;
    private int rodEyes = 0;
    private int rodTears = 0;
    private int rodNetherite = 0;

    public enum Strike { HIT, DONE, COOLDOWN, NO_SOURCE, NOTHING, NEED_ICE, WAIT_REAGENT, ROD_PHASE_DONE, ROD_DONE }

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

    public int rodStage() {
        return rodStage;
    }

    public int rodEyes() {
        return rodEyes;
    }

    public int rodTears() {
        return rodTears;
    }

    public int rodNetherite() {
        return rodNetherite;
    }

    public int rodTotalHits() {
        return EndRodRitual.completedHitsBefore(rodStage)
                + (EndRodRitual.isHammerStage(rodStage) ? manualHits : 0);
    }

    public ItemStack anvilItem() {
        return heldItem;
    }

    private void resetRodState() {
        rodStage = EndRodRitual.NONE;
        rodEyes = 0;
        rodTears = 0;
        rodNetherite = 0;
    }

    public void placeOnAnvil(ItemStack one) {
        heldItem = one.copyWithCount(1);
        manualHits = 0;
        resetRodState();
        changed();
    }

    public ItemStack takeFromAnvil() {
        ItemStack s = heldItem;
        heldItem = ItemStack.EMPTY;
        manualHits = 0;
        resetRodState();
        changed();
        return s;
    }

    /** Starts the 3-phase End Rod Cryo-Forging Ritual with a single Diamond Block placed on the anvil. */
    public void startRodRitual(ItemStack diamondBlock) {
        heldItem = diamondBlock.copyWithCount(1);
        manualHits = 0;
        rodStage = EndRodRitual.PHASE_1_DIAMOND;
        rodEyes = 0;
        rodTears = 0;
        rodNetherite = 0;
        changed();
    }

    private static int rodReagentKind(Item item) {
        if (item == Items.ENDER_EYE) {
            return EndRodRitual.REAGENT_EYE;
        }
        if (item == Items.GHAST_TEAR) {
            return EndRodRitual.REAGENT_TEAR;
        }
        if (item == Items.NETHERITE_INGOT) {
            return EndRodRitual.REAGENT_NETHERITE;
        }
        return EndRodRitual.REAGENT_NONE;
    }

    private static int rodIceTier(Block block) {
        if (block == Blocks.BLUE_ICE) {
            return EndRodRitual.ICE_BLUE;
        }
        if (block == Blocks.PACKED_ICE) {
            return EndRodRitual.ICE_PACKED;
        }
        if (block == Blocks.ICE) {
            return EndRodRitual.ICE_NORMAL;
        }
        return EndRodRitual.ICE_NONE;
    }

    private static Block degradeRodIce(Block block) {
        if (block == Blocks.BLUE_ICE) {
            return Blocks.PACKED_ICE;
        }
        if (block == Blocks.PACKED_ICE) {
            return Blocks.ICE;
        }
        if (block == Blocks.ICE) {
            return Blocks.WATER;
        }
        return block;
    }

    /** Attempts to feed Eyes of Ender, Ghast Tears, or a Netherite Ingot into the active waiting stage. */
    public EndRodRitual.ReagentStep tryInsertRodReagent(ItemStack stack, boolean creative) {
        EndRodRitual.ReagentStep step = EndRodRitual.insertReagent(
                rodStage, rodEyes, rodTears, rodNetherite, rodReagentKind(stack.getItem()), stack.getCount());
        if (!step.accepted()) {
            return step;
        }
        if (!creative) {
            stack.shrink(step.taken());
        }
        rodStage = step.newStage();
        rodEyes = step.newEyes();
        rodTears = step.newTears();
        rodNetherite = step.newNetherite();
        if (level instanceof ServerLevel sl) {
            double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.05, z = worldPosition.getZ() + 0.5;
            sl.playSound(null, worldPosition, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.7f,
                    EndRodRitual.isHammerStage(rodStage) ? 1.35f : 1.05f);
            sl.sendParticles(ParticleTypes.END_ROD, x, y, z, 8, 0.18, 0.08, 0.18, 0.03);
        }
        changed();
        return step;
    }

    /** Cancels an active End Rod ritual and returns the Diamond Block plus all inserted reagents. */
    public List<ItemStack> cancelRodRitual() {
        List<ItemStack> refund = new ArrayList<>(4);
        if (!heldItem.isEmpty()) {
            refund.add(heldItem.copy());
        }
        if (rodStage != EndRodRitual.NONE) {
            if (rodEyes > 0) {
                refund.add(new ItemStack(Items.ENDER_EYE, rodEyes));
            }
            if (rodTears > 0) {
                refund.add(new ItemStack(Items.GHAST_TEAR, rodTears));
            }
            if (rodNetherite > 0) {
                refund.add(new ItemStack(Items.NETHERITE_INGOT, rodNetherite));
            }
        }
        heldItem = ItemStack.EMPTY;
        manualHits = 0;
        resetRodState();
        changed();
        return refund;
    }

    /** Human-readable actionbar / status prompt for the current End Rod ritual stage. */
    public MutableComponent rodStatusPrompt() {
        return switch (rodStage) {
            case EndRodRitual.PHASE_1_DIAMOND, EndRodRitual.PHASE_2_VOID, EndRodRitual.PHASE_3_QUENCH -> {
                int phaseNum = rodStage == EndRodRitual.PHASE_1_DIAMOND ? 1
                        : (rodStage == EndRodRitual.PHASE_2_VOID ? 2 : 3);
                yield Component.translatable("message.golem_logix.rod_ritual.phase_progress",
                        phaseNum, manualHits, EndRodRitual.hitsForStage(rodStage),
                        Component.translatable("message.golem_logix.rod_ritual.ice." + EndRodRitual.requiredIceTier(rodStage)));
            }
            case EndRodRitual.WAIT_EYES -> Component.translatable("message.golem_logix.rod_ritual.wait_eyes",
                    EndRodRitual.EYES_REQUIRED - rodEyes);
            case EndRodRitual.WAIT_TEMPER -> Component.translatable("message.golem_logix.rod_ritual.wait_temper",
                    EndRodRitual.TEARS_REQUIRED - rodTears, EndRodRitual.NETHERITE_REQUIRED - rodNetherite);
            default -> Component.empty();
        };
    }

    public static boolean hasRequiredRodIce(Level level, BlockPos pos, int stage) {
        return EndRodRitual.hasSurroundingIce(stage,
                rodIceTier(level.getBlockState(pos.north()).getBlock()),
                rodIceTier(level.getBlockState(pos.south()).getBlock()),
                rodIceTier(level.getBlockState(pos.east()).getBlock()),
                rodIceTier(level.getBlockState(pos.west()).getBlock()));
    }

    private static void degradeSurroundingIce(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            Block cur = level.getBlockState(side).getBlock();
            Block next = degradeRodIce(cur);
            if (next != cur) {
                level.setBlock(side, next.defaultBlockState(), Block.UPDATE_ALL);
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        side.getX() + 0.5, side.getY() + 0.7, side.getZ() + 0.5,
                        12, 0.3, 0.25, 0.3, 0.02);
            }
        }
    }

    /** A hammer strike on the blank or End Rod core on the anvil (server). */
    public Strike strike(ServerLevel level, BlockPos pos, Player player, ItemStack hammer) {
        if (rodStage != EndRodRitual.NONE) {
            if (!EndRodRitual.isHammerStage(rodStage)) {
                return Strike.WAIT_REAGENT;
            }
            long now = level.getGameTime();
            if (now - lastStrike < MANUAL_COOLDOWN) {
                return Strike.COOLDOWN;
            }
            if (!hasRequiredRodIce(level, pos, rodStage)) {
                return Strike.NEED_ICE;
            }
            if (!ForgeHammerItem.canStrike(hammer)) {
                return Strike.NO_SOURCE;
            }
            ForgeHammerItem.spendStrike(hammer);
            lastStrike = now;
            player.causeFoodExhaustion(0.35f);
            EndRodRitual.StrikeStep step = EndRodRitual.stepStrike(rodStage, manualHits);
            rodStage = step.newStage();
            manualHits = step.newHits();

            double x = pos.getX() + 0.5, y = pos.getY() + 0.97, z = pos.getZ() + 0.5;
            level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.45f, 1.0f + rodTotalHits() * 0.015f);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.7f, 1.35f);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 4, 0.16, 0.04, 0.16, 0.1);
            level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 3, 0.18, 0.05, 0.18, 0.02);

            if (step.finished()) {
                degradeSurroundingIce(level, pos);
                heldItem = new ItemStack(Items.END_ROD);
                resetRodState();
                level.sendParticles(ParticleTypes.END_ROD, x, y + 0.15, z, 24, 0.25, 0.2, 0.25, 0.05);
                level.playSound(null, pos, ModSounds.ANVIL_CRUSH.get(), SoundSource.BLOCKS, 1.0f, 1.6f);
                changed();
                return Strike.ROD_DONE;
            }
            if (step.phaseCompleted()) {
                degradeSurroundingIce(level, pos);
                level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.7f, 1.25f);
                level.sendParticles(ParticleTypes.END_ROD, x, y, z, 10, 0.2, 0.08, 0.2, 0.03);
                changed();
                return Strike.ROD_PHASE_DONE;
            }
            changed();
            return Strike.HIT;
        }
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
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 8, 0.15, 0.03, 0.15, 0.1);
        level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 4, 0.2, 0.05, 0.2, 0.02);
        if (manualHits >= MANUAL_HITS) {
            heldItem = manualOutputFor(level, heldItem);
            manualHits = 0;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 6, 0.15, 0.05, 0.15, 0.02);
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

    /** Advances bringer (left deck -> anvil): takes a blank, walks to the anvil, waits until free, drops it and returns. */
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
                bringer.go(TO_ANVIL);
                yield true;
            }
            case TO_ANVIL -> {
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

    /** Advances taker (anvil -> right deck): walks to the anvil during a ritual and carries finished plates to the right deck. */
    private boolean stepTaker(@Nullable SaturatorRecipes.Info heldRecipe) {
        boolean heldIsInput = heldRecipe != null;
        boolean result = !heldItem.isEmpty() && !heldIsInput;
        return switch (taker.phase) {
            case HOME -> {
                if (isCrafting || result) {
                    taker.go(TO_ANVIL);
                    yield true;
                }
                yield false;
            }
            case TO_ANVIL -> {
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
        if (t % 20 == 0 && rodStage == EndRodRitual.NONE) {
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
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.getX() + 0.5, pos.getY() - 0.05, pos.getZ() + 0.5,
                    6, 0.15, 0.05, 0.15, 0.08);
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
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 35, 0.3, 0.3, 0.3, 0.1);
        sl.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 25, 0.3, 0.3, 0.3, 0.05);
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
        if (isCrafting) {
            double cx = pos.getX() + 0.5, cy = pos.getY() + 0.1, cz = pos.getZ() + 0.5;
            double angle = level.getGameTime() * 0.2;
            double radius = 0.6 * (1.0 - getProgressFraction() * 0.5);
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, cx + Math.cos(angle) * radius, cy, cz + Math.sin(angle) * radius, 0, 0.02, 0);
            level.addParticle(ParticleTypes.SNOWFLAKE, cx + (Math.random() - 0.5) * 0.4, cy + (Math.random() - 0.5) * 0.4,
                    cz + (Math.random() - 0.5) * 0.4, 0, 0, 0);
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
        walker.tick(lampState(recipe) == 0, 24f, 1.8f, level.random, ringGoal);
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

    /** Clears dropped items after removal so nothing can be re-dropped if removal runs twice. */
    public void clearForRemoval() {
        inDeck = ItemStack.EMPTY;
        heldItem = ItemStack.EMPTY;
        outDeck = ItemStack.EMPTY;
        bringer.hands = ItemStack.EMPTY;
        taker.hands = ItemStack.EMPTY;
        redstoneModule = false;
        resetRodState();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!heldItem.isEmpty()) {
            tag.put("HeldItem", heldItem.save(registries));
        }
        if (!inDeck.isEmpty()) {
            tag.put("InDeck", inDeck.save(registries));
        }
        if (!outDeck.isEmpty()) {
            tag.put("OutDeck", outDeck.save(registries));
        }
        tag.put("Bringer", bringer.save(registries));
        tag.put("Taker", taker.save(registries));
        tag.putInt("Progress", ritualProgress);
        tag.putInt("ManualHits", manualHits);
        tag.putInt("RodStage", rodStage);
        tag.putInt("RodEyes", rodEyes);
        tag.putInt("RodTears", rodTears);
        tag.putInt("RodNetherite", rodNetherite);
        tag.putInt("Source", source);
        tag.putBoolean("IsCrafting", isCrafting);
        tag.putInt("Coolers", coolers);
        tag.putBoolean("RedstoneModule", redstoneModule);
        tag.putBoolean("AutoOutput", autoOutput);
        redstone.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        heldItem = readOptionalItem(tag, "HeldItem", registries);
        inDeck = readOptionalItem(tag, "InDeck", registries);
        outDeck = readOptionalItem(tag, "OutDeck", registries);
        bringer.load(tag.getCompound("Bringer"), registries);
        taker.load(tag.getCompound("Taker"), registries);
        ritualProgress = tag.getInt("Progress");
        manualHits = tag.getInt("ManualHits");
        rodStage = tag.contains("RodStage") ? tag.getInt("RodStage") : EndRodRitual.NONE;
        rodEyes = tag.getInt("RodEyes");
        rodTears = tag.getInt("RodTears");
        rodNetherite = tag.getInt("RodNetherite");
        source = tag.getInt("Source");
        syncedLevel = levelEighths();
        isCrafting = tag.getBoolean("IsCrafting");
        coolers = tag.getInt("Coolers");
        redstoneModule = tag.getBoolean("RedstoneModule");
        autoOutput = !tag.contains("AutoOutput") || tag.getBoolean("AutoOutput");
        redstone.load(tag);
    }

    private static ItemStack readOptionalItem(CompoundTag tag, String key, HolderLookup.Provider registries) {
        return tag.contains(key)
                ? ItemStack.parse(registries, tag.getCompound(key)).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
