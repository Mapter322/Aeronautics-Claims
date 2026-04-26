package com.mapter.aeroclaims.sublevel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mapter.aeroclaims.Aeroclaims;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Aeroclaims.MODID)
public class RegisteredSublevelManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, ShipRegistration> registeredShips = new HashMap<>();

    private static File shipsDataFile;
    private static MinecraftServer server;
    private static int tickCounter = 0;
    private static final int COORD_UPDATE_INTERVAL = 6000;

    public static class ShipRegistration {
        public String name;
        public String ownerUuid;
        public String owner;

        public Double worldX;
        public Double worldY;
        public Double worldZ;

        public Integer blocksUsed;
        public Integer blocksMax;

        public ShipRegistration() {
        }

        public ShipRegistration(String name, UUID ownerUuid, String owner) {
            this.name = name;
            this.ownerUuid = ownerUuid == null ? null : ownerUuid.toString();
            this.owner = owner;
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        File worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
        File vsclaimsDir = new File(worldDir, "aeroclaims");
        try {
            Files.createDirectories(vsclaimsDir.toPath());
        } catch (IOException ignored) {}

        shipsDataFile = new File(vsclaimsDir, "claimed_sublevels.json");
        loadRegisteredShips();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        updateAllWorldPositions();
        saveRegisteredShips();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter >= COORD_UPDATE_INTERVAL) {
            tickCounter = 0;
            updateAllWorldPositions();
            UnregisteredSublevelManager.updateAllWorldPositions();
        }
    }

    private static void loadRegisteredShips() {
        if (!shipsDataFile.exists()) {
            registeredShips = new HashMap<>();
            return;
        }

        try (FileReader reader = new FileReader(shipsDataFile)) {
            JsonElement element = GSON.fromJson(reader, JsonElement.class);
            if (element == null || !element.isJsonObject()) {
                registeredShips = new HashMap<>();
                return;
            }

            JsonObject obj = element.getAsJsonObject();
            Map<String, ShipRegistration> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String shipId = entry.getKey();
                JsonElement value = entry.getValue();

                ShipRegistration reg = GSON.fromJson(value, ShipRegistration.class);
                if (reg != null && reg.name != null) {
                    if (value.isJsonObject()) {
                        readCoords(value.getAsJsonObject(), reg);
                    }
                    result.put(shipId, reg);
                }
            }

            registeredShips = result;
        } catch (IOException e) {
            e.printStackTrace();
            registeredShips = new HashMap<>();
        }
    }

    private static void saveRegisteredShips() {
        if (shipsDataFile == null) return;
        try (FileWriter writer = new FileWriter(shipsDataFile)) {
            JsonObject obj = new JsonObject();
            for (Map.Entry<String, ShipRegistration> entry : registeredShips.entrySet()) {
                ShipRegistration reg = entry.getValue();
                JsonObject shipObj = GSON.toJsonTree(reg).getAsJsonObject();
                if (reg != null) {
                    shipObj.remove("worldX");
                    shipObj.remove("worldY");
                    shipObj.remove("worldZ");
                    String coords = formatCoords(reg.worldX, reg.worldY, reg.worldZ);
                    if (coords != null) {
                        shipObj.addProperty("coords", coords);
                    }
                }
                obj.add(entry.getKey(), shipObj);
            }
            GSON.toJson(obj, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String formatCoords(Double x, Double y, Double z) {
        if (x == null || y == null || z == null) return null;
        return (int) Math.floor(x) + " " + (int) Math.floor(y) + " " + (int) Math.floor(z);
    }

    private static void readCoords(JsonObject shipObj, ShipRegistration reg) {
        if (shipObj.has("coords") && shipObj.get("coords").isJsonPrimitive()) {
            String[] parts = shipObj.get("coords").getAsString().trim().split("\\s+");
            if (parts.length == 3) {
                try {
                    reg.worldX = Double.parseDouble(parts[0]);
                    reg.worldY = Double.parseDouble(parts[1]);
                    reg.worldZ = Double.parseDouble(parts[2]);
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }


    public static void saveNow() {
        updateAllWorldPositions();
        saveRegisteredShips();
    }

    public static void updateAllWorldPositions() {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (!(container instanceof ServerSubLevelContainer serverContainer)) continue;
            java.util.List<ServerSubLevel> subLevels = serverContainer.getAllSubLevels();
            if (subLevels == null) continue;
            for (ServerSubLevel subLevel : subLevels) {
                String id = subLevel.getUniqueId().toString();
                ShipRegistration reg = registeredShips.get(id);
                if (reg == null) continue;
                double[] pos = SableShipUtils.getShipWorldPos(subLevel);
                if (pos != null) {
                    reg.worldX = pos[0];
                    reg.worldY = pos[1];
                    reg.worldZ = pos[2];
                }
            }
        }
    }


    public static int getCount() {
        return registeredShips.size();
    }


    public static void registerShip(String shipId, String name, UUID ownerUuid, String ownerName) {
        registeredShips.put(shipId, new ShipRegistration(name, ownerUuid, ownerName));
    }

    public static void registerShip(String shipId, String name, UUID ownerUuid, String ownerName,
                                    int blocksUsed, int blocksMax) {
        ShipRegistration reg = new ShipRegistration(name, ownerUuid, ownerName);
        reg.blocksUsed = blocksUsed;
        reg.blocksMax = blocksMax;
        registeredShips.put(shipId, reg);
    }

    public static void unregisterShip(String shipId) {
        registeredShips.remove(shipId);
    }

    public static ShipRegistration getRegistration(String shipId) {
        return registeredShips.get(shipId);
    }

    public static String getRegisteredName(String shipId) {
        ShipRegistration reg = registeredShips.get(shipId);
        return reg == null ? null : reg.name;
    }

    public static Map<String, String> getRegisteredShips(UUID playerUuid) {
        Map<String, String> result = new HashMap<>();
        if (playerUuid == null) {
            return result;
        }

        String uuidString = playerUuid.toString();
        for (Map.Entry<String, ShipRegistration> entry : registeredShips.entrySet()) {
            ShipRegistration reg = entry.getValue();
            if (reg != null && reg.name != null && uuidString.equals(reg.ownerUuid)) {
                result.put(entry.getKey(), reg.name);
            }
        }

        return result;
    }
}
