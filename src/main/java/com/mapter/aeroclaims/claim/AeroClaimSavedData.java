package com.mapter.aeroclaims.claim;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AeroClaimSavedData extends SavedData {

    private static final String DATA_NAME = "aeroclaims_sublevels_slots";
    private static final Factory<AeroClaimSavedData> FACTORY = new Factory<>(
            AeroClaimSavedData::new,
            AeroClaimSavedData::load,
            null
    );

    private final Map<UUID, Integer> migratedSlots = new HashMap<>();

    private final Map<UUID, Integer> usedSlots = new HashMap<>();

    private final Map<Long, Integer> claimsPerBlock = new HashMap<>();

    private final Map<Long, Integer> shipBlockCountCache = new HashMap<>();

    private final Map<Long, String> shipIdCache = new HashMap<>();


    public static AeroClaimSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static AeroClaimSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        AeroClaimSavedData data = new AeroClaimSavedData();

        for (Tag t : tag.getList("migrated", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            data.migratedSlots.put(e.getUUID("uuid"), e.getInt("slots"));
        }
        for (Tag t : tag.getList("used", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            data.usedSlots.put(e.getUUID("uuid"), e.getInt("slots"));
        }
        for (Tag t : tag.getList("claimsPerBlock", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            data.claimsPerBlock.put(e.getLong("pos"), e.getInt("claims"));
        }
        for (Tag t : tag.getList("shipBlockCountCache", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            data.shipBlockCountCache.put(e.getLong("pos"), e.getInt("count"));
        }
        for (Tag t : tag.getList("shipIdCache", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            data.shipIdCache.put(e.getLong("pos"), e.getString("shipId"));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag migrated = new ListTag();
        for (Map.Entry<UUID, Integer> e : migratedSlots.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", e.getKey());
            entry.putInt("slots", e.getValue());
            migrated.add(entry);
        }
        tag.put("migrated", migrated);

        ListTag used = new ListTag();
        for (Map.Entry<UUID, Integer> e : usedSlots.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", e.getKey());
            entry.putInt("slots", e.getValue());
            used.add(entry);
        }
        tag.put("used", used);

        ListTag perBlock = new ListTag();
        for (Map.Entry<Long, Integer> e : claimsPerBlock.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", e.getKey());
            entry.putInt("claims", e.getValue());
            perBlock.add(entry);
        }
        tag.put("claimsPerBlock", perBlock);

        ListTag shipCache = new ListTag();
        for (Map.Entry<Long, Integer> e : shipBlockCountCache.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", e.getKey());
            entry.putInt("count", e.getValue());
            shipCache.add(entry);
        }
        tag.put("shipBlockCountCache", shipCache);

        ListTag shipIdCacheTag = new ListTag();
        for (Map.Entry<Long, String> e : shipIdCache.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", e.getKey());
            entry.putString("shipId", e.getValue());
            shipIdCacheTag.add(entry);
        }
        tag.put("shipIdCache", shipIdCacheTag);

        return tag;
    }


    public int getMigratedSlots(UUID playerId) {
        return migratedSlots.getOrDefault(playerId, 0);
    }

    public void setMigratedSlots(UUID playerId, int amount) {
        migratedSlots.put(playerId, Math.max(0, amount));
        setDirty();
    }

    public void addMigratedSlots(UUID playerId, int amount) {
        setMigratedSlots(playerId, getMigratedSlots(playerId) + amount);
    }


    public int getUsedSlots(UUID playerId) {
        return usedSlots.getOrDefault(playerId, 0);
    }

    public void setUsedSlots(UUID playerId, int amount) {
        usedSlots.put(playerId, Math.max(0, amount));
        setDirty();
    }

    public int getFreeSlots(UUID playerId) {
        return Math.max(0, getMigratedSlots(playerId) - getUsedSlots(playerId));
    }


    public int getClaimsForBlock(BlockPos pos) {
        return claimsPerBlock.getOrDefault(pos.asLong(), 0);
    }


    public void setClaimsForBlock(BlockPos pos, UUID owner, int newCount) {
        int delta = newCount - getClaimsForBlock(pos);

        if (newCount <= 0) {
            claimsPerBlock.remove(pos.asLong());
        } else {
            claimsPerBlock.put(pos.asLong(), newCount);
        }

        usedSlots.put(owner, Math.max(0, getUsedSlots(owner) + delta));
        setDirty();
    }


    public void removeClaimsForBlock(BlockPos pos, UUID owner) {
        int current = getClaimsForBlock(pos);
        if (current == 0) return;

        claimsPerBlock.remove(pos.asLong());
        usedSlots.put(owner, Math.max(0, getUsedSlots(owner) - current));
        setDirty();
    }



    public Integer getCachedShipBlockCount(BlockPos pos) {
        return shipBlockCountCache.get(pos.asLong());
    }


    public void cacheShipBlockCount(BlockPos pos, int count) {
        shipBlockCountCache.put(pos.asLong(), count);
        setDirty();
    }


    public void clearCachedShipBlockCount(BlockPos pos) {
        shipBlockCountCache.remove(pos.asLong());
    }

    public String getCachedShipId(BlockPos pos) {
        return shipIdCache.get(pos.asLong());
    }

    public void cacheShipId(BlockPos pos, String shipId) {
        shipIdCache.put(pos.asLong(), shipId);
        setDirty();
    }

    public void clearCachedShipId(BlockPos pos) {
        shipIdCache.remove(pos.asLong());
        setDirty();
    }
}
