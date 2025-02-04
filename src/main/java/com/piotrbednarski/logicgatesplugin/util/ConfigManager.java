package com.piotrbednarski.logicgatesplugin.util;

import com.piotrbednarski.logicgatesplugin.LogicGatesPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/// Handles configuration management for logic gates plugin.
/// Manages saving/loading gates and plugin settings to/from YAML file.
public class ConfigManager {

    // Configuration keys
    public static final String CONFIG_FILE_NAME = "config.yml";
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

    /// Reloads configuration from disk
    public void reloadConfiguration() {
        configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            plugin.saveResource(CONFIG_FILE_NAME, false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        loadPluginSettings();
    }

    /// Persists current configuration to disk
    public void saveToFile() {
        try {
            FileConfiguration diskConfig = YamlConfiguration.loadConfiguration(configFile);
            diskConfig.set(CONFIG_PARTICLES_ENABLED, plugin.isParticlesEnabled());
            diskConfig.set(CONFIG_REDSTONE_COMPATIBILITY, plugin.isRedstoneCompatibility());
            diskConfig.set(CONFIG_PARTICLES_VIEW_DISTANCE, plugin.getParticleViewDistance());
            diskConfig.set(CONFIG_LANGUAGE, plugin.getDefaultLang());

            diskConfig.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + CONFIG_FILE_NAME + ": " + e.getMessage());
        }
    }
}