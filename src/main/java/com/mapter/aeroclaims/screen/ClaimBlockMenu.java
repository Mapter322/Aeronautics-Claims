package com.mapter.aeroclaims.screen;

import com.mapter.aeroclaims.block.ClaimBlock;
import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.registry.ModMenus;
import com.mapter.aeroclaims.sublevel.SubLevelTicketManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

public class ClaimBlockMenu extends AbstractContainerMenu {

    private final Inventory playerInventory;
    private final BlockPos center;
    private final UUID owner;
    private String shipName;
    private final boolean onShip;

    private boolean claimActive;
    private boolean allowParty;
    private boolean allowAllies;
    private boolean allowOthers;
    private int claimsForBlock;
    private int freeSlots;
    private int blocksPerClaim;
    private int shipBlockCount;
    private int forceloadsForBlock;
    private boolean forceloadEnabled;
    private boolean navigatingAway = false;
    private final boolean returnToShipList;
    @Nullable
    private final ResourceKey<Level> targetDimension;

    @Nullable
    private AutoCloseable tempLoadSession;
    private int tempSessionTicks = 0;

    public ClaimBlockMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(
                containerId, playerInventory,
                buf.readBlockPos(),
                buf.readUUID(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    public ClaimBlockMenu(int containerId, Inventory playerInventory,
                          BlockPos center, UUID owner, String shipName,
                          boolean onShip, boolean claimActive,
                          boolean allowParty, boolean allowAllies, boolean allowOthers,
                          int claimsForBlock, int freeSlots, int blocksPerClaim, int shipBlockCount,
                          int forceloadsForBlock, boolean forceloadEnabled, boolean returnToShipList) {
        this(containerId, playerInventory, center, owner, shipName, onShip, claimActive,
                allowParty, allowAllies, allowOthers, claimsForBlock, freeSlots, blocksPerClaim,
                shipBlockCount, forceloadsForBlock, forceloadEnabled, returnToShipList, null);
    }

    public ClaimBlockMenu(int containerId, Inventory playerInventory,
                          BlockPos center, UUID owner, String shipName,
                          boolean onShip, boolean claimActive,
                          boolean allowParty, boolean allowAllies, boolean allowOthers,
                          int claimsForBlock, int freeSlots, int blocksPerClaim, int shipBlockCount,
                          int forceloadsForBlock, boolean forceloadEnabled, boolean returnToShipList,
                          @Nullable ResourceKey<Level> targetDimension) {
        super(ModMenus.CLAIM_SETTINGS_MENU.get(), containerId);
        this.playerInventory = playerInventory;
        this.center = center;
        this.owner = owner;
        this.shipName = shipName;
        this.onShip = onShip;
        this.claimActive = claimActive;
        this.allowParty = allowParty;
        this.allowAllies = allowAllies;
        this.allowOthers = allowOthers;
        this.claimsForBlock = claimsForBlock;
        this.freeSlots = freeSlots;
        this.blocksPerClaim = blocksPerClaim;
        this.shipBlockCount = shipBlockCount;
        this.forceloadsForBlock = forceloadsForBlock;
        this.forceloadEnabled = forceloadEnabled;
        this.returnToShipList = returnToShipList;
        this.targetDimension = targetDimension;
    }

    public BlockPos getCenter()     { return center; }
    public UUID getOwner()          { return owner; }
    public String getShipName()     { return shipName; }
    public void setShipName(String v) { shipName = v; }
    public boolean isOnShip()       { return onShip; }

    public boolean isClaimActive()              { return claimActive; }
    public void setClaimActive(boolean v)       { claimActive = v; }

    public boolean isAllowParty()               { return allowParty; }
    public void setAllowParty(boolean v)        { allowParty = v; }

    public boolean isAllowAllies()              { return allowAllies; }
    public void setAllowAllies(boolean v)       { allowAllies = v; }

    public boolean isAllowOthers()              { return allowOthers; }
    public void setAllowOthers(boolean v)       { allowOthers = v; }

    public int getClaimsForBlock()              { return claimsForBlock; }
    public void setClaimsForBlock(int v)        { claimsForBlock = v; }

    public int getFreeSlots()                   { return freeSlots; }
    public void setFreeSlots(int v)             { freeSlots = v; }

    public int getBlocksPerClaim()              { return blocksPerClaim; }
    public void setBlocksPerClaim(int v)        { blocksPerClaim = v; }

    public int getShipBlockCount()              { return shipBlockCount; }
    public void setShipBlockCount(int v)        { shipBlockCount = v; }
    public int getForceloadsForBlock()          { return forceloadsForBlock; }
    public void setForceloadsForBlock(int v)    { forceloadsForBlock = v; }

    public boolean isForceloadEnabled()         { return forceloadEnabled; }
    public void setForceloadEnabled(boolean v)  { forceloadEnabled = v; }
    public void setNavigatingAway(boolean v)     { navigatingAway = v; }

    public boolean isReturnToShipList()          { return returnToShipList; }

    public int getBlockLimit()                  { return claimsForBlock * blocksPerClaim; }

    public void setTempLoadSession(@Nullable AutoCloseable session) {
        this.tempLoadSession = session;
    }

    public boolean hasTempLoadSession() {
        return this.tempLoadSession != null;
    }

    @Nullable
    public ResourceKey<Level> getTargetDimension() {
        return targetDimension;
    }

    public void closeTempLoadSession() {
        if (tempLoadSession != null) {
            try {
                tempLoadSession.close();
            } catch (Exception ignored) {}
            tempLoadSession = null;
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (tempLoadSession != null) {
            tempSessionTicks++;
            int maxTicks = AeroClaimsConfig.TEMP_LOAD_TIMEOUT_SECONDS.get() * 20;
            if (maxTicks > 0 && tempSessionTicks >= maxTicks) {
                closeTempLoadSession();
                if (playerInventory != null && playerInventory.player instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.translatable("message.aeroclaims.temp_load_timeout"));
                    sp.closeContainer();
                }
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (!player.isAlive() || !player.getUUID().equals(owner)) return false;
        if (returnToShipList) {
            if (player.level().isClientSide) return true;
            ServerLevel targetLevel = (ServerLevel) player.level();
            if (targetDimension != null && player.getServer() != null) {
                ServerLevel dimLevel = player.getServer().getLevel(targetDimension);
                if (dimLevel != null) targetLevel = dimLevel;
            }
            return targetLevel.getBlockState(center).getBlock() instanceof ClaimBlock;
        }
        return player.level().getBlockState(center).getBlock() instanceof ClaimBlock;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public void removed(Player player) {
        Level level = player.level();
        if (!level.isClientSide && !navigatingAway) {
            ServerLevel serverLevel = (ServerLevel) level;
            if (targetDimension != null && player.getServer() != null) {
                ServerLevel dimLevel = player.getServer().getLevel(targetDimension);
                if (dimLevel != null) serverLevel = dimLevel;
            }

            Claim claim = ClaimManager.getClaimByCenter(serverLevel, center);
            if (claim != null && !claim.isActive() && player.getUUID().equals(claim.getOwner())) {
                AeroClaimManager.releaseAllClaimsForBlock(serverLevel, (ServerPlayer) player, center);
                AeroClaimManager.releaseAllForceloadsForBlock(serverLevel, (ServerPlayer) player, center);
                String sid = claim.getShipId();
                if (sid != null) SubLevelTicketManager.remove(serverLevel, UUID.fromString(sid));
            }

            BlockState state = serverLevel.getBlockState(center);
            if (state.getBlock() instanceof ClaimBlock
                    && state.hasProperty(ClaimBlock.OPEN)
                    && state.getValue(ClaimBlock.OPEN)) {
                serverLevel.setBlock(center, state.setValue(ClaimBlock.OPEN, false), 3);
            }
        }
        closeTempLoadSession();
        super.removed(player);
    }
}
