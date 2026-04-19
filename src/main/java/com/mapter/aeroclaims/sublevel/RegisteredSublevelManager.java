package com.mapter.aeroclaims.sublevel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mapter.aeroclaims.Aeroclaims;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Aeroclaims.MODID)
public class RegisteredSublevelManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LEGACY_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Type SHIP_MAP_TYPE = new TypeToken<Map<String, ShipRegistration>>() {}.getType();

    private static Map<String, ShipRegistration> registeredShips = new HashMap<>();

    private static File shipsDataFile;

    public static class ShipRegistration {
        public String name;
        public String ownerUuid;
        public String owner;

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
        MinecraftServer server = event.getServer();
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
        saveRegisteredShips();
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

                if (value != null && value.isJsonPrimitive()) {
                    String name = value.getAsString();
                    result.put(shipId, new ShipRegistration(name, null, null));
                } else {
                    ShipRegistration reg = GSON.fromJson(value, ShipRegistration.class);
                    if (reg != null && reg.name != null) {
                        result.put(shipId, reg);
                    }
                }
            }

            registeredShips = result;
        } catch (IOException e) {
            e.printStackTrace();
            registeredShips = new HashMap<>();
        }
    }

    private static void saveRegisteredShips() {
        try (FileWriter writer = new FileWriter(shipsDataFile)) {
            GSON.toJson(registeredShips, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void registerShip(String shipId, String name) {
        registerShip(shipId, name, null);
    }

    public static void registerShip(String shipId, String name, UUID ownerUuid) {
        registerShip(shipId, name, ownerUuid, null);
    }

    public static void registerShip(String shipId, String name, UUID ownerUuid, String ownerName) {
        registeredShips.put(shipId, new ShipRegistration(name, ownerUuid, ownerName));
        saveRegisteredShips();
    }

    public static void unregisterShip(String shipId) {
        registeredShips.remove(shipId);
        saveRegisteredShips();
    }

    public static ShipRegistration getRegistration(String shipId) {
        return registeredShips.get(shipId);
    }

    public static String getRegisteredName(String shipId) {
        ShipRegistration reg = registeredShips.get(shipId);
        return reg == null ? null : reg.name;
    }

    public static Map<String, String> getAllRegisteredShips() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, ShipRegistration> entry : registeredShips.entrySet()) {
            ShipRegistration reg = entry.getValue();
            if (reg != null && reg.name != null) {
                result.put(entry.getKey(), reg.name);
            }
        }
        return result;
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

    public static int getRegistrationsCount(UUID playerUuid) {
        if (playerUuid == null) {
            return 0;
        }

        String uuidString = playerUuid.toString();
        int count = 0;
        for (ShipRegistration reg : registeredShips.values()) {
            if (reg != null && uuidString.equals(reg.ownerUuid)) {
                count++;
            }
        }
        return count;
    }

    public static int getMaxRegistrations(UUID playerUuid) {
        return Integer.MAX_VALUE;
    }

    public static boolean canRegister(UUID playerUuid) {
        return true;
    }
}
