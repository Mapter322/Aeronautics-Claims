package com.mapter.aeroclaims.claim;

import com.mapter.aeroclaims.permission.ClaimPermissionResolver;
import com.mapter.aeroclaims.permission.DefaultPermissionResolver;
import com.mapter.aeroclaims.permission.OpacPermissionResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class ClaimManager {

    public static ClaimPermissionResolver PERMISSION_RESOLVER = new DefaultPermissionResolver();

    public static void init(boolean opacLoaded) {
        PERMISSION_RESOLVER = opacLoaded ? new OpacPermissionResolver() : new DefaultPermissionResolver();
    }

    // Claim management

    public static void addClaim(ServerLevel level, BlockPos pos, UUID owner) {
        ClaimSavedData data = ClaimSavedData.get(level);
        data.getClaims().add(new Claim(pos, owner, new HashSet<>(), false, true, false, false));
        data.setDirty();
    }

    public static void removeClaim(ServerLevel level, BlockPos pos) {
        ClaimSavedData data = ClaimSavedData.get(level);
        data.getClaims().removeIf(c -> c.getCenter().equals(pos));
        data.setDirty();
    }

    public static void deactivateClaim(ServerLevel level, BlockPos center) {
        Claim claim = getClaimByCenter(level, center);
        if (claim == null) return;
        claim.setActive(false);
        ClaimSavedData.get(level).setDirty();
    }

    // Claim activation/refresh

    // Activates claim: runs flood-fill on ship and saves block set. Returns false if ship exceeds block limit.
    public static boolean activateClaim(ServerLevel level, BlockPos center) {
        Claim claim = getClaimByCenter(level, center);
        if (claim == null) return false;

        int limit = AeroClaimManager.getBlockLimit(level, center);
        if (limit <= 0) return false;

        if (countShipBlocks(level, center, limit + 1) > limit) return false;

        updateClaimedBlocks(level, claim, center, limit);
        claim.setActive(true);
        ClaimSavedData.get(level).setDirty();
        return true;
    }

    // Updates claimed block set without changing active flag. Used for periodic refresh.
    public static boolean refreshClaim(ServerLevel level, BlockPos center) {
        Claim claim = getClaimByCenter(level, center);
        if (claim == null) return false;

        int limit = AeroClaimManager.getBlockLimit(level, center);
        if (limit <= 0) return false;

        if (countShipBlocks(level, center, limit + 1) > limit) return false;

        updateClaimedBlocks(level, claim, center, limit);
        claim.setActive(true);
        ClaimSavedData.get(level).setDirty();
        return true;
    }

    // Recounts ship blocks and updates saved set. If deactivateOnOverflow=true, deactivates claim on overflow.
    // @return exact block count, or -1 on error
    public static int recountShipBlocks(ServerLevel level, BlockPos center, boolean deactivateOnOverflow) {
        Claim claim = getClaimByCenter(level, center);
        if (claim == null) return -1;

        int limit = AeroClaimManager.getBlockLimit(level, center);
        if (limit <= 0) return -1;

        int blockCount = countShipBlocks(level, center, limit + 1);

        if (blockCount > limit) {
            if (deactivateOnOverflow) {
                claim.setActive(false);
                ClaimSavedData.get(level).setDirty();
            }
            // Overflow - return exact count without extra flood-fill
            return countShipBlocksExact(level, center);
        }

        updateClaimedBlocks(level, claim, center, limit);
        ClaimSavedData.get(level).setDirty();
        return blockCount;
    }


    // Returns claim containing position, or null.
    public static Claim getClaimAt(ServerLevel level, BlockPos pos) {
        return ClaimSavedData.get(level).getBlockIndex().get(pos);
    }

    // Returns claim by center block, or null.
    public static Claim getClaimByCenter(ServerLevel level, BlockPos center) {
        return findClaim(level, claim -> claim.getCenter().equals(center));
    }

    // Returns claim by ship ID, or null.
    public static Claim getClaimByShipId(ServerLevel level, String shipId) {
        if (shipId == null) return null;
        return findClaim(level, claim -> shipId.equals(claim.getShipId()));
    }

    // Ship block counting

    // Count with limit - stops at hardLimit.
    public static int countShipBlocks(ServerLevel level, BlockPos start, int hardLimit) {
        if (hardLimit <= 0) return 0;
        return traverse(level, start, hardLimit).count();
    }

    // Exact count of all ship blocks without limit.
    public static int countShipBlocksExact(ServerLevel level, BlockPos start) {
        return traverse(level, start, Integer.MAX_VALUE).count();
    }

    // Private methods

    private static void updateClaimedBlocks(ServerLevel level, Claim claim, BlockPos center, int limit) {
        Set<BlockPos> blocks = floodFill(level, center, limit);
        claim.getClaimedBlocks().clear();
        claim.getClaimedBlocks().addAll(blocks);
    }

    private static Set<BlockPos> floodFill(ServerLevel level, BlockPos start, int limit) {
        return traverse(level, start, limit + 1).visitedBlocks();
    }

    private static Claim findClaim(ServerLevel level, Predicate<Claim> predicate) {
        for (Claim claim : ClaimSavedData.get(level).getClaims()) {
            if (predicate.test(claim)) return claim;
        }
        return null;
    }

    private record TraversalResult(int count, Set<BlockPos> visitedBlocks) {}

    // BFS traversal of connected solid blocks with count limit.
    private static TraversalResult traverse(ServerLevel level, BlockPos start, int limit) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        visited.add(start);
        queue.add(start);

        int count = 0;
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (!isSolid(level, current)) continue;
            if (++count > limit) break;

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (visited.add(neighbor) && isSolid(level, neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return new TraversalResult(count, visited);
    }

    private static boolean isSolid(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return !state.isAir() && !state.is(Blocks.WATER);
    }
}
