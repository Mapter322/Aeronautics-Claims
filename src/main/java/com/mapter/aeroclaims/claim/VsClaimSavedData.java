package com.mapter.aeroclaims.claim;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VsClaimSavedData extends SavedData {

    private static final String DATA_NAME = "vsclaims_ship_slots";
    private static final Factory<VsClaimSavedData> FACTORY = new Factory<>(
            VsClaimSavedData::new,
            VsClaimSavedData::load,
            null
    );

    // Player UUID -> number of migrated claims (maximum for this mod)
    private final Map<UUID, Integer> migratedSlots = new HashMap<>();

    // Player UUID -> number of used claims (active claim blocks)
    private final Map<UUID, Integer> usedSlots = new HashMap<>();

    public static VsClaimSavedData get(ServerLevel level) {
        // Use overworld to make data global for the server
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static VsClaimSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        VsClaimSavedData data = new VsClaimSavedData();

        ListTag migrated = tag.getList("migrated", Tag.TAG_COMPOUND);
        for (Tag t : migrated) {
            CompoundTag entry = (CompoundTag) t;
            UUID uuid = entry.getUUID("uuid");
            int slots = entry.getInt("slots");
            data.migratedSlots.put(uuid, slots);
        }

        ListTag used = tag.getList("used", Tag.TAG_COMPOUND);
        for (Tag t : used) {
            CompoundTag entry = (CompoundTag) t;
            UUID uuid = entry.getUUID("uuid");
            int slots = entry.getInt("slots");
            data.usedSlots.put(uuid, slots);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag migrated = new ListTag();
        for (Map.Entry<UUID, Integer> entry : migratedSlots.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("uuid", entry.getKey());
            e.putInt("slots", entry.getValue());
            migrated.add(e);
        }
        tag.put("migrated", migrated);

        ListTag used = new ListTag();
        for (Map.Entry<UUID, Integer> entry : usedSlots.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("uuid", entry.getKey());
            e.putInt("slots", entry.getValue());
            used.add(e);
        }
        tag.put("used", used);

        return tag;
    }

    //  Migrated claims (maximum)

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

    // Used claims (consumed)

    public int getUsedSlots(UUID playerId) {
        return usedSlots.getOrDefault(playerId, 0);
    }

    public void setUsedSlots(UUID playerId, int amount) {
        usedSlots.put(playerId, Math.max(0, amount));
        setDirty();
    }

    public void incrementUsedSlots(UUID playerId) {
        setUsedSlots(playerId, getUsedSlots(playerId) + 1);
    }

    public void decrementUsedSlots(UUID playerId) {
        setUsedSlots(playerId, getUsedSlots(playerId) - 1);
    }

    //  Convenience method: free claims

    public int getFreeSlots(UUID playerId) {
        return Math.max(0, getMigratedSlots(playerId) - getUsedSlots(playerId));
    }
}
