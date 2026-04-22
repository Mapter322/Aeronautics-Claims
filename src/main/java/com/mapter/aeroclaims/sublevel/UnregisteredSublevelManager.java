package com.mapter.aeroclaims.sublevel;

import com.google.gson.*;
import com.mapter.aeroclaims.Aeroclaims;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = Aeroclaims.MODID)
public class UnregisteredSublevelManager {

    private static final Logger LOGGER = LogManager.getLogger("aeroclaims/UnregisteredShipsManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "unclaimed_sublevels.json";

    private static final Map<String, UnregisteredShip> ships = new ConcurrentHashMap<>();
    private static Path saveFile = null;

    public static class UnregisteredShip {
        public final String name;
        public final String createdAt;

        public UnregisteredShip(String name) {
            this.name = name;
            this.createdAt = Instant.now().toString();
        }

        public UnregisteredShip(String name, String createdAt) {
            this.name = name;
            this.createdAt = createdAt;
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Path dataDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .toAbsolutePath().resolve("aeroclaims");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create aeroclaims directory: {}", e.toString());
        }
        saveFile = dataDir.resolve(FILE_NAME);
        load();
        LOGGER.info("UnregisteredShipsManager loaded, ships: {}", ships.size());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {

        save();
    }


    public static void addShip(String shipId, String name) {
        if (ships.containsKey(shipId)) return;
        ships.put(shipId, new UnregisteredShip(name));
        LOGGER.info("New unregistered ship detected: id={} name={}", shipId, name);
    }

    public static void removeShip(String shipId) {
        if (ships.remove(shipId) != null) {
            LOGGER.debug("Ship {} removed from unregistered list", shipId);
        }
    }

    public static boolean contains(String shipId) {
        return ships.containsKey(shipId);
    }

    public static Collection<UnregisteredShip> getAll() {
        return Collections.unmodifiableCollection(ships.values());
    }

    public static Set<String> getShipIds() {
        return Collections.unmodifiableSet(new HashSet<>(ships.keySet()));
    }

    public static int getCount() {
        return ships.size();
    }


    public static void save() {
        if (saveFile == null) return;
        try {
            JsonObject obj = new JsonObject();
            for (Map.Entry<String, UnregisteredShip> entry : ships.entrySet()) {
                String shipId = entry.getKey();
                UnregisteredShip s = entry.getValue();
                JsonObject shipObj = new JsonObject();
                shipObj.addProperty("name", s.name);
                shipObj.addProperty("createdAt", s.createdAt);
                obj.add(shipId, shipObj);
            }
            Files.writeString(saveFile, GSON.toJson(obj));
        } catch (IOException e) {
            LOGGER.error("Failed to save {}: {}", FILE_NAME, e.toString());
        }
    }

    private static void load() {
        ships.clear();
        if (saveFile == null || !Files.exists(saveFile)) return;
        try {
            String content = Files.readString(saveFile);
            JsonElement element = JsonParser.parseString(content);
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    String shipId = entry.getKey();
                    JsonElement value = entry.getValue();
                    if (value.isJsonObject()) {
                        JsonObject shipObj = value.getAsJsonObject();
                        String name = shipObj.get("name").getAsString();
                        String createdAt = shipObj.get("createdAt").getAsString();
                        ships.put(shipId, new UnregisteredShip(name, createdAt));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load {}: {}", FILE_NAME, e.toString());
        }
    }
}
