package com.mapter.aeroclaims.screen;

import com.mapter.aeroclaims.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class AeroClaimsMenu extends AbstractContainerMenu {

    public record ShipEntry(String shipName, String shipId, boolean active, int claims, int blockCount, int blockLimit,
                            boolean hasCoords, int worldX, int worldY, int worldZ, boolean forceloadEnabled) {}

    private final List<ShipEntry> ships = new ArrayList<>();

    private int aeroTotal;
    private int aeroUsed;
    private int forceloadTotal;
    private int forceloadUsed;
    private BlockPos returnClaimPos;

    public AeroClaimsMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory);
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            ships.add(new ShipEntry(
                    buf.readUtf(), buf.readUtf(), buf.readBoolean(),
                    buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readBoolean()
            ));
        }
        buf.readInt();
        aeroTotal = buf.readInt();
        aeroUsed  = buf.readInt();
        forceloadTotal = buf.readInt();
        forceloadUsed  = buf.readInt();
        if (buf.isReadable()) {
            if (buf.readBoolean()) {
                this.returnClaimPos = buf.readBlockPos();
            }
        }
    }

    public AeroClaimsMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.AEROCLAIMS_MENU.get(), containerId);
    }

    public List<ShipEntry> getShips() { return ships; }

    public int getAeroTotal()       { return aeroTotal; }
    public int getAeroUsed()        { return aeroUsed; }
    public int getForceloadTotal()  { return forceloadTotal; }
    public int getForceloadUsed()   { return forceloadUsed; }
    public BlockPos getReturnClaimPos() { return returnClaimPos; }

    public void setStats(int aeroTotal, int aeroUsed) {
        this.aeroTotal = aeroTotal;
        this.aeroUsed  = aeroUsed;
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
