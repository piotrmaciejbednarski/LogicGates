# Basic Commands

Learn how to use all available plugin commands. Most of them are straightforward.

## /logicgates colors  
Displays all gate types and their corresponding default colors.  
**Note:** The colors shown by this command remain unchanged even if you modify carpet colors in the `gates.yml` under the `carpets` section.

You can customize the command's messages in `messages.yml`.

## /logicgates help  
Shows a list of all plugin commands. Some advanced commands may not appear here.

## /logicgates author  
Displays the author of the LogicGates plugin.

## /logicgates reload
Reload plugin configuration.

## /logicgates save
Manual save plugin and gate's configuration.

## /logicgates fixparticles  
Reloads particle rendering tasks.  
**Troubleshooting Tip:** If particles aren't visible:  

1. Ensure particles are enabled with `/logicgates particles on`  
2. If issues persist, use this command to reset the particle system.

## /logicgates inspect  
Inspect a specific gate's properties:  

1. Run the command  
2. Click the **GLASS block** of a gate  

Displays:  

- Gate type  
- Facing direction  
- Output state  

For advanced diagnostics, use `/logicgates debug`.

## /logicgates rotate  
Rotate a gate's orientation:  

1. Run the command  
2. Click the gate's **GLASS block**  

What it does?

- Rotates clockwise  
- Updates input/output positions  
- Shows message with new facing direction after rotation

## /logicgates timer <time_in_seconds>  
Configure TIMER gate intervals:  

- Default: 1 second  
- Minimum: >0 seconds  

How to?

1. Enter command with desired time  
2. Click the target TIMER gate's GLASS block

## /logicgates particles <on/off>  
Toggle input/output particles:  

`/logicgates particles on` - Enable visual effects  
`/logicgates particles off` - Disable visual effects

## /logicgates cooldown <time_in_milliseconds>  
Set global gate update interval:

- Default: 100ms  
- Affects all gates' refresh rate

## /logicgates redstonecompatibility <on/off>  

This command is a crucial element of the plugin. It allows you to switch between two modes:  

- **Default mode (off):** Redstone logic compatibility is disabled. This means that gate inputs can be powered using non-standard methods that break Redstone logic.  
- **Logic compatibility mode (on):** With Redstone logic compatibility enabled, you cannot power gate inputs indirectly. You must directly connect a Redstone signal to the gate input. Powering a gate using a **REDSTONE_LAMP** or by placing a **Redstone Torch** next to (but not on the gate's wall) is also disabled.  

The recommended mode is **disabling logic compatibility**. This allows for more compact mechanisms but may cause some confusion if you don’t fully understand how it works.

## /logicgates give <gate_type>  
Get gate construction items (configured carpets):  

**Examples:**  

- `/logicgates give RS_LATCH`  
- `/logicgates give IMPLICATION`  
- `/logicgates give AND`  

Configure appearance in `carpets` config section.

## /logicgates debug  
When you enter the command, you personally enable the debugging state. Entering it again exits this state.  

The debugging mode sends a message about the update of each gate's input/output state to all players who have enabled the debugging state.  

The message will only be delivered if the player with debugging enabled is within 16 blocks of the gate that changed its state.  

The debugging message for a gate contains much more information than the gate inspection mode:  

- Gate X, Y, Z coordinates  
- Gate type  
- Gate direction  
- Input state 1  
- Input state 2  
- Output state