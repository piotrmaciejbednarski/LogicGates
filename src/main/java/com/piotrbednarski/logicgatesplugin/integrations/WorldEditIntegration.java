package com.piotrbednarski.logicgatesplugin.integrations;

import com.piotrbednarski.logicgatesplugin.LogicGatesPlugin;
import com.piotrbednarski.logicgatesplugin.listeners.GateListener;
import com.piotrbednarski.logicgatesplugin.model.GateData;
import com.piotrbednarski.logicgatesplugin.model.GateType;
import com.piotrbednarski.logicgatesplugin.util.GateUtils;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

public class WorldEditIntegration {
    private final LogicGatesPlugin plugin;

    public WorldEditIntegration(LogicGatesPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        Actor actor = event.getActor();

        // Only process player-induced edits
        if (actor != null && actor.isPlayer()) {
            // Wrap the edit session to intercept block placements
            event.setExtent(new AbstractDelegateExtent(event.getExtent()) {
                @Override
                public BaseBlock getFullBlock(BlockVector3 pos) {
                    BaseBlock baseBlock = super.getFullBlock(pos);

                    // Get corresponding Bukkit world
                    World world = Bukkit.getWorld(event.getWorld().getName());

                    if (world != null) {
                        // Schedule gate creation
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            checkAndCreateGate(world, pos, actor);
                        });
                    }

                    return baseBlock;
                }
            });
        }
    }

    /**
     * Checks if a block position qualifies as a logic gate and initializes it
     */
    private void checkAndCreateGate(World world, BlockVector3 pos, Actor actor) {
        Block baseBlock = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Block carpetBlock = baseBlock.getRelative(BlockFace.UP);

        // Check gate construction criteria
        if (isValidGate(baseBlock, carpetBlock)) {
            Player player = Bukkit.getPlayerExact(actor.getName());
            if (player == null) return;

            // Initialize gate data
            GateType gateType = plugin.getCarpetTypes().get(carpetBlock.getType());
            GateData gateData = new GateData(
                    GateListener.getPlayerFacingDirection(player),
                    gateType
            );

            // Calculate initial state and register gate
            gateData.setState(calculateInitialState(gateType, gateData));
            registerNewGate(baseBlock, gateData);
        }
    }

    /**
     * Validates if the block structure matches a gate pattern
     */
    private boolean isValidGate(Block baseBlock, Block carpetBlock) {
        return baseBlock.getType() == Material.GLASS
                && plugin.hasActivationCarpet(baseBlock);
    }

    /**
     * Calculates the initial output state for the gate
     */
    private boolean calculateInitialState(GateType type, GateData data) {
        return GateUtils.calculateOutput(type, false, false, false, data);
    }

    /**
     * Registers the new gate in the plugin system
     */
    private void registerNewGate(Block baseBlock, GateData data) {
        plugin.getGates().put(baseBlock.getLocation(), data);
        plugin.updateGate(baseBlock);
        plugin.saveGates();
    }
}
