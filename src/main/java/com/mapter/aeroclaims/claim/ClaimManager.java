package com.mapter.aeroclaims.claim;

import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.permission.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

public class ClaimManager {

    public static ClaimPermissionResolver PERMISSION_RESOLVER = new DefaultPermissionResolver();

    private record ShipTraversalResult(int count, Set<BlockPos> visitedBlocks, boolean limitExceeded) {
    }

    public static void init(boolean opacLoaded) {
        PERMISSION_RESOLVER = opacLoaded
                ? new OpacPermissionResolver()
                : new DefaultPermissionResolver();
    }

    public static void addClaim(ServerLevel level, BlockPos pos, UUID owner) {
        ClaimSavedData data = ClaimSavedData.get(level);
        Set<BlockPos> claimedBlocks = floodFill(level, pos);
        data.getClaims().add(new Claim(pos, owner, claimedBlocks, false, true, false, false));
        data.setDirty();
    }

    public static void removeClaim(ServerLevel level, BlockPos pos) {
        ClaimSavedData data = ClaimSavedData.get(level);
        data.getClaims().removeIf(c -> c.getCenter().equals(pos));
        data.setDirty();
    }

    public static Claim getClaimAt(ServerLevel level, BlockPos pos) {
        return findClaim(level, claim -> claim.contains(pos));
    }

    public static Claim getClaimByCenter(ServerLevel level, BlockPos center) {
        return findClaim(level, claim -> claim.getCenter().equals(center));
    }

    public static Claim getClaimByShipId(ServerLevel level, String shipId) {
        return shipId == null ? null : findClaim(level, claim -> shipId.equals(claim.getShipId()));
    }

    public static void refreshClaim(ServerLevel level, BlockPos center) {
        Claim claim = getClaimByCenter(level, center);
        if (claim != null) {
            int maxSize = AeroClaimsConfig.MAX_SHIP_BLOCKS.get();
            if (countShipBlocks(level, center, maxSize) > maxSize) {
                return;
            }
            Set<BlockPos> newBlocks = floodFill(level, center);
            claim.setActive(true);
            claim.getClaimedBlocks().clear();
            claim.getClaimedBlocks().addAll(newBlocks);
            ClaimSavedData.get(level).setDirty();
        }
    }

    public static void deactivateClaim(ServerLevel level, BlockPos center) {
        Claim claim = getClaimByCenter(level, center);
        if (claim == null) return;
        claim.setActive(false);
        ClaimSavedData.get(level).setDirty();
    }

    public static int countShipBlocks(ServerLevel level, BlockPos start, int hardLimit) {
        if (hardLimit <= 0) return 0;

        return traverseShipBlocks(level, start, hardLimit).count();
    }

    public static int countShipBlocksExact(ServerLevel level, BlockPos start) {
        return traverseShipBlocks(level, start, Integer.MAX_VALUE).count();
    }

    private static Set<BlockPos> floodFill(ServerLevel level, BlockPos start) {
        return traverseShipBlocks(level, start, AeroClaimsConfig.MAX_SHIP_BLOCKS.get()).visitedBlocks();
    }

    private static Claim findClaim(ServerLevel level, java.util.function.Predicate<Claim> predicate) {
        for (Claim claim : ClaimSavedData.get(level).getClaims()) {
            if (predicate.test(claim)) {
                return claim;
            }
        }
        return null;
    }

    private static ShipTraversalResult traverseShipBlocks(ServerLevel level, BlockPos start, int blockLimit) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);

        int count = 0;
        boolean limitExceeded = false;
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (isSolidShipBlock(level, current)) {
                count++;
                if (count > blockLimit) {
                    limitExceeded = true;
                    break;
                }
            }

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.contains(neighbor) && isSolidShipBlock(level, neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return new ShipTraversalResult(count, visited, limitExceeded);
    }

    private static boolean isSolidShipBlock(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).isAir() && !level.getBlockState(pos).is(Blocks.WATER);
    }
}