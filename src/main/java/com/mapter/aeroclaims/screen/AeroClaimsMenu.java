package com.mapter.aeroclaims.screen;

import com.mapter.aeroclaims.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class AeroClaimsMenu extends AbstractContainerMenu {

    public AeroClaimsMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory);
    }

    public AeroClaimsMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.AEROCLAIMS_MENU.get(), containerId);
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
