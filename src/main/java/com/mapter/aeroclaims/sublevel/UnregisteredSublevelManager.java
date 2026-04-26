package com.mapter.aeroclaims.sublevel;

import com.google.gson.*;
import com.mapter.aeroclaims.Aeroclaims;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
    private static MinecraftServer server;
    private static int tickCounter = 0;
    private static final int COORD_UPDATE_INTERVAL = 6000;

    public static class UnregisteredShip {
        public final String name;
        public final String createdAt;
        public Double worldX;
        public Double worldY;
        public Double worldZ;

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
        server = event.getServer();
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
        updateAllWorldPositions();
        save();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter >= COORD_UPDATE_INTERVAL) {
            tickCounter = 0;
            updateAllWorldPositions();
        }
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

    public static void updateAllWorldPositions() {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (!(container instanceof ServerSubLevelContainer serverContainer)) continue;
            java.util.List<ServerSubLevel> subLevels = serverContainer.getAllSubLevels();
            if (subLevels == null) continue;
            for (ServerSubLevel subLevel : subLevels) {
                String id = subLevel.getUniqueId().toString();
                UnregisteredShip ship = ships.get(id);
                if (ship == null) continue;
                double[] pos = SableShipUtils.getShipWorldPos(subLevel);
                if (pos != null) {
                    ship.worldX = pos[0];
                    ship.worldY = pos[1];
                    ship.worldZ = pos[2];
                }
            }
        }
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
                String coords = formatCoords(s.worldX, s.worldY, s.worldZ);
                if (coords != null) shipObj.addProperty("coords", coords);
                obj.add(shipId, shipObj);
            }
            Files.writeString(saveFile, GSON.toJson(obj));
        } catch (IOException e) {
            LOGGER.error("Failed to save {}: {}", FILE_NAME, e.toString());
        }
    }

    public static void saveNow() {
        updateAllWorldPositions();
        save();
    }

    private static String formatCoords(Double x, Double y, Double z) {
        if (x == null || y == null || z == null) return null;
        return (int) Math.floor(x) + " " + (int) Math.floor(y) + " " + (int) Math.floor(z);
    }

    private static void readCoords(JsonObject shipObj, UnregisteredShip ship) {
        if (shipObj.has("coords") && shipObj.get("coords").isJsonPrimitive()) {
            String[] parts = shipObj.get("coords").getAsString().trim().split("\\s+");
            if (parts.length == 3) {
                try {
                    ship.worldX = Double.parseDouble(parts[0]);
                    ship.worldY = Double.parseDouble(parts[1]);
                    ship.worldZ = Double.parseDouble(parts[2]);
                } catch (NumberFormatException ignored) {
                }
            }
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
                        UnregisteredShip ship = new UnregisteredShip(name, createdAt);
                        readCoords(shipObj, ship);
                        ships.put(shipId, ship);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load {}: {}", FILE_NAME, e.toString());
        }
    }
}
