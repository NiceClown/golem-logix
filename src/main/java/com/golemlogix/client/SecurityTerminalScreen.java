package com.golemlogix.client;

import com.golemlogix.items.IdentityPunchCardItem;
import com.golemlogix.menu.SecurityTerminalMenu;
import com.golemlogix.network.SecurityTogglePayload;
import com.golemlogix.security.SecurityPermission;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Security Terminal window: the identity cards of the players let into the network; a click picks a card, and four icon
 * toggles under the cards set what its player may do (put in, take out, order crafts, build and open the network's blocks).
 * The owner's name at the bottom; without a card a player may do nothing.
 */
public class SecurityTerminalScreen extends GolemScreen<SecurityTerminalMenu> {
    private int selectedSlot = 0;

    public SecurityTerminalScreen(SecurityTerminalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, SecurityTerminalMenu.IMAGE_WIDTH, SecurityTerminalMenu.IMAGE_HEIGHT);
    }

    private ItemStack selected() {
        return selectedSlot >= 0 && selectedSlot < menu.getCards().getContainerSize() ? menu.getCards().getItem(selectedSlot) : ItemStack.EMPTY;
    }

    private boolean has(SecurityPermission perm) {
        ItemStack card = selected();
        return !card.isEmpty() && IdentityPunchCardItem.isBound(card) && perm.test(IdentityPunchCardItem.getPermissions(card));
    }

    @Override
    protected void init() {
        super.init();
        Object[][] perms = {{SecurityPermission.INSERT, Items.HOPPER, "insert"}, {SecurityPermission.EXTRACT, Items.DROPPER, "extract"},
                {SecurityPermission.CRAFT, Items.CRAFTING_TABLE, "craft"}, {SecurityPermission.BUILD, Items.IRON_PICKAXE, "build"}};
        for (int i = 0; i < perms.length; i++) {
            SecurityPermission perm = (SecurityPermission) perms[i][0];
            ItemStack icon = new ItemStack((net.minecraft.world.item.Item) perms[i][1]);
            String key = "gui.golem_logix.security.perm." + perms[i][2];
            addRenderableWidget(new IconButton(leftPos + 52 + i * 22, topPos + 72, () -> icon,
                    () -> Component.translatable(key).append("\n").append(Component.translatable(has(perm) ? "gui.golem_logix.security.on"
                            : "gui.golem_logix.security.off").withStyle(has(perm) ? ChatFormatting.GREEN : ChatFormatting.RED))
                            .append("\n").append(Component.translatable(key + ".hint").withStyle(ChatFormatting.GRAY)),
                    () -> true, () -> has(perm), b -> toggle(perm)));
        }
    }

    private void toggle(SecurityPermission perm) {
        if (!selected().isEmpty()) {
            ClientPacketDistributor.sendToServer(new SecurityTogglePayload(menu.containerId, selectedSlot, perm.mask));
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        for (int i = 0; i < menu.getCards().getContainerSize() && i < menu.slots.size(); i++) {
            var slot = menu.getSlot(i);
            if (event.x() >= leftPos + slot.x - 1 && event.x() < leftPos + slot.x + 17
                    && event.y() >= topPos + slot.y - 1 && event.y() < topPos + slot.y + 17) {
                selectedSlot = i;
                break;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        if (selectedSlot >= 0 && selectedSlot < menu.getCards().getContainerSize()) {
            var slot = menu.getSlot(selectedSlot);
            g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0x6038B0A8);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, GolemGui.PARCHMENT, false);
        ItemStack card = selected();
        Component who = card.isEmpty() ? Component.translatable("gui.golem_logix.security.no_card")
                : IdentityPunchCardItem.isBound(card) ? Component.literal(IdentityPunchCardItem.getOwnerName(card))
                : Component.translatable("gui.golem_logix.security.unbound");
        g.text(font, Component.translatable("gui.golem_logix.security.rights"), 8, 77, GolemGui.PARCHMENT, false);
        g.text(font, Component.translatable("gui.golem_logix.security.card_owner", who), 8, 94, GolemGui.CYAN, false);
        String owner = menu.getOwnerName().isEmpty() ? "-" : menu.getOwnerName();
        g.text(font, Component.translatable("gui.golem_logix.security.owner", owner), 8, imageHeight - 94, 0xFFA89880, false);
    }
}
