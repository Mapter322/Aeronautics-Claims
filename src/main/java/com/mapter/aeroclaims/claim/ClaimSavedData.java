package com.mapter.aeroclaims.claim;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClaimSavedData extends SavedData {

    private static final Factory<ClaimSavedData> FACTORY = new Factory<>(
            ClaimSavedData::new,
            ClaimSavedData::load,
            null
    );

    private final List<Claim> claims = new ArrayList<>();

    private final Map<BlockPos, Claim> blockIndex = new HashMap<>();
    private boolean indexDirty = true;

    private void rebuildIndex() {
        blockIndex.clear();
        for (Claim claim : claims) {
            if (claim.isActive()) {
                for (BlockPos pos : claim.getClaimedBlocks()) {
                    blockIndex.put(pos, claim);
                }
            }
        }
        indexDirty = false;
    }

    public Map<BlockPos, Claim> getBlockIndex() {
        if (indexDirty) rebuildIndex();
        return blockIndex;
    }

    public void invalidateIndex() {
        indexDirty = true;
    }

    public static ClaimSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "aeroclaims_data");
    }

    public static ClaimSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ClaimSavedData data = new ClaimSavedData();

        ListTag list = tag.getList("claims", Tag.TAG_COMPOUND);
        for (Tag t : list) {
            CompoundTag c = (CompoundTag) t;

            Set<BlockPos> claimedBlocks = new HashSet<>();
            ListTag blocks = c.getList("claimedBlocks", Tag.TAG_COMPOUND);
            for (Tag bt : blocks) {
                CompoundTag b = (CompoundTag) bt;
                claimedBlocks.add(new BlockPos(b.getInt("x"), b.getInt("y"), b.getInt("z")));
            }
            data.claims.add(new Claim(
                    new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")),
                    c.getUUID("owner"),
                    claimedBlocks,
                    c.getBoolean("active"),
                    c.getBoolean("allowParty"),
                    c.getBoolean("allowAllies"),
                    c.getBoolean("allowOthers")
            ));
            if (c.contains("shipId")) {
                data.claims.get(data.claims.size() - 1).setShipId(c.getString("shipId"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {

        ListTag list = new ListTag();

        for (Claim claim : claims) {
            CompoundTag c = new CompoundTag();

            c.putInt("x", claim.getCenter().getX());
            c.putInt("y", claim.getCenter().getY());
            c.putInt("z", claim.getCenter().getZ());
            c.putUUID("owner", claim.getOwner());
            ListTag blocks = new ListTag();
            for (BlockPos pos : claim.getClaimedBlocks()) {
                CompoundTag b = new CompoundTag();
                b.putInt("x", pos.getX());
                b.putInt("y", pos.getY());
                b.putInt("z", pos.getZ());
                blocks.add(b);
            }
            c.put("claimedBlocks", blocks);
            if (claim.getShipId() != null) c.putString("shipId", claim.getShipId());
            c.putBoolean("active", claim.isActive());
            c.putBoolean("allowParty", claim.isAllowParty());
            c.putBoolean("allowAllies", claim.isAllowAllies());
            c.putBoolean("allowOthers", claim.isAllowOthers());
            list.add(c);
        }

        tag.put("claims", list);
        return tag;
    }

    @Override
    public void setDirty() {
        super.setDirty();
        invalidateIndex();
    }

    public List<Claim> getClaims() {
        return claims;
    }
}