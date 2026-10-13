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
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Punch Card Catalog: storage for punch cards with crafting-table recipes (the Cryo-Calculators run them themselves, no machine needed).
 * Capacity comes from the Pattern Providers in a separate slot: each gives {@link #slotsPerProvider()} places, up to 64 providers.
 * A Redstone Module can switch the catalog's patterns on and off by signal. The former blocks of the assembled cabinet are kept here.
 */
public class CardCatalogBlockEntity extends BlockEntity {
    public static final int MAX_PROVIDERS = 64, MAX_SLOTS = MAX_PROVIDERS * 27;
    public static final int EVENT_VIEWERS = 1;

    private final OwnedContainer cards = new OwnedContainer(MAX_SLOTS, this, CardCatalogBlockEntity::isCraftingCard) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            patterns = null;
            summaryDirty = true;
            com.golemlogix.system.GolemNetwork.patternsChanged();
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
            patterns = null;
            summaryDirty = true;
            com.golemlogix.system.GolemNetwork.patternsChanged();
        }
    };
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.REDSTONE_ONLY, this);
    private final RedstoneControl redstone = new RedstoneControl();
    private final BlockState[] originals = new BlockState[CatalogStructure.CELLS.length];
    @Nullable
    private List<PatternData> patterns;
    private boolean enabled = true;
    private int viewers = 0;
    private long lastSync = -100;
    private String sentSummary = "";
    /** The cards or providers changed since the last sync check (counting 1728 places every tick was wasted work). */
    private boolean summaryDirty = true;

    // --- client
    private int clientCards, clientCapacity;
    private boolean clientOpen;
    private final int[] clientGolems = {0, 0};
    /** Pulled-out drawers 0..1 and rotation of the letter drums on the roof (client). */
    public float drawers, drawersPrev, drums, drumsPrev;

    public CardCatalogBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CARD_CATALOG.get(), pos, state);
    }

    /** A card with a crafting-table recipe (machine recipes need a provider next to the machine, they do not go into the catalog). */
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

    /** How many card places there are now (by the number of providers). */
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

    /** Fewer providers than this cannot be left: otherwise occupied places would end up beyond the capacity. */
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

    /** Catalog patterns for the network: only within capacity, when assembled and allowed by redstone. */
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

    /** Oxidation of the golem on this side (0 = left, 1 = right), taken from the statue it was built from. */
    public int golemOxidation(int side) {
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
        return viewers;
    }

    public void startOpen() {
        viewers++;
        if (viewers == 1 && level != null) {
            level.playSound(null, worldPosition, SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 0.8f, 0.9f);
        }
        signalViewers();
    }

    public void stopOpen() {
        viewers = Math.max(0, viewers - 1);
        if (viewers == 0 && level != null) {
            level.playSound(null, worldPosition, SoundEvents.CHISELED_BOOKSHELF_INSERT, SoundSource.BLOCKS, 0.8f, 0.9f);
        }
        signalViewers();
    }

    private void signalViewers() {
        if (level != null && !level.isClientSide()) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_VIEWERS, viewers);
        }
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_VIEWERS) {
            clientOpen = param > 0;
            return true;
        }
        return super.triggerEvent(id, param);
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        for (int i = 0; i < cards.getContainerSize(); i++) {
            if (!cards.getItem(i).isEmpty()) {
                Containers.dropItemStack(level, x, y, z, cards.removeItemNoUpdate(i));
            }
        }
        Containers.dropContents(level, worldPosition, providers);
        Containers.dropContents(level, worldPosition, upgrades);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CardCatalogBlockEntity be) {
        long time = level.getGameTime();
        Direction facing = state.getValue(CardCatalogBlock.FACING);
        if ((time + pos.asLong()) % 20 == 0) {
            boolean valid = CatalogStructure.valid(level, pos, facing);
            if (be.formed() && !valid) {
                CatalogStructure.disassemble(level, pos, facing, null);
            } else if (!be.formed() && valid) {
                CatalogStructure.form(level, pos, facing);
            }
        }
        boolean module = be.upgrades.has(UpgradeType.REDSTONE);
        boolean powered = module && level.hasNeighborSignal(pos);
        boolean enabled = !module || switch (be.redstone.mode()) {
            case IGNORE -> true;
            case HIGH, PULSE -> powered;
            case LOW -> !powered;
        };
        if (enabled != be.enabled) {
            be.enabled = enabled;
            be.patterns = null;
            GolemNetwork.clearCache();
        }
        if (be.summaryDirty && time - be.lastSync >= 10) {
            be.summaryDirty = false;
            if (!be.summary().equals(be.sentSummary)) {
                be.syncNow();
            }
        }
    }

    private String summary() {
        return cardCount() + "/" + capacity();
    }

    public void syncNow() {
        if (level != null && !level.isClientSide()) {
            lastSync = level.getGameTime();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CardCatalogBlockEntity be) {
        be.drawersPrev = be.drawers;
        be.drawers = Math.max(0f, Math.min(1f, be.drawers + (be.clientOpen ? 0.08f : -0.06f)));
        be.drumsPrev = be.drums;
        // the drums click to the next letter every second and a half; while the window is open they flip quickly
        be.drums += be.clientOpen ? 0.12f : ((level.getGameTime() + pos.asLong()) % 30 < 6 ? 0.05f : 0f);
        // steam from the roof pipe: in puffs while the window is open, now and then when idle
        if (be.formed() && level.getGameTime() % (be.clientOpen ? 4 : 23) == 0) {
            var r = level.random;
            Direction f = state.getValue(CardCatalogBlock.FACING);
            double a = Math.toRadians(180 - f.toYRot()), dx = 23.3 / 16 - 0.5, dz = 23.3 / 16 - 0.5;
            double wx = dx * Math.cos(a) + dz * Math.sin(a), wz = -dx * Math.sin(a) + dz * Math.cos(a);
            level.addParticle(be.clientOpen ? net.minecraft.core.particles.ParticleTypes.CLOUD : net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE,
                    pos.getX() + 0.5 + wx + (r.nextDouble() - 0.5) * 0.15, pos.getY() + 2.85, pos.getZ() + 0.5 + wz + (r.nextDouble() - 0.5) * 0.15,
                    0, 0.04, 0);
        }
    }

    public int clientCards() {
        return clientCards;
    }

    public int clientCapacity() {
        return clientCapacity;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
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

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cards.fromTag(tag.getList("Cards", Tag.TAG_COMPOUND), registries);
        providers.fromTag(tag.getList("Providers", Tag.TAG_COMPOUND), registries);
        upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
        java.util.Arrays.fill(originals, null);
        ListTag orig = tag.getList("Originals", Tag.TAG_COMPOUND);
        for (int i = 0; i < orig.size(); i++) {
            CompoundTag e = orig.getCompound(i);
            int cell = e.getInt("Cell");
            if (cell >= 0 && cell < originals.length) {
                originals[cell] = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), e.getCompound("State"));
            }
        }
        redstone.load(tag);
        patterns = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("CardCount", cardCount());
        tag.putInt("Capacity", capacity());
        tag.putBoolean("Open", viewers > 0);
        tag.putInt("GolemL", golemOxidation(0));
        tag.putInt("GolemR", golemOxidation(1));
        sentSummary = summary();
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientCards = tag.getInt("CardCount");
        clientCapacity = tag.getInt("Capacity");
        clientOpen = tag.getBoolean("Open");
        clientGolems[0] = tag.getInt("GolemL");
        clientGolems[1] = tag.getInt("GolemR");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
