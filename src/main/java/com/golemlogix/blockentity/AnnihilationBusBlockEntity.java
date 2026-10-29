package com.golemlogix.blockentity;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blocks.GolemPlaneBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.NbtBridge;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Annihilation Bus: a small iron golem works the block in front with the tool in its slot - or, with the slot empty, with the
 * strength of every iron tool (the best of the iron pickaxe, axe, shovel, hoe and sword for that block, never worn out).
 * <ul>
 * <li>Left click: it mines the block as a player would, as fast as the tool allows (Compressed Co-Processors speed it up, four of
 * them eightfold), cracks and all. The drops go into the network; while the network cannot take them all it waits. A block the
 * tool cannot harvest is left alone, and a tool about to break is put down.</li>
 * <li>Right click: once an interval it uses the item in its slot (or its empty hand) on the block - a lever, shears, a hoe, bone
 * meal - and whatever falls out in front goes into the network.</li>
 * <li>Attack: once an interval it strikes the creatures in front with the tool (an iron sword when the slot is empty), at full
 * strength, enchantments and all, without knocking them back; what they drop goes into the network. Players, named, owned and
 * invulnerable creatures are never
 * touched; the filter takes spawn eggs (which creatures), and "grown only" spares the young.</li>
 * </ul>
 * Settings, as on a clicker: the click, sneaking, the filter as an allow or a deny list (by the block's item; an empty allow
 * list works on nothing - the bus stands still while it is set up - an empty deny list on everything), and "grown only" (a crop or anything with an age only when fully grown). Upgrades: redstone, a Fuzzy Card, and
 * the secret Plasma Temper: the tool in the slot never wears. Its speed is its tool's: it mines as a player would with it
 * (Efficiency counts), strikes at the weapon's attack speed, and clicks once a bus interval.
 * With an Autocraft Module it keeps its tool slot filled from the network with what the player last put there: bone meal
 * topped up with whatever the network has, a tool used till it breaks and then replaced by the least worn one; what the
 * network lacks is ordered from the calculators one at a time.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public class AnnihilationBusBlockEntity extends BlockEntity implements GolemPlaneBlock.Waking {
    public static final int FILTER_SLOTS = 9;
    private static final int MAX_IDLE_TICKS = 20, MAX_IDLE_STEPS = 5;
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("6f8e2c1a-3b7d-4c55-9a1e-50b1ce6a7d11"), "[Golem Logix]");
    /** The iron tools a bare golem brings to every block. */
    private static final net.minecraft.world.item.Item[] IRON_TOOLS = {Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE,
            Items.IRON_SWORD};
    /** Click modes: break, use, attack. */
    public static final int LEFT = 0, RIGHT = 1, ATTACK = 2;
    /** The bus whose golem is breaking a block or striking a creature right now: the drops go into its network (onDrops, onLoot). */
    @Nullable
    private static AnnihilationBusBlockEntity breaking;

    private final OwnedContainer filters = new OwnedContainer(FILTER_SLOTS, this, s -> true);
    private final ToolContainer tool = new ToolContainer();
    /** What the tool slot is refilled with (one, the tool unworn): what the player last put there; empty once they take it out. */
    private ItemStack refill = ItemStack.EMPTY;
    private long lastCraftRequest = -1000;
    private static final int CRAFT_REQUEST_COOLDOWN = 40, REFILL_EVERY = 10;

    /** The tool slot; the window tells it when the player takes the item out (then it is not refilled any more). */
    public class ToolContainer extends SimpleContainer {
        ToolContainer() {
            super(1);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            ItemStack held = getItem(0);
            if (!held.isEmpty()) {
                refill = held.copyWithCount(1);
                if (refill.isDamageableItem()) {
                    refill.setDamageValue(0);
                }
            }
            AnnihilationBusBlockEntity.this.setChanged();
            sync();
        }

        /** The player emptied the slot: nothing to refill. */
        public void forget() {
            if (getItem(0).isEmpty()) {
                refill = ItemStack.EMPTY;
                AnnihilationBusBlockEntity.this.setChanged();
            }
        }
    }
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.ANNIHILATION_BUS, this);
    private final RedstoneControl redstone = new RedstoneControl();

    private int mode = LEFT;
    private boolean sneak, grownOnly;
    /** A new bus works on everything (an empty deny list); an empty allow list stops it. */
    private boolean denyList = true;
    /** Mining progress on the block in front (0..1), and the block it is for. */
    private float progress;
    @Nullable
    private BlockPos mining;
    /** For the renderer: the golem is swinging (mining), and when it last used its item (right click). */
    private boolean swinging;
    /** For the renderer: the Plasma Temper is in (the golem wears it as a belt). */
    private boolean belted;
    private long lastUse = -100;
    private int idleSteps;
    private long sleepUntil;

    public AnnihilationBusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANNIHILATION_BUS.get(), pos, state);
    }

    public OwnedContainer getFilters() {
        return filters;
    }

    public ToolContainer getTool() {
        return tool;
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    public boolean swinging() {
        return swinging;
    }

    public boolean belted() {
        return belted;
    }

    public long lastUse() {
        return lastUse;
    }

    /** LEFT, RIGHT or ATTACK. */
    public int mode() {
        return mode;
    }

    @Override
    public void wake() {
        idleSteps = 0;
        sleepUntil = 0;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        wake();
    }

    /** Window data: 0 redstone mode, 1 click mode (LEFT, RIGHT, ATTACK), 2 sneak, 3 deny list, 4 grown only. */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> redstone.mode().ordinal();
                    case 1 -> mode;
                    case 2 -> sneak ? 1 : 0;
                    case 3 -> denyList ? 1 : 0;
                    default -> grownOnly ? 1 : 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> redstone.setMode(RedstoneMode.byId(value));
                    case 1 -> {
                        mode = Math.floorMod(value, 3);
                        stopMining();
                    }
                    case 2 -> sneak = value != 0;
                    case 3 -> denyList = value != 0;
                    default -> grownOnly = value != 0;
                }
                setChanged();
                sync();
            }

            @Override
            public int getCount() {
                return 5;
            }
        };
    }

    /** How often it clicks: once a bus interval; it strikes at the weapon's own pace (20 / attack speed, an iron sword bare). */
    public int interval() {
        if (mode == ATTACK) {
            ItemStack weapon = tool.getItem(0).isEmpty() ? new ItemStack(Items.IRON_SWORD) : tool.getItem(0);
            double speed = 4.0 + mainHandBonus(weapon, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
            return Math.max(1, (int) Math.ceil(20.0 / Math.max(0.25, speed)));
        }
        return Math.max(1, GolemConfig.BUS_INTERVAL_TICKS.get());
    }

    /** The sum of the plain (add value) modifiers the item and its enchantments give this attribute in the main hand. */
    private static double mainHandBonus(ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        double[] sum = {0};
        stack.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (a, m) -> {
            if (a.equals(attribute) && m.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
                sum[0] += m.amount();
            }
        });
        return sum[0];
    }

    /** The tool's speed on this block as in a player's hand: Efficiency adds its bonus to a tool that suits the block. */
    static float mineSpeed(ItemStack with, BlockState state) {
        float speed = with.getDestroySpeed(state);
        if (speed > 1f) {
            speed += (float) mainHandBonus(with, net.minecraft.world.entity.ai.attributes.Attributes.MINING_EFFICIENCY);
        }
        return speed;
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, AnnihilationBusBlockEntity be) {
        be.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        upgrades.dropStrays(level, pos);
        boolean hasRedstone = upgrades.has(UpgradeType.REDSTONE);
        boolean powered = hasRedstone && level.hasNeighborSignal(pos);
        if (hasRedstone) {
            redstone.observe(powered);
        }
        long now = level.getGameTime();
        if (tempered() != belted) {
            belted = tempered();
            sync();
        }
        Direction facing = state.getValue(DirectionalBlock.FACING);
        BlockPos target = pos.relative(facing);
        GolemNetwork net = GolemNetwork.get(level, pos);
        if (Math.floorMod(now + pos.asLong(), REFILL_EVERY) == 0 && net.isOnline() && net.isDeviceActive(pos) && refill(level, net)) {
            sleepUntil = 0;
            idleSteps = 0;
        }
        boolean ready = now >= sleepUntil && level.isLoaded(target) && net.isOnline() && net.isDeviceActive(pos)
                && redstone.allows(hasRedstone, powered, false);
        if (!ready) {
            stopMining();
            return;
        }
        if (mode == ATTACK) {
            stopMining();
            if (Math.floorMod(now + pos.asLong(), interval()) == 0) {
                if (attack(level, pos, target, facing)) {
                    redstone.allows(hasRedstone, powered, true);
                } else {
                    sleep(now);
                }
            }
            return;
        }
        BlockState front = level.getBlockState(target);
        if (front.isAir() || !wanted(front)) {
            stopMining();
            sleep(now);
            return;
        }
        if (mode == RIGHT) {
            if (Math.floorMod(now + pos.asLong(), interval()) == 0) {
                if (use(level, net, pos, target, facing)) {
                    redstone.allows(hasRedstone, powered, true);
                } else {
                    sleep(now);
                }
            }
            return;
        }
        ItemStack with = toolFor(front);
        if (with.isEmpty() || !canMine(level, target, front, with)) {
            stopMining();
            sleep(now);
            return;
        }
        if (!target.equals(mining)) {
            stopMining();
            mining = target;
        }
        if (progress < 1f) {
            float speed = mineSpeed(with, front);
            float hardness = front.getDestroySpeed(level, target);
            boolean right = !front.requiresCorrectToolForDrops() || with.isCorrectToolForDrops(front);
            progress += hardness <= 0 ? 1f : speed / hardness / (right ? 30f : 100f);
            setSwinging(true);
            level.destroyBlockProgress(fakeId(), target, progress >= 1f ? 9 : (int) (progress * 10f));
        }
        if (progress >= 1f && breakBlock(level, net, pos, target, facing, front, with)) {
            redstone.allows(hasRedstone, powered, true);
            stopMining();
        }
    }

    /** The two items are the same thing for the tool slot: the same tool however worn, or exactly the same item. */
    private static boolean sameKind(ItemStack a, ItemStack b) {
        return a.isDamageableItem() ? a.is(b.getItem()) : ItemStack.isSameItemSameComponents(a, b);
    }

    /** The Autocraft Module refills the slot: a tool in it may then be used till it breaks, a new one takes its place. */
    private boolean refilling() {
        return !refill.isEmpty() && upgrades.has(UpgradeType.CRAFTING);
    }

    /**
     * Autocraft Module: the tool slot refilled from the network, as far as the network has it. True if something changed.
     * An empty slot gets the least worn tool of the same kind (a tool is used till it breaks, never put back); bone meal and the
     * like are topped up towards a full stack with whatever the network holds. What it lacks is ordered, one at a time.
     */
    private boolean refill(ServerLevel level, GolemNetwork net) {
        if (!refilling()) {
            return false;
        }
        ItemStack held = tool.getItem(0);
        if (!held.isEmpty() && !sameKind(held, refill)) {
            return false;
        }
        if (refill.isDamageableItem()) {
            if (!held.isEmpty()) {
                return false;
            }
            ItemKey best = null;
            for (ItemKey k : net.listItems().keySet()) {
                ItemStack s = k.stack();
                if (s.is(refill.getItem()) && (best == null || s.getDamageValue() < best.stack().getDamageValue())) {
                    best = k;
                }
            }
            ItemStack fresh = best == null ? ItemStack.EMPTY : net.extractItem(best.stack(), 1, false);
            if (fresh.isEmpty()) {
                order(level, net, ItemKey.of(refill));
                return false;
            }
            tool.setItem(0, fresh);
            return true;
        }
        int want = refill.getMaxStackSize() - held.getCount();
        if (want <= 0) {
            return false;
        }
        ItemStack got = net.extractItem(refill, want, false);
        if (got.isEmpty()) {
            order(level, net, ItemKey.of(refill));
            return false;
        }
        tool.setItem(0, held.isEmpty() ? got : held.copyWithCount(held.getCount() + got.getCount()));
        return true;
    }

    /** Orders one of what the slot lacks from the calculators (once one is on its way, the next waits for it). */
    private void order(ServerLevel level, GolemNetwork net, ItemKey key) {
        long now = level.getGameTime();
        if (now - lastCraftRequest < CRAFT_REQUEST_COOLDOWN || !net.canCraft(key) || net.busCraftPending(worldPosition, key)) {
            return;
        }
        lastCraftRequest = now;
        java.util.Map<ItemKey, Long> one = new java.util.LinkedHashMap<>();
        one.put(key, 1L);
        net.startCrafting(one, null, worldPosition);
    }

    private void sleep(long now) {
        int interval = interval();
        idleSteps = Math.min(MAX_IDLE_STEPS, idleSteps + 1);
        sleepUntil = now + Math.max(interval, Math.min(MAX_IDLE_TICKS, interval << idleSteps));
    }

    private void stopMining() {
        if (mining != null && level instanceof ServerLevel sl) {
            sl.destroyBlockProgress(fakeId(), mining, -1);
        }
        mining = null;
        progress = 0f;
        setSwinging(false);
    }

    private void setSwinging(boolean on) {
        if (swinging != on) {
            swinging = on;
            sync();
        }
    }

    private int fakeId() {
        return (int) (worldPosition.asLong() ^ 0x5a1e5a1e);
    }

    /** The block's item passes the filter (allow or deny list; an empty filter takes everything) and, if asked, it is grown. */
    private boolean wanted(BlockState state) {
        if (grownOnly && !grown(state)) {
            return false;
        }
        boolean fuzzy = upgrades.has(UpgradeType.FUZZY);
        ItemStack asItem = new ItemStack(state.getBlock().asItem());
        boolean any = false, listed = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filters.getItem(i);
            if (f.isEmpty()) {
                continue;
            }
            any = true;
            if (!asItem.isEmpty() && com.golemlogix.system.Fuzzy.matches(f, asItem, fuzzy)) {
                listed = true;
                break;
            }
        }
        return any ? listed != denyList : denyList;
    }

    /** A crop at its last age; a block without an age is always grown. */
    public static boolean grown(BlockState state) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop) {
            return crop.isMaxAge(state);
        }
        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty age && property.getName().equals("age")) {
                return state.getValue(age) >= age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
            }
        }
        return true;
    }

    /** The tool for this block: the one in the slot, or the best iron tool. */
    private ItemStack toolFor(BlockState state) {
        ItemStack held = tool.getItem(0);
        if (!held.isEmpty()) {
            return held;
        }
        ItemStack best = ItemStack.EMPTY;
        float bestSpeed = -1f;
        for (var item : IRON_TOOLS) {
            ItemStack t = new ItemStack(item);
            boolean right = !state.requiresCorrectToolForDrops() || t.isCorrectToolForDrops(state);
            float speed = right ? t.getDestroySpeed(state) : 0f;
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = t;
            }
        }
        return best;
    }

    /** It can break the block: breakable, harvestable with this tool, and the tool would not break doing it. */
    private boolean canMine(ServerLevel level, BlockPos target, BlockState state, ItemStack with) {
        if (state.getDestroySpeed(level, target) < 0 || state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
            return false;
        }
        if (state.requiresCorrectToolForDrops() && !with.isCorrectToolForDrops(state)) {
            return false;
        }
        // the last point of durability is kept for the player - unless the slot is refilled: then the tool is used till it breaks
        return tempered() || refilling() || !(with.isDamageableItem() && with.getDamageValue() >= with.getMaxDamage() - 1);
    }

    /** The Plasma Temper is in: the tool never wears. */
    private boolean tempered() {
        return upgrades.has(UpgradeType.TEMPER);
    }

    /** With the temper, the tool comes out of its work as worn as it went in. */
    private ItemStack unworn(ItemStack before, ItemStack after) {
        if (tempered() && !before.isEmpty() && (after.isEmpty() || after.is(before.getItem())) && before.isDamageableItem()) {
            ItemStack kept = after.isEmpty() ? before.copy() : after;
            kept.setDamageValue(before.getDamageValue());
            return kept;
        }
        return after;
    }

    private FakePlayer golem(ServerLevel level, BlockPos pos, Direction facing, ItemStack hand) {
        FakePlayer p = FakePlayerFactory.get(level, PROFILE);
        Vec3 c = Vec3.atCenterOf(pos);
        p.snapTo(c.x, c.y - p.getEyeHeight(), c.z, facing.toYRot(), facing == Direction.UP ? -90f : facing == Direction.DOWN ? 90f : 0f);
        p.setItemInHand(InteractionHand.MAIN_HAND, hand);
        p.setShiftKeyDown(sneak);
        return p;
    }

    /** Left click done: the block breaks with the tool, its drops go into the network. False if they would not all fit. */
    private boolean breakBlock(ServerLevel level, GolemNetwork net, BlockPos pos, BlockPos target, Direction facing, BlockState state,
                               ItemStack with) {
        boolean own = !tool.getItem(0).isEmpty();
        ItemStack hand = own ? tool.getItem(0) : with.copy();
        FakePlayer p = golem(level, pos, facing, hand);
        for (ItemStack drop : Block.getDrops(state, level, target, level.getBlockEntity(target), p, hand)) {
            if (!net.insertItem(drop, true).isEmpty()) {
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                return false;
            }
        }
        ItemStack before = hand.copy();
        level.levelEvent(2001, target, Block.getId(state));
        breaking = this;
        try {
            p.gameMode.destroyBlock(target);
        } finally {
            breaking = null;
        }
        if (own) {
            tool.setItem(0, unworn(before, p.getMainHandItem()));
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setShiftKeyDown(false);
        sweep(level, new AABB(target).inflate(0.25));
        return true;
    }

    /** Right click: the item in the slot (or the bare hand) used on the block; what falls out goes into the network. */
    private boolean use(ServerLevel level, GolemNetwork net, BlockPos pos, BlockPos target, Direction facing) {
        ItemStack before = tool.getItem(0).copy();
        FakePlayer p = golem(level, pos, facing, before.copy());
        Vec3 hitAt = Vec3.atCenterOf(target).relative(facing.getOpposite(), 0.5);
        BlockHitResult hit = new BlockHitResult(hitAt, facing.getOpposite(), target, false);
        var result = p.gameMode.useItemOn(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        if (!result.consumesAction() && !p.getMainHandItem().isEmpty() && !level.getFluidState(target).isEmpty()) {
            // a bucket or a bottle takes a fluid by the item's own use (a look at the fluid), not by a click on the block; the
            // golem looks from the bus's front face, or its own block would be the first thing the look hits
            Vec3 eye = Vec3.atCenterOf(pos).relative(facing, 0.55);
            p.snapTo(eye.x, eye.y - p.getEyeHeight(), eye.z, p.getYRot(), p.getXRot());
            result = p.gameMode.useItem(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND);
        }
        ItemStack after = p.getMainHandItem().copy();
        if (!before.isEmpty() && !after.isEmpty() && !after.is(before.getItem()) && !before.isDamageableItem()) {
            // the item became another (an empty bucket a water bucket): that one is the harvest, into the network; the slot
            // is left empty for the Autocraft Module to refill with the empty one
            after = net.insertItem(after, false);
        }
        tool.setItem(0, unworn(before, after));
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // anything the golem picked up (a filled bucket's leftovers, a harvest given to the hand) goes into the network
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty()) {
                p.getInventory().setItem(i, ItemStack.EMPTY);
                ItemStack rest = net.insertItem(s, false);
                if (!rest.isEmpty()) {
                    Block.popResource(level, pos, rest);
                }
            }
        }
        p.setShiftKeyDown(false);
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(0.25), e -> e.isAlive() && e.getAge() <= 2)) {
            ItemStack rest = net.insertItem(e.getItem(), false);
            if (rest.isEmpty()) {
                e.discard();
            } else {
                e.setItem(rest);
            }
        }
        boolean did = result.consumesAction();
        if (did) {
            lastUse = level.getGameTime();
            sync();
        }
        return did;
    }

    /** The creature can be struck: alive and mortal, no player, nobody's pet, not named; the filter (spawn eggs) and the young. */
    private boolean prey(net.minecraft.world.entity.LivingEntity e) {
        if (!e.isAlive() || e.isInvulnerable() || e instanceof net.minecraft.world.entity.player.Player
                || e instanceof net.minecraft.world.entity.decoration.ArmorStand || e.hasCustomName()
                || e instanceof net.minecraft.world.entity.OwnableEntity own && own.getOwnerReference() != null) {
            return false;
        }
        if (grownOnly && e.isBaby()) {
            return false;
        }
        boolean any = false, listed = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filters.getItem(i);
            if (f.isEmpty()) {
                continue;
            }
            any = true;
            if (net.minecraft.world.item.SpawnEggItem.getType(f) == e.getType()) {
                listed = true;
                break;
            }
        }
        return any ? listed != denyList : denyList;
    }

    /** Attack: one strike at every creature in front, with the tool (or an iron sword), at full strength. False if none was there. */
    private boolean attack(ServerLevel level, BlockPos pos, BlockPos target, Direction facing) {
        var victims = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(target), this::prey);
        if (victims.isEmpty()) {
            return false;
        }
        boolean own = !tool.getItem(0).isEmpty();
        ItemStack before = own ? tool.getItem(0).copy() : new ItemStack(Items.IRON_SWORD);
        FakePlayer p = golem(level, pos, facing, before.copy());
        // a FakePlayer never ticks: neither does the weapon's damage reach its attributes nor does its swing wind up by itself
        List<Runnable> undo = new java.util.ArrayList<>();
        p.getMainHandItem().forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            var instance = p.getAttribute(attribute);
            if (instance != null) {
                instance.addOrUpdateTransientModifier(modifier);
                undo.add(() -> instance.removeModifier(modifier.id()));
            }
        });
        breaking = this;
        try {
            for (var e : victims) {
                p.attackStrengthTicker = 1000;
                p.attack(e);
                // no knockback: what stands in front stays in front for the next strike (a mob farm keeps working)
                e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
                e.hurtMarked = true;
            }
        } finally {
            breaking = null;
            undo.forEach(Runnable::run);
        }
        if (own) {
            tool.setItem(0, unworn(before, p.getMainHandItem().copy()));
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setShiftKeyDown(false);
        sweep(level, new AABB(target).inflate(1.0));
        lastUse = level.getGameTime();
        sync();
        return true;
    }

    /** Items that have just fallen in the box (dropped outside the drop events by some mod) go into the network. */
    private void sweep(ServerLevel level, AABB box) {
        GolemNetwork net = GolemNetwork.get(level, worldPosition);
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && e.getAge() <= 2)) {
            ItemStack rest = net.insertItem(e.getItem(), false);
            if (rest.isEmpty()) {
                e.discard();
            } else {
                e.setItem(rest);
            }
        }
    }

    /**
     * What a creature our golem kills drops goes into its network instead of onto the ground. Last of all handlers, so the loot
     * other mods add to the drops (Mystical Agriculture's essences, say) goes in too.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onLoot(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        AnnihilationBusBlockEntity bus = breaking;
        if (bus == null || !(event.getSource().getEntity() instanceof FakePlayer fp) || !PROFILE.id().equals(fp.getUUID())
                || bus.level == null) {
            return;
        }
        GolemNetwork net = GolemNetwork.get(bus.level, bus.worldPosition);
        event.getDrops().removeIf(e -> {
            ItemStack rest = net.insertItem(e.getItem(), false);
            if (rest.isEmpty()) {
                return true;
            }
            e.setItem(rest);
            return false;
        });
    }

    /** The drops of a block our golem breaks go into its network instead of onto the ground (last of all handlers, as above). */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onDrops(BlockDropsEvent event) {
        AnnihilationBusBlockEntity bus = breaking;
        if (bus == null || !(event.getBreaker() instanceof FakePlayer fp) || !PROFILE.id().equals(fp.getUUID())) {
            return;
        }
        GolemNetwork net = GolemNetwork.get(event.getLevel(), bus.worldPosition);
        event.getDrops().removeIf(e -> {
            ItemStack rest = net.insertItem(e.getItem(), false);
            if (rest.isEmpty()) {
                return true;
            }
            e.setItem(rest);
            return false;
        });
    }

    /** Broken or replaced: the tool and the upgrades drop. */
    public void dropContents() {
        if (level == null) {
            return;
        }
        stopMining();
        net.minecraft.world.Containers.dropContents(level, worldPosition, tool);
        net.minecraft.world.Containers.dropContents(level, worldPosition, upgrades);
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    // ----------------------------------------------------------- persistence

    private void saveTag(CompoundTag tag, HolderLookup.Provider registries, boolean full) {
        if (full) {
            tag.put("Filters", filters.createTag(registries));
            tag.put("Upgrades", upgrades.createTag(registries));
            redstone.save(tag);
            tag.putBoolean("Sneak", sneak);
            tag.putBoolean("DenyList", denyList);
            tag.putBoolean("EmptyAllowStops", true);
            tag.putBoolean("GrownOnly", grownOnly);
            if (!refill.isEmpty()) {
                tag.put("Refill", NbtBridge.saveItem(refill, registries));
            }
        }
        if (!tool.getItem(0).isEmpty()) {
            tag.put("Tool", NbtBridge.saveItem(tool.getItem(0), registries));
        }
        tag.putInt("Mode", mode);
        tag.putBoolean("Swinging", swinging);
        tag.putBoolean("Belted", belted);
        tag.putLong("LastUse", lastUse);
    }

    private void loadTag(CompoundTag tag, HolderLookup.Provider registries, boolean full) {
        if (full) {
            filters.fromTag(tag.getListOrEmpty("Filters"), registries);
            upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
            redstone.load(tag);
            sneak = tag.getBooleanOr("Sneak", false);
            denyList = tag.getBooleanOr("DenyList", true);
            if (!tag.getBooleanOr("EmptyAllowStops", false) && !denyList && filters.isEmpty()) {
                // saved when an empty allow list still meant "everything": it stays working as it did
                denyList = true;
            }
            grownOnly = tag.getBooleanOr("GrownOnly", false);
            refill = tag.contains("Refill") ? NbtBridge.loadItem(registries, tag.getCompoundOrEmpty("Refill")).orElse(ItemStack.EMPTY)
                    : ItemStack.EMPTY;
            if (refill.isEmpty() && tag.contains("Tool")) {
                // saved before the refill: what is in the slot is what it refills
                refill = NbtBridge.loadItem(registries, tag.getCompoundOrEmpty("Tool")).orElse(ItemStack.EMPTY).copyWithCount(1);
                if (refill.isDamageableItem()) {
                    refill.setDamageValue(0);
                }
            }
        }
        ItemStack t = tag.contains("Tool") ? NbtBridge.loadItem(registries, tag.getCompoundOrEmpty("Tool")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        tool.getItems().set(0, t);
        mode = tag.getIntOr("Mode", tag.getBooleanOr("RightClick", false) ? RIGHT : LEFT);
        swinging = tag.getBooleanOr("Swinging", false);
        belted = tag.getBooleanOr("Belted", false);
        lastUse = tag.getLongOr("LastUse", -100L);
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag tag = new CompoundTag();
        saveTag(tag, NbtBridge.registries(this), true);
        NbtBridge.write(output, tag);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        loadTag(NbtBridge.read(input), input.lookup(), true);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveTag(tag, registries, false);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(net.minecraft.world.level.storage.ValueInput in) {
        loadTag(NbtBridge.read(in), in.lookup(), false);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.world.level.storage.ValueInput in) {
        handleUpdateTag(in);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && state.getBlock() instanceof com.golemlogix.blocks.RemovalAware r) {
            r.onRemoved(state, level, pos);
        }
    }
}
