package com.golemlogix.blockentity;

import com.golemlogix.blocks.CardCatalogBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.system.CatalogStructure;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Punch Card Catalog: storage for crafting-table punch cards. Capacity scales with installed Pattern Providers
 * ({@link #slotsPerProvider()} each, up to 64 providers); an optional Redstone Module gates pattern visibility.
 */
public class CardCatalogBlockEntity extends BlockEntity {
    public static final int MAX_PROVIDERS = 64, MAX_SLOTS = MAX_PROVIDERS * 27;
    public static final int EVENT_VIEWERS = ViewerTracker.EVENT_VIEWERS;

    private final OwnedContainer cards = new OwnedContainer(MAX_SLOTS, this, CardCatalogBlockEntity::isCraftingCard) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            invalidatePatterns();
        }
    };
    private final OwnedContainer providers = new OwnedContainer(1, this, s -> s.is(ModBlocks.PATTERN_PROVIDER.get().asItem())) {
        @Override
        public int getMaxStackSize() {
            return MAX_PROVIDERS;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return MAX_PROVIDERS;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            invalidatePatterns();
        }
    };
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.REDSTONE_ONLY, this);
    private final RedstoneControl redstone = new RedstoneControl();
    private final ViewerTracker viewerTracker = new ViewerTracker();
    private final BlockState[] originals = new BlockState[CatalogStructure.CELLS.length];
    @Nullable
    private List<PatternData> patterns;
    private boolean enabled = true;
    private long lastSync = -100;
    private int sentCards = -1, sentCapacity = -1;
    /** Cards or providers changed since the last sync check (avoids scanning 1728 slots every tick). */
    private boolean summaryDirty = true;

    // --- client
    private int clientCards, clientCapacity;
    private final int[] clientGolems = {0, 0};
    /** Pulled-out drawers 0..1 and roof letter-drum angle (client). */
    public float drawers, drawersPrev, drums, drumsPrev;

    public CardCatalogBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CARD_CATALOG.get(), pos, state);
    }

    private void invalidatePatterns() {
        patterns = null;
        summaryDirty = true;
        if (level == null || !level.isClientSide()) {
            GolemNetwork.patternsChanged();
        }
    }

    public static boolean isCraftingCard(ItemStack stack) {
        PatternData data = stack.get(ModDataComponents.PATTERN.get());
        return data != null && data.isValid() && data.crafting();
    }

    public static int slotsPerProvider() {
        return GolemConfig.CATALOG_SLOTS_PER_PROVIDER.get();
    }

    public OwnedContainer getCards() {
        return cards;
    }

    public OwnedContainer getProviders() {
        return providers;
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    public boolean formed() {
        BlockState s = getBlockState();
        return s.hasProperty(CardCatalogBlock.FORMED) && s.getValue(CardCatalogBlock.FORMED);
    }

    public int capacity() {
        return Math.min(MAX_SLOTS, providers.getItem(0).getCount() * slotsPerProvider());
    }

    public int cardCount() {
        int n = 0;
        for (int i = 0; i < cards.getContainerSize(); i++) {
            if (!cards.getItem(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** Minimum provider count required to keep every currently occupied card slot within capacity. */
    public int minProviders() {
        int last = -1;
        for (int i = cards.getContainerSize() - 1; i >= 0; i--) {
            if (!cards.getItem(i).isEmpty()) {
                last = i;
                break;
            }
        }
        return last < 0 ? 0 : last / slotsPerProvider() + 1;
    }

    /** Patterns exposed to the network: only within capacity, when formed and enabled by redstone. */
    public List<PatternData> patterns() {
        if (!formed() || !enabled) {
            return List.of();
        }
        if (patterns == null) {
            List<PatternData> list = new ArrayList<>();
            int cap = capacity();
            for (int i = 0; i < cap; i++) {
                PatternData data = cards.getItem(i).get(ModDataComponents.PATTERN.get());
                if (data != null && data.isValid() && data.crafting()) {
                    list.add(data);
                }
            }
            patterns = list;
        }
        return patterns;
    }

    public void rememberOriginal(int cell, BlockState state) {
        originals[cell] = state;
        setChanged();
    }

    @Nullable
    public BlockState original(int cell) {
        return cell >= 0 && cell < originals.length ? originals[cell] : null;
    }

    /** Oxidation stage of the side golem (0 = left, 1 = right), read from the original statue block. */
    public int golemOxidation(int side) {
        if (side < 0 || side > 1) {
            return 0;
        }
        if (level != null && level.isClientSide()) {
            return clientGolems[side];
        }
        BlockState s = originals[side == 0 ? 12 : 14];
        return s == null ? 0 : CatalogStructure.oxidation(s);
    }

    /** Window data: 0 = capacity, 1 = cards, 2 = redstone mode. */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> capacity();
                    case 1 -> cardCount();
                    default -> redstone.mode().ordinal();
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 2) {
                    redstone.setMode(RedstoneMode.byId(value));
                    setChanged();
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    public int viewers() {
        return viewerTracker.viewers();
    }

    public void startOpen() {
        viewerTracker.open(this, () ->
                level.playSound(null, worldPosition, SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 0.8f, 0.9f));
    }

    public void stopOpen() {
        viewerTracker.close(this, () ->
                level.playSound(null, worldPosition, SoundEvents.CHISELED_BOOKSHELF_INSERT, SoundSource.BLOCKS, 0.8f, 0.9f));
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        return viewerTracker.handleEvent(id, param) || super.triggerEvent(id, param);
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        for (int i = 0; i < cards.getContainerSize(); i++) {
            if (!cards.getItem(i).isEmpty()) {
                Containers.dropItemStack(level, x, y, z, cards.removeItemNoUpdate(i));
            }
        }
        BlockEntities.dropContainers(level, worldPosition, providers, upgrades);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CardCatalogBlockEntity be) {
        long time = level.getGameTime();
        Direction facing = state.getValue(CardCatalogBlock.FACING);
        if ((time + pos.asLong()) % 20 == 0
                && BlockEntities.allLoaded(level, CatalogStructure.CELLS.length, i -> CatalogStructure.cellPos(pos, facing, i))) {
            boolean valid = CatalogStructure.valid(level, pos, facing);
            if (be.formed() && !valid) {
                CatalogStructure.disassemble(level, pos, facing, null);
            } else if (!be.formed() && valid) {
                CatalogStructure.form(level, pos, facing);
            }
        }
        boolean module = be.upgrades.has(UpgradeType.REDSTONE);
        boolean powered = module && level.hasNeighborSignal(pos);
        boolean enabledNow = !module || switch (be.redstone.mode()) {
            case IGNORE -> true;
            case HIGH, PULSE -> powered;
            case LOW -> !powered;
        };
        if (enabledNow != be.enabled) {
            be.enabled = enabledNow;
            be.patterns = null;
            GolemNetwork.patternsChanged();
        }
        if (be.summaryDirty && time - be.lastSync >= 10) {
            be.summaryDirty = false;
            if (be.cardCount() != be.sentCards || be.capacity() != be.sentCapacity) {
                be.syncNow();
            }
        }
    }

    public void syncNow() {
        if (level != null && !level.isClientSide()) {
            lastSync = level.getGameTime();
            BlockEntities.sendUpdate(this);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CardCatalogBlockEntity be) {
        boolean open = be.viewerTracker.isClientOpen();
        be.drawersPrev = be.drawers;
        be.drawers = Math.max(0f, Math.min(1f, be.drawers + (open ? 0.08f : -0.06f)));
        be.drumsPrev = be.drums;
        be.drums += open ? 0.12f : (Math.floorMod(level.getGameTime() + pos.asLong(), 30) < 6 ? 0.05f : 0f);
        if (be.formed() && level.getGameTime() % (open ? 4 : 23) == 0) {
            var r = level.getRandom();
            Direction f = state.getValue(CardCatalogBlock.FACING);
            double a = Math.toRadians(180 - f.toYRot()), dx = 23.3 / 16 - 0.5, dz = 23.3 / 16 - 0.5;
            double wx = dx * Math.cos(a) + dz * Math.sin(a), wz = -dx * Math.sin(a) + dz * Math.cos(a);
            level.addParticle(open ? ParticleTypes.CLOUD : ParticleTypes.WHITE_SMOKE,
                    pos.getX() + 0.5 + wx + (r.nextDouble() - 0.5) * 0.15, pos.getY() + 2.85,
                    pos.getZ() + 0.5 + wz + (r.nextDouble() - 0.5) * 0.15, 0, 0.04, 0);
        }
    }

    public int clientCards() {
        return clientCards;
    }

    public int clientCapacity() {
        return clientCapacity;
    }

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Cards", cards.createTag(registries));
        tag.put("Providers", providers.createTag(registries));
        tag.put("Upgrades", upgrades.createTag(registries));
        ListTag orig = new ListTag();
        for (int i = 0; i < originals.length; i++) {
            if (originals[i] != null) {
                CompoundTag e = new CompoundTag();
                e.putInt("Cell", i);
                e.put("State", NbtUtils.writeBlockState(originals[i]));
                orig.add(e);
            }
        }
        tag.put("Originals", orig);
        redstone.save(tag);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        cards.fromTag(tag.getListOrEmpty("Cards"), registries);
        providers.fromTag(tag.getListOrEmpty("Providers"), registries);
        upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
        Arrays.fill(originals, null);
        ListTag orig = tag.getListOrEmpty("Originals");
        for (int i = 0; i < orig.size(); i++) {
            CompoundTag e = orig.getCompoundOrEmpty(i);
            int cell = e.getIntOr("Cell", 0);
            if (cell >= 0 && cell < originals.length) {
                originals[cell] = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), e.getCompoundOrEmpty("State"));
            }
        }
        redstone.load(tag);
        patterns = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        int count = cardCount();
        int cap = capacity();
        tag.putInt("CardCount", count);
        tag.putInt("Capacity", cap);
        tag.putBoolean("Open", viewerTracker.isOpen());
        tag.putInt("GolemL", golemOxidation(0));
        tag.putInt("GolemR", golemOxidation(1));
        sentCards = count;
        sentCapacity = cap;
        return tag;
    }

    public void readUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientCards = tag.getIntOr("CardCount", 0);
        clientCapacity = tag.getIntOr("Capacity", 0);
        viewerTracker.setClientOpen(tag.getBooleanOr("Open", false));
        clientGolems[0] = tag.getIntOr("GolemL", 0);
        clientGolems[1] = tag.getIntOr("GolemR", 0);
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
    public void handleUpdateTag(ValueInput input) {
        BlockEntities.load(input, this::readUpdateTag);
    }

    @Override
    public void onDataPacket(Connection net, ValueInput input) {
        handleUpdateTag(input);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        BlockEntities.onPreRemove(this, pos, state);
    }
}
