package pl.bednarskiwsieci.logicgatesplugin.commands;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.bednarskiwsieci.logicgatesplugin.LogicGatesPlugin;
import pl.bednarskiwsieci.logicgatesplugin.model.GateType;
import pl.bednarskiwsieci.logicgatesplugin.util.ConfigManager;
import pl.bednarskiwsieci.logicgatesplugin.util.UpdateChecker;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// Handles execution of the /logicgates command and its subcommands.
/// Provides functionality for administering and interacting with logic gates in-game.
public class LogicGatesCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "logicgates.admin";
    private final LogicGatesPlugin plugin;
    private final ConfigManager configManager;
    private final UpdateChecker updateChecker;

    /// Constructs a new LogicGatesCommand with a reference to the main plugin instance
    /// @param plugin The LogicGatesPlugin instance
    public LogicGatesCommand(LogicGatesPlugin plugin, ConfigManager configManager, UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.updateChecker = updateChecker;
    }

    public static void sendClickableMessage(CommandSender sender, String instructionText, String buttonText,
                                            ChatColor buttonColor, String url, String hoverText) {
        // Create main instruction text component
        TextComponent instruction = new TextComponent(instructionText);
        instruction.setColor(ChatColor.BOLD.asBungee());  // Set text to bold style

        // Create clickable button component
        TextComponent button = new TextComponent(buttonText);
        button.setColor(buttonColor.asBungee());     // Set button text color
        button.setUnderlined(true);       // Add underline effect to button

        // Set click event to open URL when clicked
        button.setClickEvent(new ClickEvent(
                ClickEvent.Action.OPEN_URL,  // Define click action type
                url                         // URL to open
        ));

        // Add hover effect showing tooltip text
        button.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,  // Define hover action type
                new ComponentBuilder(hoverText).create()           // Create hover text component
        ));

        // Combine instruction text with button component
        instruction.addExtra(button);  // Append button to the instruction text

        // Send the composed message to the player
        sender.spigot().sendMessage(instruction);
    }

    // region Command Handlers

    /// Executes the given command, returning its success
    /// @param sender Source of the command
    /// @param cmd Command which was executed
    /// @param label Alias of the command which was used
    /// @param args Passed command arguments
    /// @return true if a valid command, otherwise false
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("logicgates")) {
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage(plugin.getMessage("errors.command_usage"));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "debug" -> handleDebugCommand(sender);
            case "menu" -> handleGUI(sender);
            case "update" -> handleUpdateCheck(sender);
            case "inspect" -> handleInspectCommand(sender);
            case "rotate" -> handleRotateCommand(sender);
            case "toggleinput" -> handleToggleInputCommand(sender);
            case "howto" -> sendHowToInstructions(sender);
            case "help" -> sendHelpInformation(sender);
            case "author" -> sendAuthorInfo(sender);
            case "save" -> handleSaveCommand(sender);
            case "redstonecompatibility" -> handleRedstoneCompatibility(sender, args);
            case "fixparticles" -> handleFixParticles(sender);
            case "particles" -> handleParticlesToggle(sender, args);
            case "language" -> handleLanguageChange(sender, args);
            case "timer" -> handleTimerCooldownSetting(sender, args);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage(plugin.getMessage("unknown_command"));
        }
        return true;
    }

    /// Handles the update check command sent by the specified sender. If the sender has the required admin permissions,
    /// an update check will be initiated, and a message will be sent to the sender indicating that the update check is in progress.
    ///
    /// @param sender the command sender who issued the update check command
    private void handleUpdateCheck(CommandSender sender) {
        if (!validateAdminPermission(sender)) return;

        sender.sendMessage(plugin.getMessage("update_checker.checking"));
        updateChecker.checkForUpdates(sender);
    }

    /// Handles debug command to toggle debug mode
    /// @param sender Command sender
    private void handleDebugCommand(CommandSender sender) {
        if (!validateAdminPermission(sender)) return;
        plugin.toggleDebugMode((Player) sender);
    }

    /// Handles the reload command sent by the specified sender. If the sender has the required admin permissions,
    /// the plugin configuration will be reloaded and a confirmation message will be sent to the sender.
    ///
    /// @param sender the command sender who issued the reload command
    private void handleReload(CommandSender sender) {
        if (!validateAdminPermission(sender)) return;
        plugin.reloadGlobalConfiguration();
        sender.sendMessage("LogicGates config reloaded!");
    }

    /// Handles the toggle input command sent by the specified sender. If the sender is a player and has the required
    /// permissions, the player will be added to the input toggle mode players list and a confirmation message will be sent.
    /// If the sender is not a player, an error message will be sent.
    ///
    /// @param sender the command sender who issued the toggle input command
    private void handleToggleInputCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("errors.player_only"));
            return;
        }

        if (!validatePermission(player, "logicgates.toggleinput")) return;

        plugin.getInputToggleModePlayers().add(player.getUniqueId());
        player.sendMessage(plugin.getMessage("toggle_input_mode"));
    }

    /// Handles gate selection from GUI
    /// @param sender Command sender
    private void handleGUI(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("errors.player_only"));
            return;
        }

        if (!validatePermission(player, "logicgates.give")) return;

        if (!configManager.getConfig().isConfigurationSection("carpets")) {
            player.sendMessage(plugin.getMessage("command_disabled"));
            return;
        }

        openGateGUI(player);
    }

    /// Opens the gate selection GUI for the specified player. The GUI will display all configured gate items
    /// based on the configuration settings. If no gates are configured or the configuration section is missing,
    /// appropriate messages will be sent to the player.
    ///
    /// @param player the player for whom the gate selection GUI will be opened
    private void openGateGUI(Player player) {
        ConfigurationSection carpetsSection = configManager.getConfig().getConfigurationSection("carpets");
        if (carpetsSection == null) {
            player.sendMessage(plugin.getMessage("command_disabled"));
            return;
        }

        Set<String> gateKeys = carpetsSection.getKeys(false);
        if (gateKeys.isEmpty()) {
            player.sendMessage(plugin.getMessage("no_gates_configured"));
            return;
        }

        // Calculate GUI size
        int size = (int) (Math.ceil(gateKeys.size() / 9.0) * 9);
        size = Math.max(9, Math.min(54, size));

        Inventory gui = Bukkit.createInventory(
                null,
                size,
                ChatColor.translateAlternateColorCodes('&', plugin.getMessageWithoutPrefix("gui_title"))
        );

        for (String gateKey : gateKeys) {
            ConfigurationSection gateSection = carpetsSection.getConfigurationSection(gateKey);
            if (gateSection == null) continue;

            ConfigurationSection itemSection = gateSection.getConfigurationSection("item");
            if (itemSection == null) continue;

            Material material = Material.matchMaterial(itemSection.getString("material", ""));
            if (material == null) continue;

            try {
                GateType type = GateType.valueOf(gateKey.toUpperCase());
                ItemStack item = createGUIItem(itemSection, material, type);
                gui.addItem(item);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid gate type in config: " + gateKey);
            }
        }

        player.openInventory(gui);
    }

    /// Creates a GUI item for the specified gate type using the given configuration section and material.
    ///
    /// @param itemSection the configuration section containing item settings
    /// @param material the material of the item to be created
    /// @param type the type of the gate to be associated with the item
    /// @return the created ItemStack with the gate type identifier
    private ItemStack createGUIItem(ConfigurationSection itemSection, Material material, GateType type) {
        ItemStack item = createConfiguredItem(itemSection, material);
        ItemMeta meta = item.getItemMeta();

        // Add gate identifier
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "logic_gate"),
                PersistentDataType.STRING,
                type.name()
        );

        item.setItemMeta(meta);
        return item;
    }

    /// Handles changing timer cooldown setting
    /// @param sender Command sender
    /// @param args Command arguments
    private void handleTimerCooldownSetting(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("errors.only_players"));
            return;
        }

        if (!player.hasPermission("logicgates.timer")) {
            player.sendMessage(plugin.getMessage("errors.no_permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getMessage("timer_usage"));
            return;
        }

        try {
            int cooldown = Integer.parseInt(args[1]);
            if (cooldown < 1) throw new NumberFormatException();

            plugin.getPendingCooldowns().put(player.getUniqueId(), cooldown);
            plugin.getCooldownModePlayers().add(player.getUniqueId());

            player.sendMessage(plugin.getMessage("timer_cooldown_mode_enter", String.valueOf(cooldown)));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessage("errors.cooldown_invalid"));
        }
    }

    //region Rotation mode

    /// Handles inspection mode activation
    /// @param sender Command sender
    private void handleInspectCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("errors.player_only"));
            return;
        }

        if (!validatePermission(player, "logicgates.inspect")) return;

        plugin.getInspectionModePlayers().add(player.getUniqueId());
        player.sendMessage(plugin.getMessage("inspect_mode"));
    }

    /// Handles rotation mode activation and gives the wand
    /// @param sender Command sender
    private void handleRotateCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("errors.player_only"));
            return;
        }

        // Check if the player has permission to use the command
        if (!validatePermission(player, "logicgates.rotate")) return;

        // Check if the player already has a rotation wand in their inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isRotationWand(item)) {
                // If the player already has a rotation wand, send a message to the player
                // saying that they already have a rotation wand
                player.sendMessage(plugin.getMessage("rotate_already_has_wand"));
                return;
            }
        }

        // Create a new rotation wand
        ItemStack wand = createRotationWand();

        // Add the rotation wand to the player's inventory
        player.getInventory().addItem(wand);

        // Send a message to the player
        player.sendMessage(plugin.getMessage("rotate_wand_received"));
    }

    /// Creates a new rotation wand ItemStack. The rotation wand is a stick with a unique custom model data value,
    /// display name, lore, and is unbreakable.
    ///
    /// @return the created rotation wand ItemStack
    private ItemStack createRotationWand() {
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Rotation Wand");
        meta.setLore(List.of(ChatColor.GRAY + "Right-click a logic gate to rotate it."));
        meta.setCustomModelData(1450); // Unique ID
        meta.setUnbreakable(true);
        wand.setItemMeta(meta);
        return wand;
    }

    //endregion

    /// Checks if the given ItemStack is a rotation wand. A rotation wand is identified by being a stick
    /// with a specific custom model data value.
    ///
    /// @param item the ItemStack to check
    /// @return true if the item is a rotation wand, false otherwise
    public boolean isRotationWand(ItemStack item) {
        if (item == null || item.getType() != Material.STICK) return false;
        ItemMeta meta = item.getItemMeta();

        // Find by unique id
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == 1450;
    }

    // endregion

    // region Utility Methods

    /// Handles plugin configuration save
    /// @param sender Command sender
    private void handleSaveCommand(CommandSender sender) {
        if (!validateAdminPermission(sender)) return;
        plugin.saveGates();
        sender.sendMessage(plugin.getMessage("save_success"));
    }

    /// Validates if sender has admin permissions
    /// @param sender Command sender
    /// @return true if has permission, false otherwise
    private boolean validateAdminPermission(CommandSender sender) {
        return validatePermission(sender, ADMIN_PERMISSION);
    }

    /// Validates if sender has specified permission
    /// @param sender Command sender
    /// @param permission Required permission node
    /// @return true if has permission, false otherwise
    private boolean validatePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        sender.sendMessage(plugin.getMessage("errors.no_permission"));
        return false;
    }

    // endregion

    // region Information Senders

    /// Creates configured item stack from configuration section
    /// @param itemSection Configuration section with item data
    /// @param material Item material
    /// @return Configured item stack
    public ItemStack createConfiguredItem(ConfigurationSection itemSection, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Set display name with color codes
        String displayName = ChatColor.translateAlternateColorCodes('&',
                itemSection.getString("name", ""));
        meta.setDisplayName(displayName);

        // Process and set lore with color codes
        List<String> lore = itemSection.getStringList("lore").stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /// Sends how-to instructions to sender
    /// @param sender Command sender
    private void sendHowToInstructions(CommandSender sender) {
        sender.sendMessage(plugin.getMessageWithoutPrefix("howto_header"));

        sendClickableMessage(
                sender,
                "Press the button to go to the documentation:\n",
                "[Go to documentation]",
                ChatColor.YELLOW,
                "https://logicgates.bednarskiwsieci.pl/",
                "Click to open documentation"
        );
    }

    /// Sends help information to sender
    /// @param sender Command sender
    private void sendHelpInformation(CommandSender sender) {
        sender.sendMessage(plugin.getMessageWithoutPrefix("help_header"));
        sendMultipleMessages(sender, "help_menu", "help_howto", "help_rotate",
                "help_inspect", "help_particles", "help_save",
                "help_fixparticles", "help_language", "help_redstonecompatibility",
                "help_toggleinput", "help_timer");
    }

    /// Sends author information to sender
    /// @param sender Command sender
    private void sendAuthorInfo(CommandSender sender) {
        sender.sendMessage(plugin.getMessageWithoutPrefix("author_header"));
        sendMultipleMessages(sender, "author_name", "author_contact");
    }

    /// Sends multiple messages from the plugin's message configuration
    /// @param sender Command sender
    /// @param messageKeys Array of message keys to send
    private void sendMultipleMessages(CommandSender sender, String... messageKeys) {
        for (String key : messageKeys) {
            sender.sendMessage(plugin.getMessage(key));
        }
    }

    // endregion

    // region Configuration Handlers

    /// Handles redstone compatibility toggle
    /// @param sender Command sender
    /// @param args Command arguments
    private void handleRedstoneCompatibility(CommandSender sender, String[] args) {
        if (!validateAdminPermission(sender)) return;

        if (args.length < 2) {
            sender.sendMessage(plugin.getMessage("redstonecompatibility_usage"));
            return;
        }

        String state = args[1].toLowerCase();
        switch (state) {
            case "on" -> {
                plugin.setRedstoneCompatibility(true);
                sender.sendMessage(plugin.getMessage("redstonecompatibility_enabled"));
            }
            case "off" -> {
                plugin.setRedstoneCompatibility(false);
                sender.sendMessage(plugin.getMessage("redstonecompatibility_disabled"));
            }
            default -> sender.sendMessage(plugin.getMessage("redstonecompatibility_invalid_state"));
        }
        plugin.saveGates();
    }

    /// Handles particles system fix command
    /// @param sender Command sender
    private void handleFixParticles(CommandSender sender) {
        if (!validateAdminPermission(sender)) return;
        plugin.fixParticlesTask();
        sender.sendMessage(plugin.getMessage("particles_reloaded"));
    }

    /// Handles particles system toggle
    /// @param sender Command sender
    /// @param args Command arguments
    private void handleParticlesToggle(CommandSender sender, String[] args) {
        if (!validateAdminPermission(sender)) return;

        if (args.length < 2) {
            sender.sendMessage(plugin.getMessage("particles_usage"));
            return;
        }

        String state = args[1].toLowerCase();
        switch (state) {
            case "on" -> {
                plugin.setParticlesEnabled(true);
                sender.sendMessage(plugin.getMessage("particles_enabled"));
            }
            case "off" -> {
                plugin.setParticlesEnabled(false);
                sender.sendMessage(plugin.getMessage("particles_disabled"));
            }
            default -> sender.sendMessage(plugin.getMessage("particles_invalid_state"));
        }
        plugin.saveGates();
    }

    /// Handles language change
    /// @param sender Command sender
    /// @param args Command arguments
    private void handleLanguageChange(CommandSender sender, String[] args) {
        if (!validateAdminPermission(sender)) return;

        if (args.length < 2) {
            sender.sendMessage(plugin.getMessage("language_usage"));
            return;
        }

        String languageCode = args[1].toLowerCase();
        if (plugin.getMessages().get("messages." + languageCode) != null) {
            plugin.setDefaultLang(languageCode);
            plugin.saveGates();
            sender.sendMessage(plugin.getMessage("language_changed"));
        } else {
            sender.sendMessage(plugin.getMessage("language_invalid"));
        }
    }

    // endregion
}