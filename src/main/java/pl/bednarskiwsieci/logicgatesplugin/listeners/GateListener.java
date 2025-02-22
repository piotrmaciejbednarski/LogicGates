package pl.bednarskiwsieci.logicgatesplugin.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import pl.bednarskiwsieci.logicgatesplugin.LogicGatesPlugin;
import pl.bednarskiwsieci.logicgatesplugin.commands.LogicGatesCommand;
import pl.bednarskiwsieci.logicgatesplugin.model.GateData;
import pl.bednarskiwsieci.logicgatesplugin.model.GateType;
import pl.bednarskiwsieci.logicgatesplugin.util.ConfigManager;
import pl.bednarskiwsieci.logicgatesplugin.util.GateUtils;
import pl.bednarskiwsieci.logicgatesplugin.util.UpdateChecker;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/// Listener class for handling logic gates-related events in the LogicGatesPlugin.
/// This includes block placement, block breaking, redstone changes, player interactions, and player quit events.
public class GateListener implements Listener {

    // Define the rotation order for gate directions (North -> East -> South -> West)
    public static final BlockFace[] ROTATION_ORDER = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private final LogicGatesPlugin plugin;
    private final ConfigManager configManager;
    private final UpdateChecker updateChecker;

    /// Constructor for GateListener.
    ///
    /// @param plugin The main plugin instance.
    public GateListener(LogicGatesPlugin plugin, ConfigManager configManager, UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.updateChecker = updateChecker;
    }

    /// Determines the direction a player is facing based on their yaw.
    ///
    /// @param player the player whose facing direction is to be determined.
    /// @return the BlockFace direction the player is facing (NORTH, EAST, SOUTH, or WEST).
    public static BlockFace getPlayerFacingDirection(Player player) {
        float yaw = player.getLocation().getYaw();

        if (yaw < 0) {
            yaw += 360;
        }

        if (yaw >= 315 || yaw < 45) {
            return BlockFace.SOUTH;
        } else if (yaw >= 45 && yaw < 135) {
            return BlockFace.WEST;
        } else if (yaw >= 135 && yaw < 225) {
            return BlockFace.NORTH;
        } else if (yaw >= 225) {
            return BlockFace.EAST;
        }

        return BlockFace.NORTH;
    }

    /// Handles the BlockPlaceEvent to create logic gates when a carpet is placed on a glass block.
    ///
    /// @param event The BlockPlaceEvent triggered when a block is placed.
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placedBlock = event.getBlock();
        Player player = event.getPlayer();

        // Check if the placed block is a carpet that represents a logic gate
        if (plugin.getCarpetTypes().containsKey(placedBlock.getType())) {
            // Verify if the item used to place the block matches the configuration
            if (configManager.getConfig().isConfigurationSection("carpets")) {
                GateType type = plugin.getCarpetTypes().get(placedBlock.getType());
                ConfigurationSection gateSection = configManager.getConfig().getConfigurationSection("carpets." + type.name());
                if (gateSection != null && gateSection.isConfigurationSection("item")) {
                    ConfigurationSection itemSection = gateSection.getConfigurationSection("item");
                    assert itemSection != null;

                    // Get expected material, name, and lore from the configuration
                    Material expectedMaterial = Material.matchMaterial(itemSection.getString("material", ""));
                    String expectedName = ChatColor.translateAlternateColorCodes('&', itemSection.getString("name", ""));
                    List<String> expectedLore = itemSection.getStringList("lore")
                            .stream()
                            .map(s -> ChatColor.translateAlternateColorCodes('&', s))
                            .collect(Collectors.toList());

                    // Get the item used to place the block
                    ItemStack placedItem = event.getItemInHand();

                    // Verify the item's material, name, and lore
                    ItemMeta meta = placedItem.getItemMeta();
                    if (expectedMaterial != placedItem.getType() ||
                            meta == null ||
                            !meta.hasDisplayName() ||
                            !meta.getDisplayName().equals(expectedName) ||
                            !meta.hasLore() ||
                            !Objects.equals(meta.getLore(), expectedLore)) {
                        return; // Exit if the item does not match the configuration
                    }
                }
            }

            // Check if the block below the carpet is glass
            Block glassBlockBelow = placedBlock.getRelative(BlockFace.DOWN);
            if (glassBlockBelow.getType() == Material.GLASS) {
                if (!player.hasPermission("logicgates.place")) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getMessage("errors.no_permission"));
                    return;
                }

                // Create a new gate
                GateType type = plugin.getCarpetTypes().get(placedBlock.getType());
                GateData data = new GateData(getPlayerFacingDirection(player), type);
                boolean defaultState = GateUtils.calculateOutput(type, false, false, false, data);
                data.setState(defaultState);

                // Force initial update bypassing cooldown
                plugin.getGates().put(glassBlockBelow.getLocation(), data);

                plugin.updateGate(glassBlockBelow);
                plugin.saveGates();

                player.sendMessage(plugin.getMessage("gate_created", type.name()));
            }
        }

        // Update neighboring gates after placing a block
        for (BlockFace face : ROTATION_ORDER) {
            Block neighbor = placedBlock.getRelative(face);
            if (neighbor.getType() == Material.GLASS && plugin.getGates().containsKey(neighbor.getLocation())) {
                GateData data = plugin.getGates().get(neighbor.getLocation());
                if (data == null || !plugin.hasActivationCarpet(neighbor)) {
                    return;
                }

                plugin.updateGate(neighbor);

                // Check the gate's output block for invalid materials
                Block outputBlock = neighbor.getRelative(data.getFacing());
                if (outputBlock.getType() == Material.REDSTONE_WIRE ||
                        outputBlock.getType() == Material.REPEATER ||
                        outputBlock.getType() == Material.REDSTONE_TORCH ||
                        outputBlock.getType() == Material.COMPARATOR ||
                        outputBlock.getType() == Material.REDSTONE_WALL_TORCH) {
                    event.getPlayer().sendMessage(plugin.getMessage("invalid_output_material"));
                }
            }
        }
    }

    /// Handles the BlockBreakEvent to remove logic gates when a glass block or carpet is broken.
    ///
    /// @param event The BlockBreakEvent triggered when a block is broken.
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block brokenBlock = event.getBlock();
        Player player = event.getPlayer();

        // Handle breaking of glass blocks (gates)
        if (isBlockGate(event, player, brokenBlock)) return;

        // Handle breaking of carpets (activation blocks for gates)
        if (plugin.getCarpetTypes().containsKey(brokenBlock.getType())) {
            Block gateBlock = brokenBlock.getRelative(BlockFace.DOWN);
            if (gateBlock.getType() == Material.GLASS && plugin.getGates().containsKey(gateBlock.getLocation())) {
                if (!player.hasPermission("logicgates.break")) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getMessage("errors.no_permission"));
                    return;
                }

                // Cancel the event to prevent default carpet breaking
                event.setCancelled(true);

                // Remove gate data
                plugin.getGates().remove(gateBlock.getLocation());
                plugin.saveGates();
                player.sendMessage(plugin.getMessage("gate_removed"));

                // Create and add a special carpet item to the player's inventory
                GateType type = plugin.getCarpetTypes().get(brokenBlock.getType());
                ItemStack carpetItem = createGateItem(type);
                if (carpetItem != null) {
                    player.getInventory().addItem(carpetItem).values().forEach(item -> {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    });
                }

                // Remove the carpet from the world
                brokenBlock.setType(Material.AIR);
                return;
            }
        }

        // Update neighboring gates after breaking a block
        for (BlockFace face : ROTATION_ORDER) {
            Block neighbor = brokenBlock.getRelative(face);
            if (neighbor.getType() == Material.GLASS && plugin.getGates().containsKey(neighbor.getLocation())) {
                GateData data = plugin.getGates().get(neighbor.getLocation());
                if (data == null || !plugin.hasActivationCarpet(neighbor)) {
                    return;
                }

                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.updateGate(neighbor), 1L);
            }
        }
    }

    private boolean isBlockGate(BlockBreakEvent event, Player player, Block gateBlock) {
        if (gateBlock.getType() == Material.GLASS && plugin.getGates().containsKey(gateBlock.getLocation())) {
            if (!player.hasPermission("logicgates.break")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessage("errors.no_permission"));
                return true;
            }

            // Remove the gate and associated carpet
            plugin.getGates().remove(gateBlock.getLocation());
            plugin.saveGates();
            player.sendMessage(plugin.getMessage("gate_removed"));

            // Remove the carpet above the glass block
            Block carpetBlock = gateBlock.getRelative(BlockFace.UP);
            if (plugin.getCarpetTypes().containsKey(carpetBlock.getType())) {
                carpetBlock.setType(Material.AIR);
            }

            return true;
        }
        return false;
    }

    private ItemStack createGateItem(GateType type) {
        ConfigurationSection gateSection = configManager.getConfig().getConfigurationSection("carpets." + type.name());
        if (gateSection != null && gateSection.isConfigurationSection("item")) {
            ConfigurationSection itemSection = gateSection.getConfigurationSection("item");
            Material material = Material.matchMaterial(itemSection.getString("material", ""));
            if (material == null) return null;

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return item;

            // Set the name and lore according to the configuration
            String name = ChatColor.translateAlternateColorCodes('&', itemSection.getString("name", ""));
            List<String> lore = itemSection.getStringList("lore").stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList());

            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);

            return item;
        }
        return null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (configManager.getConfig().getBoolean("update_checker.notify_on_join") &&
                updateChecker.isUpdateAvailable() &&
                event.getPlayer().hasPermission("logicgates.update")) {

            String message = plugin.getMessage("update_checker.notify_join",
                    updateChecker.getLatestVersion(), updateChecker.getDownloadUrl());

            event.getPlayer().sendMessage(message);
        }
    }

    /// Handles the BlockRedstoneEvent to update gates when redstone power changes.
    ///
    /// @param event The BlockRedstoneEvent triggered when redstone power changes.
    @EventHandler
    public void onRedstoneChange(BlockRedstoneEvent event) {
        // Mark adjacent glass blocks as needing updates when redstone changes
        for (BlockFace face : BlockFace.values()) {
            Block possibleGate = event.getBlock().getRelative(face);
            if (possibleGate.getType() == Material.GLASS) {
                plugin.getGatesToUpdate().add(possibleGate.getLocation());
            }
        }
    }

    /// Handles the PlayerInteractEvent for gate inspection and rotation.
    ///
    /// @param event The PlayerInteractEvent triggered when a player interacts with a block.
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        // Handle input toggle mode
        if (plugin.getInputToggleModePlayers().contains(player.getUniqueId())) {
            event.setCancelled(true);

            if (!player.hasPermission("logicgates.toggleinput")) {
                plugin.getInputToggleModePlayers().remove(player.getUniqueId());
                player.sendMessage(plugin.getMessage("errors.no_permission"));
                return;
            }

            GateData data = plugin.getGates().get(clicked.getLocation());
            if (data == null) return;

            if (
                    data.getType() == GateType.TIMER
                            || data.getType() == GateType.NOT
                            || data.getType() == GateType.RS_LATCH
            ) {
                plugin.getInputToggleModePlayers().remove(player.getUniqueId());
                return; // Ignore TIMER, NOT, RS_LATCH gates
            }

            boolean newState = !data.isThreeInput();
            data.setThreeInput(newState); // Toggle

            player.sendMessage(plugin.getMessage("gate_input_toggled",
                    data.getType().name(),
                    newState ? "3" : "2"));

            plugin.saveGates();
            plugin.getInputToggleModePlayers().remove(player.getUniqueId());
        }

        // Handle inspection mode
        if (plugin.getInspectionModePlayers().contains(player.getUniqueId())) {
            event.setCancelled(true);

            // Check permissions
            if (!player.hasPermission("logicgates.inspect")) {
                plugin.getInspectionModePlayers().remove(player.getUniqueId());
                player.sendMessage(plugin.getMessage("errors.no_permission"));
                return;
            }

            GateData data = plugin.getGates().get(clicked.getLocation());
            if (data == null) return;

            // Get input states using debug logic
            BlockFace leftInputDir = GateUtils.rotateCounterClockwise(data.getFacing(), ROTATION_ORDER);
            BlockFace rightInputDir = GateUtils.rotateClockwise(data.getFacing(), ROTATION_ORDER);
            BlockFace backInputDir = data.getFacing().getOppositeFace();

            boolean leftState = plugin.getRedstoneState(clicked, leftInputDir);
            boolean rightState = plugin.getRedstoneState(clicked, rightInputDir);
            boolean backState = false;

            if (data.isThreeInput()) {
                backState = plugin.getRedstoneState(clicked, backInputDir);
                if (clicked.getRelative(backInputDir).getType() == Material.AIR) {
                    backState = false;
                }
            }

            // Calculate output using unified logic
            boolean output = GateUtils.calculateOutput(data.getType(), leftState, rightState, backState, data);

            // Format inspection message
            player.sendMessage(plugin.getMessage("inspect_header"));
            player.sendMessage(plugin.getMessage("inspect_type", data.getType().name()));
            player.sendMessage(plugin.getMessage("inspect_facing", data.getFacing().name()));
            player.sendMessage(plugin.getMessage("inspect_output",
                    plugin.formatRedstoneState(output)));

            plugin.getInspectionModePlayers().remove(player.getUniqueId());
        }

        // Handle timer cooldown mode
        if (plugin.getCooldownModePlayers().contains(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.getCooldownModePlayers().remove(player.getUniqueId());

            GateData data = plugin.getGates().get(clicked.getLocation());
            if (data == null || data.getType() != GateType.TIMER) {
                player.sendMessage(plugin.getMessage("errors.not_timer_gate"));
                return;
            }

            Integer cooldown = plugin.getPendingCooldowns().remove(player.getUniqueId());
            if (cooldown != null) {
                data.setInterval(cooldown * 1000L); // Convert seconds to miliseconds
                plugin.saveGates();
                player.sendMessage(plugin.getMessage("timer_cooldown_set_success", String.valueOf(cooldown)));
            }
        }

        // Handle rotation wand usage
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getItem() != null) {
            ItemStack item = event.getItem();

            LogicGatesCommand logicGatesCommand = new LogicGatesCommand(plugin, configManager, updateChecker);

            // Check if the item is a rotation wand
            if (logicGatesCommand.isRotationWand(item)) {
                // Cancel the default block interaction
                event.setCancelled(true);

                // Check if the player has permission to rotate gates
                if (!player.hasPermission("logicgates.rotate")) {
                    player.sendMessage(plugin.getMessage("errors.no_permission"));
                    return;
                }

                // Check if a block was clicked
                if (clicked == null) return;

                // Check if the clicked block is a gate
                if (clicked.getType() == Material.GLASS && plugin.hasActivationCarpet(clicked)) {
                    // Get the gate data
                    GateData data = plugin.getGates().get(clicked.getLocation());
                    if (data != null) {
                        // Rotate the gate
                        plugin.rotateGate(clicked);
                        // Send a message to the player indicating the gate's new facing direction
                        player.sendMessage(plugin.getMessage("gate_rotated", data.getFacing().name()));
                        // Save the updated gate data
                        plugin.saveGates();
                    } else {
                        // Send a message to the player indicating that the clicked block is not a gate
                        player.sendMessage(plugin.getMessage("errors.not_a_gate"));
                    }
                } else {
                    // Send a message to the player indicating that the clicked block is not a gate
                    player.sendMessage(plugin.getMessage("errors.not_a_gate"));
                }
            }
        }
    }

    /// Handles the InventoryClickEvent to manage interactions with a custom GUI.
    ///
    /// @param event the InventoryClickEvent triggered when a player clicks in an inventory.
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check if it's our GUI
        String guiTitle = ChatColor.translateAlternateColorCodes('&', plugin.getMessageWithoutPrefix("gui_title"));
        if (!event.getView().getTitle().equals(guiTitle)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // Check if the item has the correct tag
        ItemMeta meta = clickedItem.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "logic_gate");

        if (!data.has(key, PersistentDataType.STRING)) return;
        String gateType = data.get(key, PersistentDataType.STRING);

        // Create a new item from the configuration
        ConfigurationSection gateSection = configManager.getConfig()
                .getConfigurationSection("carpets." + gateType);

        if (gateSection != null) {
            ConfigurationSection itemSection = gateSection.getConfigurationSection("item");
            Material material = Material.matchMaterial(itemSection.getString("material", ""));

            LogicGatesCommand logicGatesCommand = new LogicGatesCommand(plugin, configManager, updateChecker);
            if (material != null) {
                ItemStack itemToGive = logicGatesCommand.createConfiguredItem(itemSection, material);
                player.getInventory().addItem(itemToGive);
                player.sendMessage(plugin.getMessage("item_given", gateType));
            }
        }
    }

    /// Handles the PlayerQuitEvent to remove players from debug mode when they quit.
    ///
    /// @param event The PlayerQuitEvent triggered when a player quits the game.
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getDebugPlayers().remove(event.getPlayer().getUniqueId());
    }
}