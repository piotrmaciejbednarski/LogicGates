package com.piotrbednarski.logicgatesplugin.commands;

import com.piotrbednarski.logicgatesplugin.LogicGatesPlugin;
import com.piotrbednarski.logicgatesplugin.model.GateType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

/// Handles execution of the /logicgates command and its subcommands.
/// Provides functionality for administering and interacting with logic gates in-game.
public class LogicGatesCommand implements CommandExecutor {

    private final LogicGatesPlugin plugin;
    private static final String ADMIN_PERMISSION = "logicgates.admin";

    /// Constructs a new LogicGatesCommand with a reference to the main plugin instance
    /// @param plugin The LogicGatesPlugin instance
    public LogicGatesCommand(LogicGatesPlugin plugin) {
        this.plugin = plugin;
    }

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
            case "give" -> handleGiveCommand(sender, args);
            case "inspect" -> handleInspectCommand(sender);
            case "rotate" -> handleRotateCommand(sender);
            case "toggleinput" -> handleToggleInputCommand(sender);
            case "colors" -> sendColorInformation(sender);
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

    // region Command Handlers

    /// Handles debug command to toggle debug mode
    /// @param sender Command sender
    private void handleDebugCommand(CommandSender sender) {
        if (!validateAdminPermission(sender)) return;
        plugin.toggleDebugMode((Player) sender);
    }

    private void handleReload(CommandSender sender) {
        if (!validateAdminPermission(sender)) return;
        plugin.reloadConfiguration();
        sender.sendMessage("LogicGates config reloaded!");
    }

    private void handleToggleInputCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("errors.player_only"));
            return;
        }

        if (!validatePermission(player, "logicgates.toggleinput")) return;

        plugin.getInputToggleModePlayers().add(player.getUniqueId());
        player.sendMessage(plugin.getMessage("toggle_input_mode"));
    }

    /// Handles item giving command
    /// @param sender Command sender
    /// @param args Command arguments
    private void handleGiveCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("errors.player_only"));
            return;
        }

        if (!validatePermission(player, "logicgates.give")) return;

        if (!plugin.getGatesConfig().isConfigurationSection("carpets")) {
            player.sendMessage(plugin.getMessage("command_disabled"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getMessage("give_usage"));
            return;
        }

        processItemGive(player, args[1]);
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

    /// Handles rotation mode activation
    /// @param sender Command sender
    private void handleRotateCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("errors.player_only"));
            return;
        }

        if (!validatePermission(player, "logicgates.rotate")) return;

        plugin.getRotationModePlayers().add(player.getUniqueId());
        player.sendMessage(plugin.getMessage("rotate_mode"));
    }

    /// Handles plugin configuration save
    /// @param sender Command sender
    private void handleSaveCommand(CommandSender sender) {
        if (!validateAdminPermission(sender)) return;
        plugin.saveGates();
        sender.sendMessage(plugin.getMessage("save_success"));
    }

    // endregion

    // region Utility Methods

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

    /// Creates and gives gate item to player based on configuration
    /// @param player Target player
    /// @param gateTypeArg Gate type argument
    private void processItemGive(Player player, String gateTypeArg) {
        try {
            GateType type = GateType.valueOf(gateTypeArg.toUpperCase());
            ConfigurationSection itemSection = plugin.getGatesConfig()
                    .getConfigurationSection("carpets." + type.name() + ".item");

            if (itemSection == null) {
                player.sendMessage(plugin.getMessage("no_config_for_gate", type.name()));
                return;
            }

            Material material = Material.matchMaterial(itemSection.getString("material", ""));
            if (material == null) {
                player.sendMessage(plugin.getMessage("invalid_material_in_config",
                        itemSection.getString("material")));
                return;
            }

            ItemStack item = createConfiguredItem(itemSection, material);
            player.getInventory().addItem(item);
            player.sendMessage(plugin.getMessage("item_given", type.name()));
        } catch (IllegalArgumentException ex) {
            player.sendMessage(plugin.getMessage("invalid_gate_type", gateTypeArg));
        }
    }

    /// Creates configured item stack from configuration section
    /// @param itemSection Configuration section with item data
    /// @param material Item material
    /// @return Configured item stack
    private ItemStack createConfiguredItem(ConfigurationSection itemSection, Material material) {
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

    // endregion

    // region Information Senders

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

    /// Sends color coding information to sender
    /// @param sender Command sender
    private void sendColorInformation(CommandSender sender) {
        sender.sendMessage(plugin.getMessageWithoutPrefix("colors_header"));
        sendClickableMessage(
                sender,
                "Press the button to go to the documentation:\n",
                "[Go to documentation]",
                ChatColor.YELLOW,
                "https://piotrmaciejbednarski.github.io/logicgates-docs/features/gates/",
                "Click to open documentation"
        );
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
                "https://piotrmaciejbednarski.github.io/logicgates-docs/tutorials/create-first-gate/",
                "Click to open documentation"
        );
    }

    /// Sends help information to sender
    /// @param sender Command sender
    private void sendHelpInformation(CommandSender sender) {
        sender.sendMessage(plugin.getMessageWithoutPrefix("help_header"));
        sendMultipleMessages(sender, "help_colors", "help_howto", "help_rotate",
                "help_inspect", "help_particles", "help_save",
                "help_fixparticles", "help_language", "help_redstonecompatibility",
                "help_give", "help_toggleinput", "help_timer");
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
        if (languageCode.equals("en") || languageCode.equals("pl")) {
            plugin.setDefaultLang(languageCode);
            plugin.saveGates();
            sender.sendMessage(plugin.getMessage("language_changed"));
        } else {
            sender.sendMessage(plugin.getMessage("language_invalid"));
        }
    }

    // endregion
}