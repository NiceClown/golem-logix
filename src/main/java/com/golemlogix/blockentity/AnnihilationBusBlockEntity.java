package com.golemlogix.blockentity;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blocks.GolemPlaneBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
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
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Annihilation Bus: a small iron golem works the block in front with the tool in its slot - or, with the slot empty, with the
 * strength of every iron tool (the best of the iron pickaxe, axe, shovel, hoe and sword for that block, never worn out).
 * <ul>
 * <li>Left click: mines the block as a player would, cracks and all. Drops go into the network; while full it waits.</li>
 * <li>Right click: once an interval uses the item in its slot (or empty hand) on the block; drops go into the network.</li>
 * <li>Attack: once an interval strikes creatures in front at full weapon strength without knockback; loot goes into the network.
 * Players, named, owned, and invulnerable creatures are spared; the filter takes spawn eggs and "grown only" spares babies.</li>
 * </ul>
 * With an Autocraft Module it keeps its tool slot filled from the network with what the player last placed there.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public class AnnihilationBusBlockEntity extends BlockEntity implements GolemPlaneBlock.Waking {
    public static final int FILTER_SLOTS = 9;
    private static final int MAX_IDLE_TICKS = 20, MAX_IDLE_STEPS = 5;
    private static final int CRAFT_REQUEST_COOLDOWN = 40, REFILL_EVERY = 10;
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("6f8e2c1a-3b7d-4c55-9a1e-50b1ce6a7d11"), "[Golem Logix]");
    /** Click modes: break, use, attack. */
    public static final int LEFT = 0, RIGHT = 1, ATTACK = 2;
    /** The bus whose golem is breaking a block or striking a creature right now: drops go into its network (onDrops, onLoot). */
    @Nullable
    private static AnnihilationBusBlockEntity breaking;

    private final OwnedContainer filters = new OwnedContainer(FILTER_SLOTS, this, s -> true);
    private final ToolContainer tool = new ToolContainer();
    /** What the tool slot is refilled with (one unworn item): what the player last put there; empty once taken out. */
    private ItemStack refill = ItemStack.EMPTY;
    private long lastCraftRequest = -1000;

    /** The tool slot; the window calls {@link #forget()} when the player empties it so refilling stops. */
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

        @Override
        public boolean stillValid(Player player) {
            if (isRemoved() || getLevel() != player.level()) {
                return false;
            }
            return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
        }

        /** Updates the slot from internal work (wear, refill, rejected harvest) without changing what the slot refills with. */
        void setInternal(ItemStack stack) {
            getItems().set(0, stack);
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
    /** A new bus works on everything (empty deny list); an empty allow list stops it. */
    private boolean denyList = true;
    /** Mining progress on the block in front (0..1), and the block it is for. */
    private float progress;
    @Nullable
    private BlockPos mining;
    /** For the renderer: whether the golem is swinging, wearing Plasma Temper, and when it last used its item. */
    private boolean swinging;
    private boolean belted;
    private long lastUse = -100;
    private int idleSteps;
    private long sleepUntil;
    private long nextBreakTry;
    private long fullStorageEpoch;

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
        nextBreakTry = 0;
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

    /** How often it clicks: once a bus interval; in ATTACK mode, at the weapon's own pace (20 / attack speed). */
    public int interval() {
        return mode == ATTACK
                ? AnnihilationTools.attackInterval(tool.getItem(0))
                : Math.max(1, GolemConfig.BUS_INTERVAL_TICKS.get());
    }

    static float mineSpeed(ItemStack with, BlockState state) {
        return AnnihilationTools.mineSpeed(with, state);
    }

    public static boolean grown(BlockState state) {
        return AnnihilationTools.grown(state);
    }

    public static boolean temperLifts(ItemStack with, BlockState state) {
        return AnnihilationTools.temperLifts(with, state);
    }

    public static ItemStack temperedHand(ItemStack with, BlockState state, HolderLookup.Provider registries) {
        return AnnihilationTools.temperedHand(with, state, registries);
    }

    public static ItemStack blessed(ItemStack hand, HolderLookup.Provider registries) {
        return AnnihilationTools.blessed(hand, registries);
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, AnnihilationBusBlockEntity be) {
        be.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        upgrades.dropStrays(level, pos);
        boolean hasRedstone = upgrades.has(UpgradeType.REDSTONE);
        boolean powered = BlockEntities.pollRedstone(level, pos, hasRedstone, redstone);
        long now = level.getGameTime();
        if (tempered() != belted) {
            belted = tempered();
            sync();
        }
        Direction facing = state.getValue(DirectionalBlock.FACING);
        BlockPos target = pos.relative(facing);
        boolean refillDue = refilling() && Math.floorMod(now + pos.asLong(), REFILL_EVERY) == 0;
        if (!refillDue && now < sleepUntil) {
            stopMining();
            return;
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        if (refillDue && net.isOnline() && net.isDeviceActive(pos) && refill(level, net)) {
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
                    idleSteps = 0;
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
                    idleSteps = 0;
                    redstone.allows(hasRedstone, powered, true);
                } else {
                    sleep(now);
                }
            }
            return;
        }
        tickMining(level, net, pos, target, facing, front, hasRedstone, powered, now);
    }

    private void tickMining(ServerLevel level, GolemNetwork net, BlockPos pos, BlockPos target, Direction facing,
                            BlockState front, boolean hasRedstone, boolean powered, long now) {
        boolean temper = tempered();
        ItemStack with = AnnihilationTools.toolFor(tool.getItem(0), front, temper);
        if (with.isEmpty() || !AnnihilationTools.canMine(level, target, front, with, temper, refilling())) {
            stopMining();
            sleep(now);
            return;
        }
        if (!target.equals(mining)) {
            stopMining();
            mining = target;
        }
        if (progress < 1f) {
            float speed = AnnihilationTools.mineSpeed(with, front);
            float hardness = front.getDestroySpeed(level, target);
            boolean right = AnnihilationTools.harvests(with, front, temper);
            progress += hardness <= 0 ? 1f : speed / hardness / (right ? 30f : 100f);
            setSwinging(true);
            level.destroyBlockProgress(fakeId(), target, progress >= 1f ? 9 : (int) (progress * 10f));
        }
        if (progress >= 1f && (now >= nextBreakTry || fullStorageEpoch != GolemNetwork.storageEpoch())) {
            if (breakBlock(level, net, pos, target, facing, front, with)) {
                idleSteps = 0;
                redstone.allows(hasRedstone, powered, true);
                stopMining();
            } else {
                // network full (or cannot harvest): avoid running Block.getDrops every tick until space frees or retry elapses
                nextBreakTry = now + 10;
                fullStorageEpoch = GolemNetwork.storageEpoch();
            }
        }
    }

    // ------------------------------------------------------------------ tool refill

    private static boolean sameKind(ItemStack a, ItemStack b) {
        return a.isDamageableItem() ? a.is(b.getItem()) : ItemStack.isSameItemSameComponents(a, b);
    }

    private boolean refilling() {
        return !refill.isEmpty() && upgrades.has(UpgradeType.CRAFTING);
    }

    /**
     * Autocraft Module: refills the tool slot from the network. Empty tool slots take the least worn tool of the same kind;
     * non-damageable items (bone meal, buckets) top up toward a stack. Missing items are ordered one at a time.
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
                    if (s.getDamageValue() == 0) {
                        break;
                    }
                }
            }
            ItemStack fresh = best == null ? ItemStack.EMPTY : net.extractItem(best.stack(), 1, false);
            if (fresh.isEmpty()) {
                order(level, net, ItemKey.of(refill));
                return false;
            }
            tool.setInternal(fresh);
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
        tool.setInternal(held.isEmpty() ? got : held.copyWithCount(held.getCount() + got.getCount()));
        return true;
    }

    private void order(ServerLevel level, GolemNetwork net, ItemKey key) {
        long now = level.getGameTime();
        if (now - lastCraftRequest < CRAFT_REQUEST_COOLDOWN || !net.canCraft(key) || net.busCraftPending(worldPosition, key)) {
            return;
        }
        lastCraftRequest = now;
        BlockEntities.requestCraft(net, worldPosition, key, 1L);
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
        nextBreakTry = 0;
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

    /** True when the block passes allow/deny filters and, if requested, is fully grown. */
    private boolean wanted(BlockState state) {
        if (grownOnly && !AnnihilationTools.grown(state)) {
            return false;
        }
        boolean fuzzy = upgrades.has(UpgradeType.FUZZY);
        ItemStack asItem = null;
        boolean any = false, listed = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filters.getItem(i);
            if (f.isEmpty()) {
                continue;
            }
            any = true;
            if (asItem == null) {
                asItem = new ItemStack(state.getBlock().asItem());
            }
            if (!asItem.isEmpty() && Fuzzy.matches(f, asItem, fuzzy)) {
                listed = true;
                break;
            }
        }
        return any ? listed != denyList : denyList;
    }

    private boolean tempered() {
        return upgrades.has(UpgradeType.TEMPER);
    }

    // ------------------------------------------------------------------ FakePlayer actions

    private FakePlayer golem(ServerLevel level, BlockPos pos, Direction facing, ItemStack hand) {
        FakePlayer p = FakePlayerFactory.get(level, PROFILE);
        Vec3 c = Vec3.atCenterOf(pos);
        p.moveTo(c.x, c.y - p.getEyeHeight(), c.z, facing.toYRot(), facing == Direction.UP ? -90f : facing == Direction.DOWN ? 90f : 0f);
        p.setItemInHand(InteractionHand.MAIN_HAND, hand);
        p.setShiftKeyDown(sneak);
        return p;
    }

    /** Breaks the block with {@code with}; drops go into the network. Returns false if drops would not all fit. */
    private boolean breakBlock(ServerLevel level, GolemNetwork net, BlockPos pos, BlockPos target, Direction facing, BlockState state,
                               ItemStack with) {
        boolean temper = tempered();
        boolean own = !tool.getItem(0).isEmpty() && !temper;
        ItemStack hand = temper ? AnnihilationTools.temperedHand(with, state, level.registryAccess())
                : own ? tool.getItem(0) : with.copy();
        FakePlayer p = golem(level, pos, facing, hand);
        try {
            if (state.requiresCorrectToolForDrops() && !hand.isCorrectToolForDrops(state)) {
                return false;
            }
            for (ItemStack drop : Block.getDrops(state, level, target, level.getBlockEntity(target), p, hand)) {
                if (!net.insertItem(drop, true).isEmpty()) {
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
                tool.setInternal(AnnihilationTools.unworn(before, p.getMainHandItem(), temper));
            }
        } finally {
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.setShiftKeyDown(false);
        }
        sweep(level, net, new AABB(target).inflate(0.25));
        return true;
    }

    /** Right click: uses the slotted item (or empty hand) on the block; harvested items go into the network. */
    private boolean use(ServerLevel level, GolemNetwork net, BlockPos pos, BlockPos target, Direction facing) {
        ItemStack before = tool.getItem(0).copy();
        FakePlayer p = golem(level, pos, facing, before.copy());
        InteractionResult result;
        try {
            Vec3 hitAt = Vec3.atCenterOf(target).relative(facing.getOpposite(), 0.5);
            BlockHitResult hit = new BlockHitResult(hitAt, facing.getOpposite(), target, false);
            result = p.gameMode.useItemOn(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            if (!result.consumesAction() && !p.getMainHandItem().isEmpty() && !level.getFluidState(target).isEmpty()) {
                // buckets and bottles scoop fluids via item use raycast from the front face
                Vec3 eye = Vec3.atCenterOf(pos).relative(facing, 0.55);
                p.moveTo(eye.x, eye.y - p.getEyeHeight(), eye.z, p.getYRot(), p.getXRot());
                result = p.gameMode.useItem(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND);
            }
            ItemStack after = p.getMainHandItem().copy();
            if (!before.isEmpty() && !after.isEmpty() && !after.is(before.getItem()) && !before.isDamageableItem()) {
                // transformed item (e.g. empty bucket -> water bucket) enters the network; setInternal preserves refill if full
                after = net.insertItem(after, false);
            }
            tool.setInternal(AnnihilationTools.unworn(before, after, tempered()));
        } finally {
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            drainFakeInventory(level, net, pos, p);
            p.setShiftKeyDown(false);
        }
        sweep(level, net, new AABB(target).inflate(0.25));
        boolean did = result.consumesAction();
        if (did) {
            lastUse = level.getGameTime();
            sync();
        }
        return did;
    }

    /** Empties anything the FakePlayer picked up during right-click into the network (dropping overflow). */
    private static void drainFakeInventory(ServerLevel level, GolemNetwork net, BlockPos pos, FakePlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                inv.setItem(i, ItemStack.EMPTY);
                ItemStack rest = net.insertItem(s, false);
                if (!rest.isEmpty()) {
                    Block.popResource(level, pos, rest);
                }
            }
        }
    }

    /** True when the creature may be struck: mortal, non-player, unnamed, unowned, matching spawn-egg filters and age. */
    private boolean prey(LivingEntity e) {
        if (!e.isAlive() || e.isInvulnerable() || e instanceof Player
                || e instanceof ArmorStand || e.hasCustomName()
                || e instanceof OwnableEntity own && own.getOwnerUUID() != null) {
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
            if (f.getItem() instanceof SpawnEggItem egg && egg.getType(f) == e.getType()) {
                listed = true;
                break;
            }
        }
        return any ? listed != denyList : denyList;
    }

    /** Strikes every valid creature in front at full strength without knockback. Returns false if none present. */
    private boolean attack(ServerLevel level, BlockPos pos, BlockPos target, Direction facing) {
        var victims = level.getEntitiesOfClass(LivingEntity.class, new AABB(target), this::prey);
        if (victims.isEmpty()) {
            return false;
        }
        boolean temper = tempered();
        ItemStack slotted = tool.getItem(0);
        boolean own = !slotted.isEmpty();
        ItemStack plain = own ? slotted.copy() : ItemStack.EMPTY;
        ItemStack hand = own ? slotted.copy() : AnnihilationTools.ironSword().copy();
        if (temper) {
            hand = AnnihilationTools.blessed(hand, level.registryAccess());
        }
        FakePlayer p = golem(level, pos, facing, hand);
        // FakePlayer never ticks, so weapon attributes must be applied transiently around the strike
        List<Runnable> undo = new ArrayList<>();
        p.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
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
                e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
                e.hurtMarked = true;
            }
            if (own) {
                ItemStack after = temper ? plain : p.getMainHandItem().copy();
                tool.setInternal(AnnihilationTools.unworn(plain, after, temper));
            }
        } finally {
            breaking = null;
            undo.forEach(Runnable::run);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.setShiftKeyDown(false);
        }
        sweep(level, GolemNetwork.get(level, worldPosition), new AABB(target).inflate(1.0));
        lastUse = level.getGameTime();
        sync();
        return true;
    }

    /** Picks up newly spawned item entities in {@code box} (from mods bypassing drop events) into the network. */
    private void sweep(ServerLevel level, GolemNetwork net, AABB box) {
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && e.getAge() <= 2)) {
            ItemStack rest = net.insertItem(e.getItem(), false);
            if (rest.isEmpty()) {
                e.discard();
            } else {
                e.setItem(rest);
            }
        }
    }

    private static void collectDrops(Collection<ItemEntity> drops, GolemNetwork net) {
        drops.removeIf(e -> {
            ItemStack rest = net.insertItem(e.getItem(), false);
            if (rest.isEmpty()) {
                return true;
            }
            e.setItem(rest);
            return false;
        });
    }

    /** Captures mob loot from our golem at LOWEST priority so other mods' drop additions enter the network too. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLoot(LivingDropsEvent event) {
        AnnihilationBusBlockEntity bus = breaking;
        if (bus == null || !(event.getSource().getEntity() instanceof FakePlayer fp) || !PROFILE.getId().equals(fp.getUUID())
                || bus.level == null || event.getEntity().level() != bus.level) {
            return;
        }
        collectDrops(event.getDrops(), GolemNetwork.get(bus.level, bus.worldPosition));
    }

    /** Captures block drops from our golem at LOWEST priority so other mods' drop additions enter the network too. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(BlockDropsEvent event) {
        AnnihilationBusBlockEntity bus = breaking;
        if (bus == null || !(event.getBreaker() instanceof FakePlayer fp) || !PROFILE.getId().equals(fp.getUUID())
                || event.getLevel() != bus.level) {
            return;
        }
        collectDrops(event.getDrops(), GolemNetwork.get(event.getLevel(), bus.worldPosition));
    }

    /** Broken or replaced: stops cracks and drops tool and upgrades. */
    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        stopMining();
        BlockEntities.dropContainers(level, worldPosition, tool, upgrades);
    }

    private void sync() {
        BlockEntities.sendUpdate(this, 2);
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
                tag.put("Refill", refill.save(registries));
            }
        }
        if (!tool.getItem(0).isEmpty()) {
            tag.put("Tool", tool.getItem(0).save(registries));
        }
        tag.putInt("Mode", mode);
        tag.putBoolean("Swinging", swinging);
        tag.putBoolean("Belted", belted);
        tag.putLong("LastUse", lastUse);
    }

    private void loadTag(CompoundTag tag, HolderLookup.Provider registries, boolean full) {
        if (full) {
            filters.fromTag(tag.getList("Filters", Tag.TAG_COMPOUND), registries);
            upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
            redstone.load(tag);
            sneak = tag.getBoolean("Sneak");
            denyList = !tag.contains("DenyList") || tag.getBoolean("DenyList");
            if (!tag.getBoolean("EmptyAllowStops") && !denyList && filters.isEmpty()) {
                // saved when an empty allow list still meant "everything": keep working as it did
                denyList = true;
            }
            grownOnly = tag.getBoolean("GrownOnly");
            refill = tag.contains("Refill")
                    ? ItemStack.parse(registries, tag.getCompound("Refill")).orElse(ItemStack.EMPTY)
                    : ItemStack.EMPTY;
            if (refill.isEmpty() && tag.contains("Tool")) {
                // saved before refill existed: default to what sits in the slot
                refill = ItemStack.parse(registries, tag.getCompound("Tool")).orElse(ItemStack.EMPTY).copyWithCount(1);
                if (refill.isDamageableItem()) {
                    refill.setDamageValue(0);
                }
            }
        }
        ItemStack t = tag.contains("Tool")
                ? ItemStack.parse(registries, tag.getCompound("Tool")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        tool.getItems().set(0, t);
        mode = tag.contains("Mode") ? tag.getInt("Mode") : tag.getBoolean("RightClick") ? RIGHT : LEFT;
        swinging = tag.getBoolean("Swinging");
        belted = tag.getBoolean("Belted");
        lastUse = tag.contains("LastUse") ? tag.getLong("LastUse") : -100L;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveTag(tag, registries, true);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadTag(tag, registries, true);
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
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadTag(tag, registries, false);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }
}
