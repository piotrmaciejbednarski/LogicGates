package com.piotrbednarski.logicgatesplugin.util;

import com.piotrbednarski.logicgatesplugin.LogicGatesPlugin;
import com.piotrbednarski.logicgatesplugin.model.GateData;
import com.piotrbednarski.logicgatesplugin.model.GateType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/// Handles configuration management for logic gates plugin.
/// Manages saving/loading gates and plugin settings to/from YAML file.
public class ConfigManager {

    // Configuration keys
    public static final String CONFIG_FILE_NAME = "gates.yml";
    public static final String CONFIG_GATES = "gates";
    public static final String CONFIG_PARTICLES_ENABLED = "particlesEnabled";
    public static final String CONFIG_REDSTONE_COMPATIBILITY = "redstoneCompatibility";
    public static final String CONFIG_PARTICLES_VIEW_DISTANCE = "particlesViewDistance";
    public static final String CONFIG_LANGUAGE = "language";

    private final LogicGatesPlugin plugin;
    private File configFile;
    private FileConfiguration config;

    /// Initializes configuration manager with plugin instance
    /// @param plugin Main plugin instance
    public ConfigManager(LogicGatesPlugin plugin) {
        this.plugin = plugin;
        initializeConfiguration();
    }

    /// Sets up configuration file and loads it
    private void initializeConfiguration() {
        configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);

        // Create new config file if it doesn't exist
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create " + CONFIG_FILE_NAME + ": " + e.getMessage());
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /// @return Current file configuration instance
    public FileConfiguration getConfig() {
        return config;
    }

    /// Loads plugin settings from configuration file
    public void loadPluginSettings() {
        plugin.setRedstoneCompatibility(config.getBoolean(CONFIG_REDSTONE_COMPATIBILITY, false));
        plugin.setParticlesEnabled(config.getBoolean(CONFIG_PARTICLES_ENABLED, true));
        plugin.setParticleViewDistance(config.getInt(CONFIG_PARTICLES_VIEW_DISTANCE, 16));
        plugin.setDefaultLang(config.getString(CONFIG_LANGUAGE, "en"));
    }

    /// Saves gate data to configuration
    /// @param location Gate location in world
    /// @param gateData Gate configuration data
    public void saveGate(Location location, GateData gateData) {
        String gateKey = plugin.convertLocationToString(location);
        String baseKey = CONFIG_GATES + "." + gateKey;

        // Store gate properties in nested structure
        config.set(baseKey + ".facing", gateData.getFacing().name());
        config.set(baseKey + ".type", gateData.getType().name());
        config.set(baseKey + ".state", gateData.getState());
        config.set(baseKey + ".interval", gateData.getInterval());
        config.set(baseKey + ".lastToggleTime", gateData.getLastToggleTime());

        saveToFile();
    }

    /// Reloads configuration from disk
    public void reloadConfiguration() {
        configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            plugin.saveResource(CONFIG_FILE_NAME, false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        loadPluginSettings();
    }

    /// Loads all gates from configuration into memory
    /// @param gates Map to populate with loaded gate data
    public void loadGates(Map<Location, GateData> gates) {
        plugin.setRedstoneCompatibility(config.getBoolean(CONFIG_REDSTONE_COMPATIBILITY, false));

        if (config.contains(CONFIG_GATES)) {
            // Iterate through all stored gate entries
            Objects.requireNonNull(config.getConfigurationSection(CONFIG_GATES))
                    .getKeys(false)
                    .forEach(key -> {
                        Location loc = plugin.convertStringToLocation(key);

                        // Validate gate block still exists and is valid
                        if (loc != null && loc.getBlock().getType() == Material.GLASS) {
                            String facingStr = config.getString(CONFIG_GATES + "." + key + ".facing");
                            String typeStr = config.getString(CONFIG_GATES + "." + key + ".type");
                            boolean state = config.getBoolean(CONFIG_GATES + "." + key + ".state", false);
                            long interval = config.getLong(CONFIG_GATES + "." + key + ".interval", 1000);
                            long lastToggleTime = config.getLong(CONFIG_GATES + "." + key + ".lastToggleTime", 0);

                            if (facingStr != null && typeStr != null) {
                                GateData data = new GateData(
                                        org.bukkit.block.BlockFace.valueOf(facingStr),
                                        GateType.valueOf(typeStr)
                                );
                                data.setState(state);
                                data.setInterval(interval);
                                data.setLastToggleTime(lastToggleTime);
                                gates.put(loc, data);
                            }
                        }
                    });
        }
    }

    /// Persists current configuration to disk
    public void saveToFile() {
        try {
            FileConfiguration diskConfig = YamlConfiguration.loadConfiguration(configFile);
            diskConfig.set(CONFIG_PARTICLES_ENABLED, plugin.isParticlesEnabled());
            diskConfig.set(CONFIG_REDSTONE_COMPATIBILITY, plugin.isRedstoneCompatibility());
            diskConfig.set(CONFIG_PARTICLES_VIEW_DISTANCE, plugin.getParticleViewDistance());
            diskConfig.set(CONFIG_LANGUAGE, plugin.getDefaultLang());

            if (config.contains(CONFIG_GATES)) {
                diskConfig.set(CONFIG_GATES, config.get(CONFIG_GATES));
            }

            diskConfig.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + CONFIG_FILE_NAME + ": " + e.getMessage());
        }
    }
}