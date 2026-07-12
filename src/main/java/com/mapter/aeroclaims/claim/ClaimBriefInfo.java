package com.mapter.aeroclaims.claim;

import net.minecraft.server.level.ServerLevel;

public class ClaimBriefInfo {

    private final int claimsForBlock;
    private final Integer blockCount;
    private final int blockLimit;
    private final boolean active;
    private final int forceloadsForBlock;

    private ClaimBriefInfo(int claimsForBlock, Integer blockCount, int blockLimit, boolean active, int forceloadsForBlock) {
        this.claimsForBlock = claimsForBlock;
        this.blockCount = blockCount;
        this.blockLimit = blockLimit;
        this.active = active;
        this.forceloadsForBlock = forceloadsForBlock;
    }


    public static ClaimBriefInfo of(ServerLevel level, Claim claim) {
        if (claim == null) return null;
        AeroClaimSavedData data = AeroClaimSavedData.get(level);
        int claimsForBlock = data.getClaimsForBlock(claim.getCenter());
        Integer blockCount = data.getCachedShipBlockCount(claim.getCenter());
        int blockLimit = AeroClaimManager.getBlockLimit(level, claim.getCenter());
        int forceloadsForBlock = data.getForceloadsForBlock(claim.getCenter());
        return new ClaimBriefInfo(claimsForBlock, blockCount, blockLimit, claim.isActive(), forceloadsForBlock);
    }


    public static ClaimBriefInfo ofShip(ServerLevel level, String shipId) {
        return of(level, ClaimManager.getClaimByShipId(level, shipId));
    }

    public int getClaimsForBlock() { return claimsForBlock; }
    public int getForceloadsForBlock() { return forceloadsForBlock; }
    public Integer getBlockCount() { return blockCount; }
    public int getBlockLimit() { return blockLimit; }
    public boolean isActive() { return active; }
    public boolean isBlockCountKnown() { return blockCount != null; }
}
