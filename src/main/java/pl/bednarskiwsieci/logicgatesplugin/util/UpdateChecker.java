package pl.bednarskiwsieci.logicgatesplugin.util;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import pl.bednarskiwsieci.logicgatesplugin.LogicGatesPlugin;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class UpdateChecker {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
    // GitHub repository details (CHANGE THESE)
    private static final String GITHUB_USER = "piotrmaciejbednarski";
    private static final String REPO_NAME = "LogicGates";
    private static final String REPO_URL = "https://api.github.com/repos/%s/%s/releases/latest";
    private final LogicGatesPlugin plugin;
    private String latestVersion;
    private long lastCheckTime;

    public UpdateChecker(LogicGatesPlugin plugin) {
        this.plugin = plugin;
        this.latestVersion = "";
        this.lastCheckTime = 0;
    }

    /**
     * Checks for updates asynchronously
     * @param sender Who to notify about results (can be null)
     */
    public void checkForUpdates(CommandSender sender) {
        // Check if update checker is enabled in config
        if (!plugin.getConfig().getBoolean("update_checker.enabled", true)) {
            if (sender != null) {
                sender.sendMessage(plugin.getMessage("update_checker.disabled"));
            }
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Create API URL
                URL url = new URL(String.format(REPO_URL, GITHUB_USER, REPO_NAME));

                // Configure HTTPS connection
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setConnectTimeout(5000);

                // Handle response
                if (connection.getResponseCode() == 200) {
                    // Read JSON response
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String response = reader.lines().collect(Collectors.joining());
                    reader.close();

                    String downloadUrl = "";

                    // Parse version from JSON
                    try {
                        GitHubRelease release = GSON.fromJson(response, GitHubRelease.class);
                        latestVersion = release.tagName.replace("v", "");
                        downloadUrl = release.htmlUrl;
                    } catch (JsonSyntaxException e) {
                        plugin.getLogger().warning("Invalid JSON response: " + e.getMessage());
                    }

                    lastCheckTime = System.currentTimeMillis();

                    // Save last check time
                    plugin.getConfig().set("last_update_check", lastCheckTime);
                    plugin.saveConfig();

                    // Notify sender if update is available
                    if (sender != null) {
                        if (isUpdateAvailable()) {
                            sendUpdateMessage(sender, downloadUrl);
                        } else {
                            sender.sendMessage(plugin.getMessage("update_checker.up_to_date"));
                        }
                    }
                } else {
                    plugin.getLogger().warning("Failed to check updates: HTTP " + connection.getResponseCode());
                }
                connection.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Update check failed: " + e.getMessage());
                if (sender != null) {
                    sender.sendMessage(plugin.getMessage("update_checker.failed"));
                }
            }
        });
    }

    /**
     * Compares current version with latest version
     * @return true if update is available
     */
    public boolean isUpdateAvailable() {
        if (latestVersion.isEmpty()) return false;

        String currentVersion = plugin.getDescription().getVersion();
        // Split versions into numeric components
        String[] currentParts = currentVersion.split("\\.");
        String[] latestParts = latestVersion.split("\\.");

        // Compare each version segment
        for (int i = 0; i < Math.min(currentParts.length, latestParts.length); i++) {
            int current = Integer.parseInt(currentParts[i]);
            int latest = Integer.parseInt(latestParts[i]);

            if (latest > current) return true;
            if (latest < current) return false;
        }
        // Handle cases like 1.2 vs 1.2.3
        return latestParts.length > currentParts.length;
    }

    public void sendUpdateMessage(CommandSender receiver, String downloadUrl) {
        String message = plugin.getMessage("update_checker.available")
                .replace("%latest%", latestVersion)
                .replace("%current%", plugin.getDescription().getVersion())
                .replace("%url%", downloadUrl);

        receiver.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * Checks if enough time has passed since last check
     * @return true if should check again
     */
    public boolean shouldCheckAutomatically() {
        FileConfiguration config = plugin.getConfig();
        long checkInterval = config.getLong("update_checker.interval_hours", 24);
        long lastCheck = config.getLong("last_update_check", 0);

        return System.currentTimeMillis() - lastCheck >
                TimeUnit.HOURS.toMillis(checkInterval);
    }

    public String getLatestVersion() {
        return this.latestVersion;
    }

    public String getDownloadUrl() {
        return String.format("https://github.com/%s/%s/releases/latest",
                GITHUB_USER,
                REPO_NAME);
    }

    /**
     * Data class for GitHub API response
     */
    private static class GitHubRelease {
        @SerializedName("tag_name")
        String tagName;

        @SerializedName("html_url")
        String htmlUrl;
    }
}
