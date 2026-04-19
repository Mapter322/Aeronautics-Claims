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

        for (Claim claim : ClaimSavedData.get(level).getClaims()) {
            if (claim.contains(pos)) return claim;
        }
        return null;
    }

    public static Claim getClaimByCenter(ServerLevel level, BlockPos center) {

        for (Claim claim : ClaimSavedData.get(level).getClaims()) {
            if (claim.getCenter().equals(center)) return claim;
        }
        return null;
    }

    public static Claim getClaimByShipId(ServerLevel level, String shipId) {
        if (shipId == null) return null;
        for (Claim claim : ClaimSavedData.get(level).getClaims()) {
            if (shipId.equals(claim.getShipId())) return claim;
        }
        return null;
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

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);

        int count = 0;
        while (!queue.isEmpty() && count <= hardLimit) {
            BlockPos current = queue.poll();
            if (!level.getBlockState(current).isAir() && !level.getBlockState(current).is(Blocks.WATER)) {
                count++;
                if (count > hardLimit) return count;
            }

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.contains(neighbor)
                        && !level.getBlockState(neighbor).isAir()
                        && !level.getBlockState(neighbor).is(Blocks.WATER)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return count;
    }

    public static int countShipBlocksExact(ServerLevel level, BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        int count = 0;
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (!level.getBlockState(current).isAir() && !level.getBlockState(current).is(Blocks.WATER)) {
                count++;
            }

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.contains(neighbor)
                        && !level.getBlockState(neighbor).isAir()
                        && !level.getBlockState(neighbor).is(Blocks.WATER)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return count;
    }

    private static Set<BlockPos> floodFill(ServerLevel level, BlockPos start) {
        Set<BlockPos> claimed = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);
        claimed.add(start);
        int maxSize = AeroClaimsConfig.MAX_SHIP_BLOCKS.get();
        while (!queue.isEmpty() && claimed.size() < maxSize) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.contains(neighbor) && !level.getBlockState(neighbor).isAir() && !level.getBlockState(neighbor).is(Blocks.WATER)) {
                    visited.add(neighbor);
                    claimed.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return claimed;
    }
}