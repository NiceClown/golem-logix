package com.golemlogix.blockentity;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blocks.GolemPlaneBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.GolemNetwork;
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
 * </ul>
 * Settings, as on a clicker: the click, sneaking, the filter as an allow or a deny list (by the block's item; empty works on
 * everything), and "grown only" (a crop or anything with an age only when fully grown). Upgrades: speed, redstone, a Fuzzy Card,
 * and the secret Plasma Temper: the tool in the slot never wears.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public class AnnihilationBusBlockEntity extends BlockEntity implements GolemPlaneBlock.Waking {
    public static final int FILTER_SLOTS = 9;
    private static final double[] INTERVAL_MULT = {1.0, 0.75, 0.5, 0.25, 0.125};
    /** How much faster it mines with 0..4 Compressed Co-Processors. */
    private static final float[] MINE_MULT = {1f, 1.5f, 2f, 4f, 8f};
    private static final int MAX_IDLE_TICKS = 20, MAX_IDLE_STEPS = 5;
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("6f8e2c1a-3b7d-4c55-9a1e-50b1ce6a7d11"), "[Golem Logix]");
    /** The iron tools a bare golem brings to every block. */
    private static final net.minecraft.world.item.Item[] IRON_TOOLS = {Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE,
            Items.IRON_SWORD};
    /** The bus whose golem is breaking a block right now: its drops go into that bus's network (see onDrops). */
    @Nullable
    private static AnnihilationBusBlockEntity breaking;

    private final OwnedContainer filters = new OwnedContainer(FILTER_SLOTS, this, s -> true);
    private final SimpleContainer tool = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            AnnihilationBusBlockEntity.this.setChanged();
            sync();
        }
    };
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.ANNIHILATION_BUS, this);
    private final RedstoneControl redstone = new RedstoneControl();

    private boolean rightClick, sneak, denyList, grownOnly;
    /** Mining progress on the block in front (0..1), and the block it is for. */
    private float progress;
    @Nullable
    private BlockPos mining;
    /** For the renderer: the golem is swinging (mining), and when it last used its item (right click). */
    private boolean swinging;
    private long lastUse = -100;
    private int idleSteps;
    private long sleepUntil;

    public AnnihilationBusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANNIHILATION_BUS.get(), pos, state);
    }

    public OwnedContainer getFilters() {
        return filters;
    }

    public SimpleContainer getTool() {
        return tool;
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    public boolean swinging() {
        return swinging;
    }

    public long lastUse() {
        return lastUse;
    }

    public boolean rightClick() {
        return rightClick;
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

    /** Window data: 0 redstone mode, 1 right click, 2 sneak, 3 deny list, 4 grown only. */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> redstone.mode().ordinal();
                    case 1 -> rightClick ? 1 : 0;
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
                        rightClick = value != 0;
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

    public int interval() {
        int n = upgrades.count(UpgradeType.SPEED);
        return Math.max(1, (int) Math.round(GolemConfig.BUS_INTERVAL_TICKS.get() * INTERVAL_MULT[n]));
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, AnnihilationBusBlockEntity be) {
        be.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean hasRedstone = upgrades.has(UpgradeType.REDSTONE);
        boolean powered = hasRedstone && level.hasNeighborSignal(pos);
        if (hasRedstone) {
            redstone.observe(powered);
        }
        long now = level.getGameTime();
        Direction facing = state.getValue(DirectionalBlock.FACING);
        BlockPos target = pos.relative(facing);
        GolemNetwork net = GolemNetwork.get(level, pos);
        boolean ready = now >= sleepUntil && level.isLoaded(target) && net.isOnline() && net.isDeviceActive(pos)
                && redstone.allows(hasRedstone, powered, false);
        if (!ready) {
            stopMining();
            return;
        }
        BlockState front = level.getBlockState(target);
        if (front.isAir() || !wanted(front)) {
            stopMining();
            sleep(now);
            return;
        }
        if (rightClick) {
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
            float speed = with.getDestroySpeed(front);
            float hardness = front.getDestroySpeed(level, target);
            boolean right = !front.requiresCorrectToolForDrops() || with.isCorrectToolForDrops(front);
            progress += hardness <= 0 ? 1f : speed / hardness / (right ? 30f : 100f) * MINE_MULT[upgrades.count(UpgradeType.SPEED)];
            setSwinging(true);
            level.destroyBlockProgress(fakeId(), target, progress >= 1f ? 9 : (int) (progress * 10f));
        }
        if (progress >= 1f && breakBlock(level, net, pos, target, facing, front, with)) {
            redstone.allows(hasRedstone, powered, true);
            stopMining();
        }
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
        return !any || listed != denyList;
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
        return tempered() || !(with.isDamageableItem() && with.getDamageValue() >= with.getMaxDamage() - 1);
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
        return true;
    }

    /** Right click: the item in the slot (or the bare hand) used on the block; what falls out goes into the network. */
    private boolean use(ServerLevel level, GolemNetwork net, BlockPos pos, BlockPos target, Direction facing) {
        ItemStack before = tool.getItem(0).copy();
        FakePlayer p = golem(level, pos, facing, before.copy());
        Vec3 hitAt = Vec3.atCenterOf(target).relative(facing.getOpposite(), 0.5);
        BlockHitResult hit = new BlockHitResult(hitAt, facing.getOpposite(), target, false);
        var result = p.gameMode.useItemOn(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        tool.setItem(0, unworn(before, p.getMainHandItem().copy()));
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

    /** The drops of a block our golem breaks go into its network instead of onto the ground. */
    @SubscribeEvent
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
            tag.putBoolean("GrownOnly", grownOnly);
        }
        if (!tool.getItem(0).isEmpty()) {
            tag.put("Tool", NbtBridge.saveItem(tool.getItem(0), registries));
        }
        tag.putBoolean("RightClick", rightClick);
        tag.putBoolean("Swinging", swinging);
        tag.putLong("LastUse", lastUse);
    }

    private void loadTag(CompoundTag tag, HolderLookup.Provider registries, boolean full) {
        if (full) {
            filters.fromTag(tag.getListOrEmpty("Filters"), registries);
            upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
            redstone.load(tag);
            sneak = tag.getBooleanOr("Sneak", false);
            denyList = tag.getBooleanOr("DenyList", false);
            grownOnly = tag.getBooleanOr("GrownOnly", false);
        }
        ItemStack t = tag.contains("Tool") ? NbtBridge.loadItem(registries, tag.getCompoundOrEmpty("Tool")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        tool.getItems().set(0, t);
        rightClick = tag.getBooleanOr("RightClick", false);
        swinging = tag.getBooleanOr("Swinging", false);
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
