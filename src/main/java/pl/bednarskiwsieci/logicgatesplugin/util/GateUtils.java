package pl.bednarskiwsieci.logicgatesplugin.util;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.Comparator;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.Repeater;
import pl.bednarskiwsieci.logicgatesplugin.LogicGatesPlugin;
import pl.bednarskiwsieci.logicgatesplugin.model.GateData;
import pl.bednarskiwsieci.logicgatesplugin.model.GateType;

import java.util.Arrays;

import static pl.bednarskiwsieci.logicgatesplugin.listeners.GateListener.ROTATION_ORDER;

/// Utility class for handling logic gate operations and Minecraft block interactions.
/// Provides methods for location conversion, block rotation, logic calculations, and visual effects.
public final class GateUtils {

    private GateUtils() {
        // Private constructor to prevent instantiation of utility class
    }

    /// Converts a Location object to a standardized string format.
    ///
    /// @param loc The location to convert
    /// @return String in format "worldName_x_y_z" or "invalid_0_0_0" for null input
    public static String convertLocationToString(Location loc) {
        if (loc == null || loc.getWorld() == null)
            return "invalid_0_0_0";
        return String.format("%s_%d_%d_%d",
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ());
    }

    /// Converts a formatted string back to a Location object.
    ///
    /// @param str The string in "worldName_x_y_z" format
    /// @return Corresponding Location or null if parsing fails
    public static Location convertStringToLocation(String str) {
        String[] parts = str.split("_");
        if (parts.length != 4) {
            Bukkit.getLogger().warning("Invalid location format: " + str);
            return null;
        }

        try {
            World world = Bukkit.getWorld(parts[0]);
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to parse location: " + str);
            return null;
        }
    }

    /// Rotates a BlockFace clockwise based on the provided rotation order.
    ///
    /// @param face The original BlockFace to rotate
    /// @param rotationOrder Array defining rotation sequence
    /// @return New rotated BlockFace
    public static BlockFace rotateClockwise(BlockFace face, BlockFace[] rotationOrder) {
        int index = Arrays.asList(rotationOrder).indexOf(face);
        return rotationOrder[(index + 1) % 4]; // Next index
    }

    /// Rotates a BlockFace counter-clockwise based on the provided rotation order.
    ///
    /// @param face The original BlockFace to rotate
    /// @param rotationOrder Array defining rotation sequence
    /// @return New rotated BlockFace
    public static BlockFace rotateCounterClockwise(BlockFace face, BlockFace[] rotationOrder) {
        int index = Arrays.asList(rotationOrder).indexOf(face);
        return rotationOrder[(index + 3) % 4]; // Previous index
    }

    /// Calculates the output of a logic gate based on its type and inputs.
    ///
    /// @param type The gate type to calculate for
    /// @param input1 First input state
    /// @param input2 Second input state (ignored for single-input gates)
    /// @return Resulting output state
    public static boolean calculateOutput(GateType type, boolean input1, boolean input2, boolean input3,
            GateData data) {
        boolean isThreeInput = data.isThreeInput();
        return switch (type) {
            case XNOR -> isThreeInput ? (input1 == input2 && input2 == input3) : (input1 == input2);
            case IMPLICATION -> isThreeInput ? (!input1 || !input2 || input3) : (!input1 || input2);
            case XOR -> {
                if (isThreeInput) {
                    int count = (input1 ? 1 : 0) + (input2 ? 1 : 0) + (input3 ? 1 : 0);
                    yield (count & 1) == 1;
                } else {
                    yield input1 != input2;
                }
            }
            case AND -> isThreeInput ? (input1 && input2 && input3) : (input1 && input2);
            case OR -> isThreeInput ? (input1 || input2 || input3) : (input1 || input2);
            case NOT -> !input1;
            case NAND -> isThreeInput ? !(input1 && input2 && input3) : !(input1 && input2);
            case NOR -> isThreeInput ? !(input1 || input2 || input3) : !(input1 || input2);
            case TIMER -> {
                long currentTime = System.currentTimeMillis();
                long interval = data.getInterval();
                if (currentTime - data.getLastToggleTime() >= interval) {
                    data.setState(!data.getState());
                    data.setLastToggleTime(currentTime);
                }
                yield data.getState();
            }
            case RS_LATCH -> {
                if (input1 && input2) {
                    yield data.getState();
                } else if (input1) {
                    data.setState(true);
                } else if (input2) {
                    data.setState(false);
                }
                yield data.getState();
            }
            default -> false;
        };
    }

    /// Applies redstone power to a block with appropriate type handling.
    ///
    /// @param block The target block to modify
    /// @param power The power level to set (0-15)
    public static synchronized void setRedstonePower(Block block, int power) {
        if (block == null)
            return;

        Material type = block.getType();
        try {
            BlockData data = block.getBlockData();

            switch (type) {
                case REDSTONE_WIRE -> {
                    if (data instanceof RedstoneWire wire) {
                        wire.setPower(power);
                        block.setBlockData(wire);
                    }
                }
                case REDSTONE_LAMP, REDSTONE_TORCH, REDSTONE_WALL_TORCH, FURNACE, CAMPFIRE -> {
                    if (data instanceof Lightable lightable) {
                        lightable.setLit(power > 0);
                        block.setBlockData(lightable);
                    }
                }
                case REPEATER -> {
                    if (data instanceof Repeater repeater) {
                        repeater.setPowered(power > 0);
                        block.setBlockData(repeater);
                    }
                }
                case COMPARATOR -> {
                    if (data instanceof Comparator comparator) {
                        comparator.setPowered(power > 0);
                        block.setBlockData(comparator);
                    }
                }
                case PISTON, STICKY_PISTON -> {
                    if (data instanceof Piston piston) {
                        piston.setExtended(power > 0);
                        block.setBlockData(piston);
                    }
                }
                case TARGET -> {
                    if (data instanceof AnaloguePowerable target) {
                        target.setPower(power > 0 ? 15 : 0);
                        block.setBlockData(target);
                    }
                }
                default -> {
                    if (data instanceof Openable openable) {
                        openable.setOpen(power > 0);
                        block.setBlockData(openable);
                    }
                    if (data instanceof Powerable powerable) {
                        powerable.setPowered(power > 0);
                        block.setBlockData(powerable);
                    }
                }
            }

            block.getState().update(true, true);
        } catch (ClassCastException e) {
            Bukkit.getLogger()
                    .warning("Failed to set redstone power for block (ClassCastException): " + block.getType());
        } catch (Exception e) {
            Bukkit.getLogger().warning(
                    "Failed to set redstone power for block: " + block.getType() + " due to " + e.getMessage());
        }
    }

    /// Displays particles indicating gate inputs and outputs.
    ///
    /// @param plugin Main plugin instance
    /// @param gateBlock The block representing the logic gate
    /// @param gateType Type of the logic gate
    /// @param facing Direction the gate is facing
    /// @param viewDistance Maximum distance from which particles are visible
    public static void showParticles(LogicGatesPlugin plugin,
            Block gateBlock,
            GateType gateType,
            BlockFace facing,
            double viewDistance) {
        World world = gateBlock.getWorld();
        Location center = gateBlock.getLocation().add(0.5, 0.5, 0.5);

        // Check if any player is within view distance
        boolean shouldRender = Bukkit.getOnlinePlayers().stream().anyMatch(p -> p.getWorld().equals(world) &&
                p.getLocation().distanceSquared(center) <= viewDistance * viewDistance);
        if (!shouldRender)
            return;

        // Calculate particle positions relative to gate facing
        double offsetDistance = 0.7; // Distance from center for input/output markers
        Location inputLocation = center.clone().add(
                rotateCounterClockwise(facing, ROTATION_ORDER).getDirection().multiply(offsetDistance));
        Location secondInputLocation = center.clone().add(
                rotateClockwise(facing, ROTATION_ORDER).getDirection().multiply(offsetDistance));
        Location thirdInputLocation = center.clone().add(
                facing.getOppositeFace().getDirection().multiply(offsetDistance));
        Location outputLocation = center.clone().add(
                facing.getDirection().multiply(offsetDistance));

        // Spawn output particles (green)
        if (plugin.getCarpetTypes().containsKey(outputLocation.getBlock().getType())) {
            // Adjust position when output block material is CARPET
            world.spawnParticle(Particle.REDSTONE,
                    center.clone().add(facing.getDirection().multiply(1.4)),
                    5, new Particle.DustOptions(Color.GREEN, 1));
        } else {
            world.spawnParticle(Particle.REDSTONE,
                    outputLocation,
                    5, new Particle.DustOptions(Color.GREEN, 1));
        }

        // Spawn input particles based on gate type
        if (gateType == GateType.NOT) {
            // Determine NOT input location based on plugin configuration
            Location notGateInputLocation = plugin.getNotGateInputPosition().equals("opposite") ? thirdInputLocation
                    : inputLocation;
            // Single red input for NOT gate
            world.spawnParticle(Particle.REDSTONE,
                    notGateInputLocation,
                    5, new Particle.DustOptions(Color.RED, 1));
        } else if (gateType == GateType.TIMER) {
            // Special handling for timer gate
            world.spawnParticle(Particle.REDSTONE,
                    outputLocation,
                    5, new Particle.DustOptions(Color.GREEN, 1));
        } else {
            // Inputs for other gates: minimum 2 inputs
            GateData data = plugin.getGates().get(gateBlock.getLocation());
            if (data.isThreeInput()) {
                world.spawnParticle(Particle.REDSTONE,
                        thirdInputLocation,
                        5, new Particle.DustOptions(Color.AQUA, 1));
            }
            world.spawnParticle(Particle.REDSTONE,
                    inputLocation,
                    5, new Particle.DustOptions(Color.RED, 1));
            world.spawnParticle(Particle.REDSTONE,
                    secondInputLocation,
                    5, new Particle.DustOptions(Color.BLUE, 1));
        }
    }
}