package com.piotrbednarski.logicgatesplugin;

import com.piotrbednarski.logicgatesplugin.commands.LogicGatesCommand;
import com.piotrbednarski.logicgatesplugin.integrations.WorldEditIntegration;
import com.piotrbednarski.logicgatesplugin.listeners.GateListener;
import com.piotrbednarski.logicgatesplugin.model.GateData;
import com.piotrbednarski.logicgatesplugin.model.GateType;
import com.piotrbednarski.logicgatesplugin.util.ConfigManager;
import com.piotrbednarski.logicgatesplugin.util.GateUtils;
import com.piotrbednarski.logicgatesplugin.util.GatesConfigManager;
import com.piotrbednarski.logicgatesplugin.util.UpdateChecker;
import com.sk89q.worldedit.WorldEdit;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.piotrbednarski.logicgatesplugin.listeners.GateListener.ROTATION_ORDER;

/// Main plugin class for Logic Gates implementation in Minecraft.
/// Handles gate management, configuration, and event processing.
public class LogicGatesPlugin extends JavaPlugin {

    //region Data Storage
    private final ConcurrentHashMap<Location, GateData> gates = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Location> gatesToUpdate = new ConcurrentLinkedQueue<>();
    private final Set<UUID> debugPlayers = new HashSet<>();
    private final Set<UUID> rotationModePlayers = new HashSet<>();
    private final Set<UUID> inspectionModePlayers = new HashSet<>();
    private final Set<UUID> inputToggleModePlayers = new HashSet<>();
    private final Set<UUID> cooldownModePlayers = new HashSet<>();
    private final Map<UUID, Integer> pendingCooldowns = new HashMap<>();

    // Tick management system
    private volatile long serverTick = 0L;
    private final ConcurrentHashMap<Location, Long> lastUpdateTicks = new ConcurrentHashMap<>();

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
    private String defaultLang = "en";
    //endregion

    //region Task Management
    private BukkitTask timerGateUpdateTask;
    private BukkitTask particleTask;
    private ConfigManager configManager;
    private GatesConfigManager gatesConfigManager;
    private UpdateChecker updateChecker;
    //endregion

    //region Plugin Lifecycle
    @Override
    public void onEnable() {
        initializeConfigFiles();
        configManager = new ConfigManager(this);
        gatesConfigManager = new GatesConfigManager(this);
        updateChecker = new UpdateChecker(this);

        // Load configuration and gates
        configManager.loadPluginSettings();
        gatesConfigManager.loadGates(gates);

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

        // Initialize bStats
        int BSTATS_ID = 24700;
        Metrics metrics = new Metrics(this, BSTATS_ID);

        // Automatic update check on startup
        try {
            if (updateChecker.shouldCheckAutomatically()) {
                getLogger().info("Performing automatic update check...");
                updateChecker.checkForUpdates(null);
            }
        } catch (Exception e) {
            getLogger().severe("An error occurred while checking for updates");
        }

        // Register WorldEdit Integration
        try {
            WorldEdit.getInstance().getEventBus().register(new WorldEditIntegration(this));
            getLogger().info("WorldEdit Integration has been registered");
        } catch (Exception e) {
            getLogger().severe("Failed to register WorldEdit integration");
        }
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

        File configFile = new File(getDataFolder(), ConfigManager.CONFIG_FILE_NAME);
        if (!configFile.exists()) saveResource(ConfigManager.CONFIG_FILE_NAME, false);
    }
    //endregion

    //region Component Registration
    private void registerCommands() {
        Objects.requireNonNull(getCommand("logicgates")).setExecutor(new LogicGatesCommand(this, configManager, updateChecker));
    }

    private void registerEventListeners() {
        getServer().getPluginManager().registerEvents(new GateListener(this, configManager, updateChecker), this);
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
    private void initializeTickSystem() {
        // Update tick counter every server tick (50ms)
        Bukkit.getScheduler().runTaskTimer(this, () -> serverTick++, 0L, 1L);
    }

    private void scheduleDependentUpdates(Block outputBlock, BlockFace facing) {
        // Check all potential connection directions
        EnumSet<BlockFace> directions = EnumSet.of(
                facing,
                facing.getOppositeFace(),
                GateUtils.rotateClockwise(facing, ROTATION_ORDER),
                GateUtils.rotateCounterClockwise(facing, ROTATION_ORDER)
        );

        directions.forEach(dir -> {
            Location dependentLoc = outputBlock.getRelative(dir).getLocation();
            if(gates.containsKey(dependentLoc)) {
                gatesToUpdate.offer(dependentLoc);
            }
        });
    }

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
        if (data == null || !hasActivationCarpet(gateBlock)) return;

        // Get the output block relative to the gate's facing direction.
        final BlockFace facing = data.getFacing();
        Block outputBlock = gateBlock.getRelative(facing);

        boolean currentPhysicalState = getRedstoneState(gateBlock, facing);

        // Bypass cooldown for initial state mismatch
        boolean forceUpdate = (data.getState() != currentPhysicalState);

        // Tick-based cooldown check (2 ticks minimum)
        Long lastTick = lastUpdateTicks.get(loc);
        if(lastTick != null && (serverTick - lastTick) < 2) return;

        // Store current tick before processing
        lastUpdateTicks.put(loc, serverTick);

        // If a carpet is present at the standard output location, shift the output block one further block in the facing direction.
        if (carpetTypes.containsKey(outputBlock.getType())) {
            outputBlock = outputBlock.getRelative(facing, 1);
        }

        BlockFace leftFacing = GateUtils.rotateCounterClockwise(facing, ROTATION_ORDER);
        BlockFace rightFacing = GateUtils.rotateClockwise(facing, ROTATION_ORDER);
        BlockFace backFacing = facing.getOppositeFace();

        boolean leftState;
        boolean rightState;
        boolean backState;

        // Determine the redstone input states from the left and right sides of the gate.
        leftState = getRedstoneState(gateBlock, leftFacing);
        rightState = getRedstoneState(gateBlock, rightFacing);
        backState = false;

        if (data.isThreeInput()) {
            backState = getRedstoneState(gateBlock, backFacing);
            if (gateBlock.getRelative(backFacing).getType() == Material.AIR) {
                backState = false;
            }
        }

        // Force false if input block is air
        if (gateBlock.getRelative(leftFacing).getType() == Material.AIR) leftState = false;
        if (gateBlock.getRelative(rightFacing).getType() == Material.AIR) rightState = false;
        if (gateBlock.getRelative(backFacing).getType() == Material.AIR) backState = false;

        // Calculate the output state based on the gate's type and the input states.
        boolean output = GateUtils.calculateOutput(data.getType(), leftState, rightState, backState, data);

        // Always update if:
        // 1. Physical state differs from logical state (initialization)
        // 2. Output changed

        boolean needsUpdate = true;

        // Ignore checking when RS_LATCH
        if (data.getType() != GateType.RS_LATCH) needsUpdate = forceUpdate || (data.getState() != output);

        if (needsUpdate) {
            GateUtils.setRedstonePower(outputBlock, output ? 15 : 0);
            data.setState(output);
            debugGateUpdate(gateBlock, data, leftState, rightState, backState, output);
        }

        scheduleDependentUpdates(outputBlock, facing);
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
        if (type == Material.AIR) return false;

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
        gatesConfigManager.saveGates(gates);
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
        initializeTickSystem();
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

    /// Starts a repeating task to process pending gate updates asynchronously
    private void startUpdateProcessingTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            // Process in batches
            List<Location> batch = new ArrayList<>(gatesToUpdate.size());
            while(true) {
                Location loc = gatesToUpdate.poll();
                if(loc == null) break;
                batch.add(loc);
            }

            batch.forEach(loc -> {
                Block block = loc.getBlock();
                if(block.getType() == Material.GLASS) {
                    updateGate(block);
                }
            });
        }, 1L, 1L);
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
                                boolean leftInput, boolean rightInput, boolean backInput, boolean outputResult) {
        // Exit early if there are no debug players to notify
        if (debugPlayers.isEmpty()) return;

        // Create a debug message using a text block
        String debugInfo = String.format("""
            &6[DEBUG]&e Gate at %s
            &7Type:&f %s
            &7Facing:&f %s
            &7Input 1:&f %s
            &7Input 2:&f %s
            &7Input 3:&f %s
            &7Output:&f %s
            """,
                formatLocation(gateBlock.getLocation()),
                data.getType().name(),
                data.getFacing().name(),
                formatRedstoneState(leftInput),
                formatRedstoneState(rightInput),
                formatRedstoneState(backInput),
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
    public ConcurrentLinkedQueue<Location> getGatesToUpdate() {
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

    public Set<UUID> getInputToggleModePlayers() {
        return inputToggleModePlayers;
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
        configManager.reloadConfiguration();
        gatesConfigManager.loadGates(gates);
    }
    //endregion
}
