package com.mapter.aeroclaims.claim;

import net.minecraft.core.BlockPos;

import java.util.Set;
import java.util.UUID;

public class Claim {

    private final BlockPos center;
    private final UUID owner;
    private final Set<BlockPos> claimedBlocks;
    private boolean active;
    private boolean allowParty;
    private boolean allowAllies;
    private boolean allowOthers;
    private String shipId;

    public Claim(BlockPos center, UUID owner, Set<BlockPos> claimedBlocks, boolean active, boolean allowParty, boolean allowAllies, boolean allowOthers) {
        this.center = center;
        this.owner = owner;
        this.claimedBlocks = claimedBlocks;
        this.active = active;
        this.allowParty = allowParty;
        this.allowAllies = allowAllies;
        this.allowOthers = allowOthers;
    }

    public String getShipId() { return shipId; }

    public void setShipId(String shipId) { this.shipId = shipId; }

    public boolean contains(BlockPos pos) {
        return active && claimedBlocks.contains(pos);
    }

    public UUID getOwner() {
        return owner;
    }

    public BlockPos getCenter() {
        return center;
    }

    public Set<BlockPos> getClaimedBlocks() {
        return claimedBlocks;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAllowParty() {
        return allowParty;
    }

    public void setAllowParty(boolean allowParty) {
        this.allowParty = allowParty;
    }

    public boolean isAllowAllies() {
        return allowAllies;
    }

    public void setAllowAllies(boolean allowAllies) {
        this.allowAllies = allowAllies;
    }

    public boolean isAllowOthers() {
        return allowOthers;
    }

    public void setAllowOthers(boolean allowOthers) {
        this.allowOthers = allowOthers;
    }
}