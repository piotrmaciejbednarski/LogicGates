package com.piotrbednarski.logicgatesplugin;

import com.piotrbednarski.logicgatesplugin.commands.LogicGatesCommand;
import com.piotrbednarski.logicgatesplugin.listeners.GateListener;
import com.piotrbednarski.logicgatesplugin.model.GateData;
import com.piotrbednarski.logicgatesplugin.model.GateType;
import com.piotrbednarski.logicgatesplugin.util.ConfigManager;
import com.piotrbednarski.logicgatesplugin.util.GateUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;

import static com.piotrbednarski.logicgatesplugin.listeners.GateListener.ROTATION_ORDER;

/// Main plugin class for Logic Gates implementation in Minecraft.
/// Handles gate management, configuration, and event processing.
public class LogicGatesPlugin extends JavaPlugin {

    //region Data Storage
    private final Map<Location, GateData> gates = new HashMap<>();
    private final Set<Location> gatesToUpdate = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> debugPlayers = new HashSet<>();
    private final Set<UUID> rotationModePlayers = new HashSet<>();
    private final Set<UUID> inspectionModePlayers = new HashSet<>();
    private final Set<UUID> cooldownModePlayers = new HashSet<>();
    private final Map<UUID, Integer> pendingCooldowns = new HashMap<>();
    private final Map<Location, Long> lastUpdateTimes = new HashMap<>();

    // Mapping of carpet colors to gate types
    private final Map<Material, GateType> carpetTypes = new HashMap<>() {{
        put(Material.RED_CARPET, GateType.XOR);
        put(Material.BLUE_CARPET, GateType.AND);
        put(Material.GREEN_CARPET, GateType.OR);
        put(Material.BLACK_CARPET, GateType.NOT);
        put(Material.YELLOW_CARPET, GateType.NAND);
        put(Material.WHITE_CARPET, GateType.NOR);
        put(Material.CYAN_CARPET, GateType.XNOR);
        put(Material.MAGENTA_CARPET, GateType.IMPLICATION);
        put(Material.ORANGE_CARPET, GateType.RS_LATCH);
        put(Material.BROWN_CARPET, GateType.TIMER);
    }};
    //endregion

    //region Plugin Settings
    private boolean particlesEnabled = true;
    private int particleViewDistance = 16;
    private boolean redstoneCompatibility = false;
    private long cooldownMs = 100;
    private String defaultLang = "en";
    //endregion

    //region Task Management
    private BukkitTask timerGateUpdateTask;
    private BukkitTask particleTask;
    private ConfigManager configManager;
    //endregion

    //region Plugin Lifecycle
    @Override
    public void onEnable() {
        initializeConfigFiles();
        configManager = new ConfigManager(this);

        // Load configuration and gates
        configManager.loadPluginSettings();
        configManager.loadGates(gates);

        // Register plugin components
        registerCommands();
        registerEventListeners();

        // Start background tasks
        startScheduledTasks();
        saveGates();

        getLogger().info(
                String.format("Version %s (API %s) Enabled - made by Piotr Bednarski",
                        getDescription().getVersion(),
                        getDescription().getAPIVersion()
                )
        );
    }

    @Override
    public void onDisable() {
        cancelTasks();
        cleanupData();

        getLogger().info(
                String.format("Version %s (API %s) Disabled - made by Piotr Bednarski",
                        getDescription().getVersion(),
                        getDescription().getAPIVersion()
                )
        );
    }
    //endregion

    //region Configuration Management
    private void initializeConfigFiles() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) saveResource("messages.yml", false);

        File gatesFile = new File(getDataFolder(), ConfigManager.CONFIG_FILE_NAME);
        if (!gatesFile.exists()) saveResource(ConfigManager.CONFIG_FILE_NAME, false);
    }
    //endregion

    //region Component Registration
    private void registerCommands() {
        Objects.requireNonNull(getCommand("logicgates")).setExecutor(new LogicGatesCommand(this));
    }

    private void registerEventListeners() {
        getServer().getPluginManager().registerEvents(new GateListener(this), this);
    }
    //endregion

    //region Language support
    /// Retrieves a localized message with a prefix from the configuration.
    ///
    /// The method looks up the message under "messages.&lt;currentLang&gt;.&lt;key&gt;".
    /// If the message is not found, it falls back to the English version and adds a missing translation notice.
    /// It then appends the configured prefix (if any) to the message and translates color codes.
    ///
    ///
    /// @param key  the key of the message to retrieve
    /// @param args optional arguments to be formatted into the message
    /// @return the formatted message with the prefix and color codes translated
    public String getMessage(String key, Object... args) {
        // Determine the current language setting.
        String currentLang = defaultLang;
        // Construct the configuration key for the message.
        String messageKey = "messages." + currentLang + "." + key;
        // Retrieve the message from the configuration; default to an empty string if not found.
        String message = getMessages().getString(messageKey, "");
        // If the message is empty, fall back to the English translation.
        if (message.isEmpty()) {
            message = getMessages().getString("messages.en." + key, "&cMissing translation: " + key);
        }
        // Retrieve the prefix for the current language, if defined.
        String prefix = getMessages().getString("messages." + currentLang + ".prefix", "");
        // Format the message with the provided arguments, append the prefix, and translate color codes.
        return ChatColor.translateAlternateColorCodes('&', prefix + String.format(message, args));
    }

    /// Retrieves a localized message without a prefix from the configuration.
    ///
    /// This method functions similarly to [#getMessage(String,Object...)], but it does not prepend
    /// any prefix to the message. If the message is missing in the current language, it falls back to the English version.
    ///
    ///
    /// @param key  the key of the message to retrieve
    /// @param args optional arguments to be formatted into the message
    /// @return the formatted message without the prefix, with color codes translated
    public String getMessageWithoutPrefix(String key, Object... args) {
        // Determine the current language setting.
        String currentLang = defaultLang;
        // Construct the configuration key for the message.
        String messageKey = "messages." + currentLang + "." + key;
        // Retrieve the message from the configuration; default to an empty string if not found.
        String message = getMessages().getString(messageKey, "");
        // If the message is empty, fall back to the English translation.
        if (message.isEmpty()) {
            message = getMessages().getString("messages.en." + key, "&cMissing translation: " + key);
        }
        // Format the message with the provided arguments and translate color codes.
        return ChatColor.translateAlternateColorCodes('&', String.format(message, args));
    }
    //endregion

    //region Gate management
    /// Updates the gate's output based on its current redstone inputs
    ///
    /// This method checks if the gate has the proper activation carpet and if the cooldown period
    /// has elapsed before processing the inputs. It then calculates the new output state, plays a sound
    /// for feedback, and schedules the update of the redstone power on the output block.
    ///
    ///
    /// @param gateBlock the block representing the gate
    public void updateGate(Block gateBlock) {
        // Retrieve the location of the gate block.
        Location loc = gateBlock.getLocation();
        // Retrieve the stored data for the gate at this location.
        GateData data = gates.get(loc);

        // If no data is found or if the gate doesn't have the required activation carpet
        if (data == null || !hasActivationCarpet(gateBlock)) {
            return;
        }

        long now = System.currentTimeMillis();
        // Check if the gate is in cooldown. If the last update was too recent, skip processing.
        if (lastUpdateTimes.containsKey(loc) && now - lastUpdateTimes.get(loc) < cooldownMs) {
            return;
        }

        // Record the time of the current update.
        lastUpdateTimes.put(loc, now);

        // Get the output block relative to the gate's facing direction.
        Block outputBlock = gateBlock.getRelative(data.getFacing());

        // If a carpet is present at the standard output location, shift the output block one further block in the facing direction.
        if (carpetTypes.containsKey(outputBlock.getType())) {
            outputBlock = outputBlock.getRelative(data.getFacing(), 1);
        }

        // Determine the redstone input states from the left and right sides of the gate.
        boolean leftState = getRedstoneState(gateBlock, GateUtils.rotateCounterClockwise(data.getFacing(), ROTATION_ORDER));
        boolean rightState = getRedstoneState(gateBlock, GateUtils.rotateClockwise(data.getFacing(), ROTATION_ORDER));
        // Calculate the output state based on the gate's type and the input states.
        boolean output = GateUtils.calculateOutput(data.getType(), leftState, rightState, data);

        // Output debugging information for the gate update.
        debugGateUpdate(gateBlock, data, leftState, rightState, output);

        // Update the stored output state in GateData
        data.setState(output);

        // Play a sound at the gate's location for user feedback.
        gateBlock.getWorld().playSound(loc, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.1f, 0.1f);

        // Schedule the task to update the redstone power of the output block one tick later.
        Block finalOutputBlock = outputBlock;
        Bukkit.getScheduler().runTaskLater(this, () ->
                GateUtils.setRedstonePower(finalOutputBlock, output ? 15 : 0), 1L);
    }
    //endregion

    //region Redstone state methods
    /// Checks the redstone state at a specific face of a block
    /// @param gateBlock The base block of the gate
    /// @param face The face to check for redstone input
    /// @return True if redstone signal is present
    public boolean getRedstoneState(Block gateBlock, BlockFace face) {
        Block targetBlock = gateBlock.getRelative(face);
        Material type = targetBlock.getType();

        if (!redstoneCompatibility) {
            return checkStandardRedstoneComponents(targetBlock, type);
        }
        return checkCompatibilityRedstoneComponents(targetBlock, type, face);
    }

    /// Checks if the given block, when used as a standard redstone component, is active (powered).
    ///
    /// @param targetBlock the block to check
    /// @param type the material type of the redstone component
    /// @return `true` if the component is active, `false` otherwise
    private boolean checkStandardRedstoneComponents(Block targetBlock, Material type) {
        // Retrieve the block data once to avoid repeated calls
        BlockData data = targetBlock.getBlockData();

        switch (type) {
            case REDSTONE_TORCH:
            case REDSTONE_WALL_TORCH:
                // For torches, check if they are lit
                return ((Lightable) data).isLit();
            case REDSTONE_WIRE:
                // Redstone wire is active if it has a power level greater than 0
                return targetBlock.getBlockPower() > 0;
            case REDSTONE_BLOCK:
                // A redstone block is always powered
                return true;
            case REPEATER:
            case COMPARATOR:
                // For repeaters and comparators, check if they are powered
                return ((Powerable) data).isPowered();
            default:
                // Fallback: use the block power as the activation indicator
                return targetBlock.getBlockPower() > 0;
        }
    }

    /// Checks if the given block, when used as a redstone component in compatibility mode, is active.
    ///
    /// @param targetBlock the block to check
    /// @param type the material type of the redstone component
    /// @param face the specific block face to consider for directional components
    /// @return `true` if the component is active in compatibility mode, `false` otherwise
    private boolean checkCompatibilityRedstoneComponents(Block targetBlock, Material type, BlockFace face) {
        // Retrieve the block data once to avoid repeated calls
        BlockData data = targetBlock.getBlockData();

        switch (type) {
            case REDSTONE_WALL_TORCH:
                // Check if the wall torch is lit
                return ((Lightable) data).isLit();
            case REDSTONE_WIRE:
                // For redstone wire, ensure the connection on the given face is of type SIDE and the block is powered
                RedstoneWire wire = (RedstoneWire) data;
                return wire.getFace(face) == RedstoneWire.Connection.SIDE && targetBlock.getBlockPower() > 0;
            case REDSTONE_BLOCK:
                // A redstone block is always powered
                return true;
            case REPEATER:
            case COMPARATOR:
                Directional directional = (Directional) data;
                if (directional.getFacing().getOppositeFace() == BlockFace.EAST
                        || directional.getFacing().getOppositeFace() == BlockFace.WEST) {
                    Powerable powerable = (Powerable) targetBlock.getBlockData();
                    return powerable.isPowered();
                }
                return false;
            default:
                // Fallback: use the block power as the activation indicator
                return targetBlock.getBlockPower() > 0;
        }
    }
    //endregion

    //region Utility Methods
    /// Resets the redstone output of the gate block.
    ///
    /// The method retrieves the gate data to determine its facing direction.
    /// If no data is found, it defaults to NORTH.
    /// It then sets the redstone power of the block relative to the gate's facing to 0.
    ///
    ///
    /// @param gateBlock the gate block whose output should be reset
    public void resetOutput(Block gateBlock) {
        GateData data = gates.get(gateBlock.getLocation());
        BlockFace facing = (data != null) ? data.getFacing() : BlockFace.NORTH;
        GateUtils.setRedstonePower(gateBlock.getRelative(facing), 0);
    }

    /// Checks if the gate has an activation carpet.
    ///
    /// The method determines whether there is a carpet block one block above the given gate block.
    ///
    ///
    /// @param gate the gate block to check for an activation carpet
    /// @return `true` if the block above the gate is a carpet from the predefined types, `false` otherwise
    public boolean hasActivationCarpet(Block gate) {
        return carpetTypes.containsKey(gate.getRelative(BlockFace.UP).getType());
    }

    /// Rotates the gate's facing direction.
    ///
    /// The method retrieves the current gate data and calculates the new facing based on the predefined rotation order.
    /// It then updates the gate data, stores it, and triggers an update for the gate.
    ///
    ///
    /// @param gateBlock the gate block to rotate
    public void rotateGate(Block gateBlock) {
        GateData data = gates.get(gateBlock.getLocation());
        if (data == null) {
            return;
        }

        int currentIndex = Arrays.asList(ROTATION_ORDER).indexOf(data.getFacing());
        int newIndex = (currentIndex + 1) % ROTATION_ORDER.length;
        data.setFacing(ROTATION_ORDER[newIndex]);

        gates.put(gateBlock.getLocation(), data);
        updateGate(gateBlock);
    }

    /// Saves the current configuration to file and clears all stored data.
    private void cleanupData() {
        gates.clear();
        debugPlayers.clear();
        rotationModePlayers.clear();
        inspectionModePlayers.clear();
    }

    /// Saves all gates to the configuration file.
    public void saveGates() {
        // Clear previous gate configuration and save current gates
        configManager.getConfig().set(ConfigManager.CONFIG_GATES, null);
        gates.forEach((loc, data) -> configManager.saveGate(loc, data));
        configManager.saveToFile();
    }

    /// Cancels the current particle task (if any) and restarts it.
    public void fixParticlesTask() {
        if (particleTask != null) {
            particleTask.cancel();
        }
        startParticleTask();
    }

    /// Formats the given location as a string in the format "X:{x}, Y:{y}, Z:{z}".
    ///
    /// @param loc the location to format
    /// @return a formatted string representing the location
    public String formatLocation(Location loc) {
        return String.format("X:%d, Y:%d, Z:%d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /// Returns a formatted string representing the redstone state.
    ///
    /// @param state the redstone state (true for active, false for inactive)
    /// @return the localized string for the given redstone state
    public String formatRedstoneState(boolean state) {
        return state ? getMessageWithoutPrefix("redstone_active")
                : getMessageWithoutPrefix("redstone_inactive");
    }

    /// Toggles debug mode for the specified player.
    ///
    /// @param player the player whose debug mode is to be toggled
    public void toggleDebugMode(Player player) {
        // Toggle the player's debug mode status and send a feedback message
        if (debugPlayers.remove(player.getUniqueId())) {
            player.sendMessage("Debug mode OFF");
        } else {
            debugPlayers.add(player.getUniqueId());
            player.sendMessage("Debug mode ON");
        }
    }
    //endregion

    //region Task Scheduling
    /// Starts all scheduled tasks for gate updates, particles, and processing.
    private void startScheduledTasks() {
        startTimerUpdateTask();
        startParticleTask();
        startUpdateProcessingTask();
    }

    /// Starts a repeating task to update timer gates every tick.
    private void startTimerUpdateTask() {
        timerGateUpdateTask = Bukkit.getScheduler().runTaskTimer(this, () ->
                // Iterate over all gates and update those of TIMER type
                gates.forEach((loc, data) -> {
                    if (data.getType() == GateType.TIMER) {
                        updateGate(loc.getBlock());
                    }
                }), 0L, 1L);
    }

    /// Starts a repeating task to display particles near gates.
    private void startParticleTask() {
        particleTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!particlesEnabled) return; // Exit if particles are disabled
            gates.forEach((loc, data) -> {
                Block gateBlock = loc.getBlock();
                // Only show particles for glass gates with an activation carpet
                if (gateBlock.getType() == Material.GLASS && hasActivationCarpet(gateBlock)) {
                    GateUtils.showParticles(this, gateBlock, data.getType(), data.getFacing(), particleViewDistance);
                }
            });
        }, 0L, 10L);
    }

    /// Starts a repeating task to process pending gate updates asynchronously.
    private void startUpdateProcessingTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            // Copy locations to process and clear the update set
            Set<Location> toProcess = new HashSet<>(gatesToUpdate);
            gatesToUpdate.clear();

            // Process updates asynchronously to avoid blocking the main thread
            Bukkit.getScheduler().runTaskAsynchronously(this, () ->
                    toProcess.forEach(loc -> {
                        // Only update if the block type is GLASS
                        if (loc.getBlock().getType() == Material.GLASS) {
                            Bukkit.getScheduler().runTask(this, () -> updateGate(loc.getBlock()));
                        }
                    })
            );
        }, 0L, 0L);
    }

    /// Cancels scheduled tasks for timer gate updates and particle effects.
    private void cancelTasks() {
        if (timerGateUpdateTask != null) {
            timerGateUpdateTask.cancel();
        }
        if (particleTask != null) {
            particleTask.cancel();
        }
    }
    //endregion

    //region Debugging and Messages
    /// Sends a debug update message for a gate to all registered debug players.
    ///
    /// @param gateBlock   the block representing the gate
    /// @param data        the data associated with the gate
    /// @param leftInput   the state of the left redstone input
    /// @param rightInput  the state of the right redstone input
    /// @param outputResult the computed output state of the gate
    public void debugGateUpdate(Block gateBlock, GateData data,
                                boolean leftInput, boolean rightInput, boolean outputResult) {
        // Exit early if there are no debug players to notify
        if (debugPlayers.isEmpty()) return;

        // Create a debug message using a text block
        String debugInfo = String.format("""
            &6[DEBUG]&e Gate at %s
            &7Type:&f %s
            &7Facing:&f %s
            &7Input 1:&f %s
            &7Input 2:&f %s
            &7Output:&f %s
            """,
                formatLocation(gateBlock.getLocation()),
                data.getType().name(),
                data.getFacing().name(),
                formatRedstoneState(leftInput),
                formatRedstoneState(rightInput),
                formatRedstoneState(outputResult)
        );

        // Translate color codes and broadcast the debug message
        String formatted = ChatColor.translateAlternateColorCodes('&', debugInfo);
        broadcastDebugMessage(gateBlock, formatted);
    }

    /// Broadcasts a debug message to all debug players who are within a valid distance of the specified gate.
    ///
    /// @param gateBlock the gate block associated with the debug message
    /// @param message   the debug message to be sent
    private void broadcastDebugMessage(Block gateBlock, String message) {
        for (UUID uuid : debugPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            // Only send the message if the player is online and within range
            if (p != null && isValidRecipient(p, gateBlock)) {
                p.sendMessage(message);
            }
        }
    }

    /// Checks whether a player is within an acceptable distance from the gate block to receive debug messages.
    ///
    /// @param p         the player to check
    /// @param gateBlock the gate block from which the debug message originates
    /// @return `true` if the player is in the same world and within 16 blocks (256 distance squared); `false` otherwise
    private boolean isValidRecipient(Player p, Block gateBlock) {
        return p.getWorld().equals(gateBlock.getWorld()) &&
                p.getLocation().distanceSquared(gateBlock.getLocation()) <= 256;
    }
    //endregion

    //region Getters/Setters
    /// Returns the map of gate locations and their corresponding data.
    ///
    /// @return the gates map
    public Map<Location, GateData> getGates() {
        return gates;
    }

    public Set<UUID> getCooldownModePlayers() {
        return cooldownModePlayers;
    }

    public Map<UUID, Integer> getPendingCooldowns() {
        return pendingCooldowns;
    }

    /// Returns the set of gate locations that require updates.
    ///
    /// @return the set of gate locations to update
    public Set<Location> getGatesToUpdate() {
        return gatesToUpdate;
    }

    /// Returns the map associating carpet materials with gate types.
    ///
    /// @return the carpet types map
    public Map<Material, GateType> getCarpetTypes() {
        return carpetTypes;
    }

    /// Returns the set of player UUIDs who are in debug mode.
    ///
    /// @return the set of debug players
    public Set<UUID> getDebugPlayers() {
        return debugPlayers;
    }

    /// Returns the set of player UUIDs who are in rotation mode.
    ///
    /// @return the set of players in rotation mode
    public Set<UUID> getRotationModePlayers() {
        return rotationModePlayers;
    }

    /// Returns the set of player UUIDs who are in inspection mode.
    ///
    /// @return the set of players in inspection mode
    public Set<UUID> getInspectionModePlayers() {
        return inspectionModePlayers;
    }

    /// Checks if particle effects are enabled.
    ///
    /// @return `true` if particles are enabled, otherwise `false`
    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    /// Enables or disables particle effects.
    ///
    /// @param particlesEnabled `true` to enable particles, `false` to disable them
    public void setParticlesEnabled(boolean particlesEnabled) {
        this.particlesEnabled = particlesEnabled;
    }

    /// Returns the distance within which particles are visible.
    ///
    /// @return the particle view distance
    public int getParticleViewDistance() {
        return particleViewDistance;
    }

    /// Sets the distance within which particles are visible.
    ///
    /// @param particleViewDistance the new particle view distance
    public void setParticleViewDistance(int particleViewDistance) {
        this.particleViewDistance = particleViewDistance;
    }

    /// Checks if redstone compatibility mode is enabled.
    ///
    /// @return `true` if redstone compatibility is enabled, otherwise `false`
    public boolean isRedstoneCompatibility() {
        return redstoneCompatibility;
    }

    /// Enables or disables redstone compatibility mode.
    ///
    /// @param redstoneCompatibility `true` to enable compatibility, `false` to disable it
    public void setRedstoneCompatibility(boolean redstoneCompatibility) {
        this.redstoneCompatibility = redstoneCompatibility;
    }

    /// Returns the cooldown duration in milliseconds.
    ///
    /// @return the cooldown duration in ms
    public long getCooldownMs() {
        return cooldownMs;
    }

    /// Sets the cooldown duration in milliseconds.
    ///
    /// @param cooldownMs the new cooldown duration in ms
    public void setCooldownMs(long cooldownMs) {
        this.cooldownMs = cooldownMs;
    }

    /// Returns the default language code.
    ///
    /// @return the default language
    public String getDefaultLang() {
        return defaultLang;
    }

    /// Sets the default language code.
    ///
    /// @param defaultLang the new default language
    public void setDefaultLang(String defaultLang) {
        this.defaultLang = defaultLang;
    }

    /// Loads and returns the messages configuration from "messages.yml".
    ///
    /// @return the messages configuration
    public FileConfiguration getMessages() {
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }

    /// Loads and returns the gates configuration from the configured file.
    ///
    /// @return the gates configuration
    public FileConfiguration getGatesConfig() {
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), ConfigManager.CONFIG_FILE_NAME));
    }

    /// Converts a Location object to its string representation.
    ///
    /// @param loc the location to convert
    /// @return the string representation of the location
    public String convertLocationToString(Location loc) {
        return GateUtils.convertLocationToString(loc);
    }

    /// Converts a string representation back to a Location object.
    ///
    /// @param str the string representation of the location
    /// @return the corresponding Location object
    public Location convertStringToLocation(String str) {
        return GateUtils.convertStringToLocation(str);
    }

    public void reloadConfiguration() {
        ConfigManager configManager = new ConfigManager(this);
        configManager.reloadConfiguration();
        configManager.loadGates(gates);
    }
    //endregion
}
