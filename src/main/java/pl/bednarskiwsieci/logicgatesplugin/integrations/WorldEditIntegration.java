package pl.bednarskiwsieci.logicgatesplugin.integrations;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import pl.bednarskiwsieci.logicgatesplugin.LogicGatesPlugin;
import pl.bednarskiwsieci.logicgatesplugin.model.GateData;
import pl.bednarskiwsieci.logicgatesplugin.model.GateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class shows an example of how to implement a "batch" approach to marking gates before
 * a WorldEdit operation, thereby reducing the number of runTaskLater(...) calls and minimizing
 * repeated data fetches.
 */
public class WorldEditIntegration implements Listener {

    private final LogicGatesPlugin plugin;

    public WorldEditIntegration(LogicGatesPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Intercepts player commands beginning with "//" (except "//paste") to batch-process
     * gates in the selected region. Collects all valid gates and updates them in one or two passes.
     */
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) throws IncompleteRegionException {
        String command = event.getMessage();
        Player player = event.getPlayer();

        if (command.startsWith("//") && !command.startsWith("//paste")) {
            var sessionManager = WorldEdit.getInstance().getSessionManager();
            var localSession = sessionManager.get(BukkitAdapter.adapt(player));
            var selectionWorld = localSession.getSelectionWorld();
            Region selectedRegion = localSession.getSelection(selectionWorld);

            // Collect all valid gates first
            List<Block> gateBlocks = new ArrayList<>();
            for (BlockVector3 blockPos : selectedRegion) {
                Block baseBlock = BukkitAdapter.adapt(selectionWorld)
                        .getBlockAt(blockPos.x(), blockPos.y(), blockPos.z());
                Block carpetBlock = baseBlock.getRelative(BlockFace.UP);
                if (isValidGate(baseBlock, carpetBlock)) {
                    gateBlocks.add(baseBlock);
                }
            }

            // Process all gates in one batch
            for (Block glassBlock : gateBlocks) {
                GateData gateData = plugin.getGates().get(glassBlock.getLocation());
                if (gateData != null) {
                    // Temporarily change glass to chest and store gate data
                    glassBlock.setType(Material.CHEST);
                    BlockState chestState = glassBlock.getState();
                    if (chestState instanceof Chest chest) {
                        chest.getBlockInventory().setItem(0, new ItemStack(Material.WRITABLE_BOOK));
                        BookMeta bookMeta = (BookMeta) chest.getBlockInventory().getItem(0).getItemMeta();
                        Map<String, Object> gateMetadata = new HashMap<>();

                        // Store the facing and other data
                        gateMetadata.put("facing", gateData.getFacing().name());
                        gateMetadata.put("isThreeInput", gateData.isThreeInput());
                        gateMetadata.put("state", gateData.getState());

                        if (gateData.getType() == GateType.TIMER) {
                            gateMetadata.put("interval", gateData.getInterval());
                        }

                        Gson gson = new Gson();
                        bookMeta.addPage(gson.toJson(gateMetadata));
                        chest.getBlockInventory().getItem(0).setItemMeta(bookMeta);
                    }
                }
            }

            // Revert all blocks back to glass in one scheduled task
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Block glassBlock : gateBlocks) {
                    if (glassBlock.getType() == Material.CHEST) {
                        glassBlock.setType(Material.GLASS);
                    }
                }
            }, 1L);
        }
    }

    /**
     * Intercepts block placements as part of a WorldEdit EditSession, restoring gate blocks from
     * chest-with-book to glass gates in batches.
     */
    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        Actor actor = event.getActor();

        if (actor != null && actor.isPlayer() && event.getStage() == EditSession.Stage.BEFORE_CHANGE) {
            List<BlockVector3> changedPositions = new ArrayList<>();

            event.setExtent(new AbstractDelegateExtent(event.getExtent()) {
                @Override
                public BaseBlock getFullBlock(BlockVector3 position) {
                    // Track the changed position
                    changedPositions.add(position);
                    // Return the block from the underlying extent
                    return super.getFullBlock(position);
                }
            });

            // After the blocks have been tracked, schedule a single task to handle them all
            Bukkit.getScheduler().runTask(plugin, () -> {
                World bukkitWorld = Bukkit.getWorld(event.getWorld().getName());
                if (bukkitWorld != null) {
                    restoreGatesInBatch(bukkitWorld, changedPositions, actor);
                }
            });
        }
    }

    /**
     * Batch method that processes all changed positions in one go, checking if any are
     * valid chests-with-book and converting them back to gates.
     */
    private void restoreGatesInBatch(World world, List<BlockVector3> positions, Actor actor) {
        Player player = Bukkit.getPlayerExact(actor.getName());
        if (player == null) return;

        // Minimizes repeated lookups by reading from the block state just once per position
        for (BlockVector3 pos : positions) {
            Block baseBlock = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (baseBlock.getType() == Material.CHEST) {
                Block carpetBlock = baseBlock.getRelative(BlockFace.UP);

                // Default values
                BlockFace facing = BlockFace.EAST;
                Boolean isThreeInput = false;
                Boolean state = false;
                Long interval = 1000L;

                if (carpetBlock.getType().toString().endsWith("_CARPET")) {
                    // Retrieve data from Chest
                    BlockState chestState = baseBlock.getState();
                    if (chestState instanceof Chest chest) {
                        ItemStack storedBook = chest.getBlockInventory().getItem(0);
                        if (storedBook != null && storedBook.getItemMeta() instanceof BookMeta meta) {
                            Gson gson = new Gson();
                            JsonObject data;

                            try {
                                data = gson.fromJson(meta.getPage(1), JsonObject.class);
                            } catch (JsonSyntaxException e) {
                                // The book exists but contains invalid JSON
                                return;
                            } catch (IllegalArgumentException e) {
                                // Invalid page number
                                return;
                            }

                            // Retrieve original data
                            facing = BlockFace.valueOf(data.get("facing").getAsString());
                            isThreeInput = Boolean.valueOf(data.get("isThreeInput").getAsString());
                            state = Boolean.valueOf(data.get("state").getAsString());
                            if (data.get("interval") != null) {
                                interval = Long.valueOf(data.get("interval").getAsString());
                            }

                            // Convert the chest to glass
                            baseBlock.setType(Material.GLASS);
                        }
                    }
                }

                // Check if it's a valid gate with a carpet on top
                if (isValidGate(baseBlock, carpetBlock)) {
                    GateType gateType = plugin.getCarpetTypes().get(carpetBlock.getType());

                    // Apply original data
                    GateData gateData = new GateData(facing, gateType);
                    if (state != null) {
                        gateData.setState(state);
                    }

                    if (isThreeInput != null) {
                        gateData.setThreeInput(isThreeInput);
                    }

                    if (interval != null) {
                        gateData.setInterval(interval);
                    }

                    registerNewGate(baseBlock, gateData);
                }
            }
        }
    }

    /**
     * Checks whether the given block and the block above it fulfill the gate criteria (glass + activation carpet).
     */
    private boolean isValidGate(Block baseBlock, Block carpetBlock) {
        return baseBlock.getType() == Material.GLASS
                && plugin.hasActivationCarpet(baseBlock);
    }

    /**
     * Registers the new gate data in the plugin.
     */
    private void registerNewGate(Block baseBlock, GateData data) {
        plugin.getGates().put(baseBlock.getLocation(), data);
        plugin.updateGate(baseBlock);
        plugin.saveGates();
    }
}