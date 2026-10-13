package com.golemlogix.blockentity;

import com.golemlogix.blocks.TelegraphBlock;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModSounds;
import com.golemlogix.items.TelegraphLinkCardItem;
import com.golemlogix.items.TelegraphRangeCardItem;
import com.golemlogix.menu.TelegraphMenu;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.TelegraphLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The main cell of a Cryo-Source Telegraph: the range card, the Write slot and the Memory slot (one excludes the other), and the
 * seal written on its cards (Reset gives a new one, so every card written before stops working). The link itself is resolved by
 * TelegraphLinks; the network walk follows it. Every second the server checks whether the telegraph works (linked and its network
 * is online) and tells the clients, which play the operator's Morse rhythm on their own: no per-tick traffic.
 */
public class TelegraphBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_RANGE = 0, SLOT_WRITE = 1, SLOT_MEMORY = 2;
    /** The Write slot takes a whole stack of linkage cards and writes them all at once; Range and Memory take one card (the menu). */
    public static final int WRITE_STACK = 16;

    private final OwnedContainer slots = new OwnedContainer(3, this, s -> true) {
        @Override
        public int getMaxStackSize() {
            return WRITE_STACK;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return mayPlace(slot, stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged();
        }
    };
    /** The seal written on this telegraph's cards. 0 = not chosen yet. */
    private int seal;
    /** Server: working now (as sent to clients), the partners of the last link state (they are told when it changes). */
    private boolean active;
    private List<GolemNetwork.Node> lastEnds = new ArrayList<>();
    private boolean announce;

    // --- client
    private boolean clientActive;
    /** 0..1: the telegraph wakes up and falls asleep smoothly (renderer). */
    public float wake, wakePrev;

    public TelegraphBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TELEGRAPH.get(), pos, state);
    }

    // ------------------------------------------------------------------ slots

    public boolean mayPlace(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_RANGE -> stack.getItem() instanceof TelegraphRangeCardItem;
            case SLOT_WRITE -> TelegraphLinkCardItem.isCard(stack) && slots.getItem(SLOT_MEMORY).isEmpty();
            case SLOT_MEMORY -> TelegraphLinkCardItem.isCard(stack) && slots.getItem(SLOT_WRITE).isEmpty();
            default -> false;
        };
    }

    public OwnedContainer slots() {
        return slots;
    }

    public ItemStack memoryCard() {
        return slots.getItem(SLOT_MEMORY);
    }

    /** 0 without a range card, 1 the whole dimension, 2 any dimension. */
    public int rangeTier() {
        return TelegraphRangeCardItem.tierOf(slots.getItem(SLOT_RANGE));
    }

    public int seal() {
        if (seal == 0 && level != null && !level.isClientSide()) {
            seal = newSeal(0);
            setChanged();
        }
        return seal;
    }

    private static int newSeal(int old) {
        int s;
        do {
            s = java.util.concurrent.ThreadLocalRandom.current().nextInt();
        } while (s == 0 || s == old);
        return s;
    }

    public GlobalPos globalPos() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    /** A card in the Write slot always holds this telegraph; the link state may have changed: tell both ends of every link. */
    private void slotsChanged() {
        if (level == null || level.isClientSide()) {
            return;
        }
        ItemStack write = slots.getItem(SLOT_WRITE);
        if (TelegraphLinkCardItem.isCard(write)) {
            TelegraphLinkCardItem.Link mine = new TelegraphLinkCardItem.Link(globalPos(), seal());
            if (!mine.equals(TelegraphLinkCardItem.link(write))) {
                write.set(ModDataComponents.TELEGRAPH_LINK.get(), mine);
            }
        }
        linksChanged();
    }

    /** Re-registers the link and makes the networks at this telegraph and at the old and new partners recompute. */
    private void linksChanged() {
        TelegraphLinks.track(this);
        List<GolemNetwork.Node> ends = ends();
        GolemNetwork.nodesChanged(level, worldPosition);
        for (GolemNetwork.Node n : lastEnds) {
            GolemNetwork.nodesChanged(n.level(), n.pos());
        }
        for (GolemNetwork.Node n : ends) {
            GolemNetwork.nodesChanged(n.level(), n.pos());
        }
        lastEnds = ends;
    }

    private List<GolemNetwork.Node> ends() {
        List<GolemNetwork.Node> out = new ArrayList<>();
        for (TelegraphLinks.Partner p : TelegraphLinks.partners(level, worldPosition)) {
            out.add(p.node());
        }
        // the host this card names, even when the link does not work (it may have just stopped working)
        TelegraphLinkCardItem.Link link = TelegraphLinkCardItem.link(memoryCard());
        if (link != null && level.getServer() != null) {
            Level hostLevel = level.getServer().getLevel(link.target().dimension());
            if (hostLevel != null) {
                out.add(new GolemNetwork.Node(hostLevel, link.target().pos()));
            }
        }
        return out;
    }

    /**
     * The big Reset button: erases the card in the Memory slot, gives the telegraph a new seal (every card written for it stops
     * working, its receivers drop) and writes the card in the Write slot again with the new seal.
     */
    public void reset() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (TelegraphBlockEntity r : TelegraphLinks.receiversOf(this)) {
            lastEnds.add(new GolemNetwork.Node(r.getLevel(), r.getBlockPos()));
        }
        seal = newSeal(seal);
        ItemStack memory = slots.getItem(SLOT_MEMORY);
        if (!memory.isEmpty()) {
            memory.remove(ModDataComponents.TELEGRAPH_LINK.get());
        }
        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5f, 1.6f);
        slots.setChanged();
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < slots.getContainerSize(); i++) {
            ItemStack s = slots.removeItemNoUpdate(i);
            if (!s.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, s);
            }
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            TelegraphLinks.track(this);
            // the networks on both sides are told on the first tick, not in the middle of loading a chunk
            announce = true;
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) {
            List<GolemNetwork.Node> ends = new ArrayList<>(lastEnds);
            for (TelegraphBlockEntity r : TelegraphLinks.receiversOf(this)) {
                ends.add(new GolemNetwork.Node(r.getLevel(), r.getBlockPos()));
            }
            TelegraphLinks.untrack(this);
            GolemNetwork.nodesChanged(level, worldPosition);
            for (GolemNetwork.Node n : ends) {
                GolemNetwork.nodesChanged(n.level(), n.pos());
            }
        }
        super.setRemoved();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TelegraphBlockEntity be) {
        if (be.announce) {
            be.announce = false;
            be.linksChanged();
        }
        if ((level.getGameTime() + pos.asLong()) % 20 != 0) {
            return;
        }
        boolean linked = TelegraphLinks.status(be) == TelegraphLinks.Status.LINKED || TelegraphLinks.linkedReceivers(be) > 0;
        boolean on = linked && GolemNetwork.get(level, pos).isOnline();
        if (on != be.active) {
            be.active = on;
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    public boolean isActive() {
        return level != null && level.isClientSide() ? clientActive : active;
    }

    // ------------------------------------------------------------------ the operator's rhythm (client)

    /** Ticks of one Morse unit: the operator keys fast and crisp. */
    public static final int UNIT = 2;
    /** At the start of every message the incoming Source strikes the top crystal; the keying starts after it. */
    public static final int FLASH = 12;
    private static final boolean[] MESSAGE = morse("GOLEM");
    public static final int CYCLE = FLASH + MESSAGE.length * UNIT + 20 * UNIT;

    private static boolean[] morse(String text) {
        java.util.Map<Character, String> code = java.util.Map.of('G', "--.", 'O', "---", 'L', ".-..", 'E', ".", 'M', "--");
        List<Boolean> units = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            String c = code.get(text.charAt(i));
            for (int k = 0; k < c.length(); k++) {
                int len = c.charAt(k) == '-' ? 3 : 1;
                for (int u = 0; u < len; u++) {
                    units.add(true);
                }
                if (k < c.length() - 1) {
                    units.add(false);
                }
            }
            if (i < text.length() - 1) {
                for (int u = 0; u < 3; u++) {
                    units.add(false);
                }
            }
        }
        boolean[] out = new boolean[units.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = units.get(i);
        }
        return out;
    }

    /** The key is pressed at this tick of the cycle (time in ticks, any value). */
    public static boolean keyDown(long time) {
        long t = Math.floorMod(time, CYCLE) - FLASH;
        if (t < 0) {
            return false;
        }
        long u = t / UNIT;
        return u < MESSAGE.length && MESSAGE[(int) u];
    }

    /** The key went down at exactly this tick. */
    public static boolean keyPressed(long time) {
        return keyDown(time) && !keyDown(time - 1);
    }

    /** 0..1 flash of the top crystal at the start of each message. */
    public static float crystalFlash(float time) {
        float t = time - (float) Math.floor(time / CYCLE) * CYCLE;
        return t < FLASH + 6 ? 1f - Math.max(0f, t - 2f) / (FLASH + 4f) : 0f;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TelegraphBlockEntity be) {
        be.wakePrev = be.wake;
        be.wake = Math.max(0f, Math.min(1f, be.wake + (be.clientActive ? 0.05f : -0.03f)));
        if (be.wake < 0.95f) {
            return;
        }
        long time = level.getGameTime() + pos.asLong() % 97;
        if (Math.floorMod(time, CYCLE) == 0) {
            level.playLocalSound(pos, ModSounds.VACUUM_HUM.get(), SoundSource.BLOCKS, 0.25f, 1.6f, false);
        }
        if (keyPressed(time)) {
            level.playLocalSound(pos, ModSounds.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.12f, 1.9f, false);
        }
    }

    // ------------------------------------------------------------------ menu

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> TelegraphLinks.status(TelegraphBlockEntity.this).ordinal();
                case 1 -> TelegraphLinks.linkedReceivers(TelegraphBlockEntity.this);
                case 2 -> active ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.golem_logix.cryo_source_telegraph");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new TelegraphMenu(id, inv, this, slots, data, ContainerLevelAccess.create(level, worldPosition));
    }

    // ------------------------------------------------------------------ save and sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Slots", slots.createTag(registries));
        tag.putInt("Seal", seal);
        tag.putBoolean("Active", active);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        slots.fromTag(tag.getList("Slots", Tag.TAG_COMPOUND), registries);
        seal = tag.getInt("Seal");
        active = tag.getBoolean("Active");
        clientActive = active;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Active", active);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientActive = tag.getBoolean("Active");
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The cell of a telegraph block entity is always the main one. */
    public TelegraphBlock.Part part() {
        return TelegraphBlock.Part.BASE;
    }
}
