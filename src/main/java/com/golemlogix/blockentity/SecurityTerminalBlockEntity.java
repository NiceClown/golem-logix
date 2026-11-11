package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.items.IdentityPunchCardItem;
import com.golemlogix.security.SecurityPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class SecurityTerminalBlockEntity extends BlockEntity {
    public static final int SLOTS = 16;

    private UUID ownerId;
    private String ownerName = "";
    private final OwnedContainer cards = new OwnedContainer(SLOTS, this, s -> s.getItem() instanceof IdentityPunchCardItem);

    public SecurityTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SECURITY_TERMINAL.get(), pos, state);
    }

    public void setOwner(Player player) {
        this.ownerId = player.getUUID();
        this.ownerName = player.getScoreboardName();
        setChanged();
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Container getCards() {
        return cards;
    }

    public boolean isOwner(Player player) {
        return ownerId != null && ownerId.equals(player.getUUID());
    }

    public boolean hasPermission(Player player, SecurityPermission perm) {
        if (isOwner(player)) {
            return true;
        }
        if (player.canUseGameMasterBlocks()) {
            return true;
        }
        UUID uuid = player.getUUID();
        for (int i = 0; i < cards.getContainerSize(); i++) {
            ItemStack stack = cards.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof IdentityPunchCardItem && IdentityPunchCardItem.isBound(stack)) {
                if (uuid.equals(IdentityPunchCardItem.getOwnerId(stack))) {
                    int perms = IdentityPunchCardItem.getPermissions(stack);
                    if (perm.test(perms)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void togglePermission(int slot, SecurityPermission perm) {
        if (slot >= 0 && slot < cards.getContainerSize()) {
            ItemStack stack = cards.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof IdentityPunchCardItem && IdentityPunchCardItem.isBound(stack)) {
                int perms = IdentityPunchCardItem.getPermissions(stack);
                int toggled = perms ^ perm.mask;
                IdentityPunchCardItem.setPermissions(stack, toggled);
                cards.setItem(slot, stack);
                setChanged();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerId != null) {
            tag.putString("OwnerId", ownerId.toString());
        }
        tag.putString("OwnerName", ownerName);
        tag.put("Cards", cards.createTag(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("OwnerId", Tag.TAG_STRING)) {
            try {
                ownerId = UUID.fromString(tag.getString("OwnerId"));
            } catch (Exception ignored) {}
        }
        ownerName = tag.getString("OwnerName");
        cards.fromTag(tag.getList("Cards", Tag.TAG_COMPOUND), registries);
    }
}
