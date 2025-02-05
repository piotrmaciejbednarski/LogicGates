package com.piotrbednarski.logicgatesplugin.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.piotrbednarski.logicgatesplugin.LogicGatesPlugin;
import com.piotrbednarski.logicgatesplugin.model.GateData;
import org.bukkit.Location;
import org.bukkit.Material;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GatesConfigManager {
    private static final String GATES_FILE_NAME = "gates.json";

    private final LogicGatesPlugin plugin;
    private final Gson gson;
    private File gatesFile;

    public GatesConfigManager(LogicGatesPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        initializeGatesFile();
    }

    private void initializeGatesFile() {
        gatesFile = new File(plugin.getDataFolder(), GATES_FILE_NAME);

        if (!gatesFile.exists()) {
            try {
                gatesFile.getParentFile().mkdirs();
                gatesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create " + GATES_FILE_NAME + ": " + e.getMessage());
            }
        }
    }

    public void saveGates(ConcurrentHashMap<Location, GateData> gates) {
        Map<String, GateData> serializableGates = new HashMap<>();

        gates.forEach((location, gateData) ->
                serializableGates.put(plugin.convertLocationToString(location), gateData)
        );

        try (FileWriter writer = new FileWriter(gatesFile)) {
            gson.toJson(serializableGates, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save gates: " + e.getMessage());
        }
    }

    public void loadGates(ConcurrentHashMap<Location, GateData> gates) {
        try (FileReader reader = new FileReader(gatesFile)) {
            Map<String, GateData> serializedGates = gson.fromJson(reader,
                    new com.google.gson.reflect.TypeToken<Map<String, GateData>>(){}.getType()
            );

            if (serializedGates == null) return;

            serializedGates.forEach((locationStr, gateData) -> {
                Location loc = plugin.convertStringToLocation(locationStr);
                if (loc != null && loc.getBlock().getType() == Material.GLASS) {
                    // Use thread-safe put operation
                    gates.put(loc, gateData);
                }
            });
        } catch (IOException e) {
            plugin.getLogger().warning("No gates file found, starting fresh");
        }
    }
}