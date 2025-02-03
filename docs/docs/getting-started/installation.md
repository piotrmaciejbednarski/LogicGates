# Installation

## Requirements
- Minecraft Server 1.16+
- Java 17+
- Spigot/Paper recommended

## Installation Steps
1. Download latest plugin JAR from [releases page]
2. Place file in your `plugins/` directory
3. Restart server
4. Configure permissions (see [permissions guide](https://piotrmaciejbednarski.github.io/logicgates-docs/commands/permissions/))

## First Run Configuration
Edit `gates.yml`:

```yaml
# Enable or disable particle effects for the logic gates.
particlesEnabled: true

# Set the maximum view distance (in blocks) at which particle effects are visible.
particlesViewDistance: 16

# Enable or disable compatibility with standard redstone logic.
redstoneCompatibility: false

# The language code for messages (e.g., en, pl, es, de).
language: en

# The cooldown in milliseconds between allowed actions/updates.
cooldownMs: 100

# This section defines the configuration for the carpet items used to create logic gates.
# Each key corresponds to a GateType and defines the item settings (material, name, lore).
carpets:
  # Configuration for the XOR gate carpet item.
  XOR:
    item:
      # The material must match one of the supported carpet types.
      material: red_carpet
      # The display name for the item (use color codes with &).
      name: '&cXOR'
      # The lore provides additional information about the item.
      lore:
        - '&7Creates an XOR gate'
        - '&7Exclusive logic function'

  # Configuration for the AND gate carpet item.
  AND:
    item:
      material: blue_carpet
      name: '&9AND'
      lore:
        - '&7Creates an AND gate'
        - '&7Only outputs true if all inputs are true'

  # Configuration for the OR gate carpet item.
  OR:
    item:
      material: green_carpet
      name: '&aOR'
      lore:
        - '&7Creates an OR gate'
        - '&7Outputs true if any input is true'

  # Configuration for the NOT gate carpet item.
  NOT:
    item:
      material: black_carpet
      name: '&0NOT'
      lore:
        - '&7Creates a NOT gate'
        - '&7Inverts the input signal'

  # Configuration for the NAND gate carpet item.
  NAND:
    item:
      material: yellow_carpet
      name: '&eNAND'
      lore:
        - '&7Creates a NAND gate'
        - '&7Outputs false only if all inputs are true'

  # Configuration for the NOR gate carpet item.
  NOR:
    item:
      material: white_carpet
      name: '&fNOR'
      lore:
        - '&7Creates a NOR gate'
        - '&7Outputs true only if all inputs are false'

  # Configuration for the XNOR gate carpet item.
  XNOR:
    item:
      material: cyan_carpet
      name: '&bXNOR'
      lore:
        - '&7Creates an XNOR gate'
        - '&7Outputs true when inputs are equal'

  # Configuration for the Implication gate carpet item.
  IMPLICATION:
    item:
      material: magenta_carpet
      name: '&dIMPLICATION'
      lore:
        - '&7Creates an Implication gate'
        - '&7Implements logical implication'

  # Configuration for the RS Latch gate carpet item.
  RS_LATCH:
    item:
      material: orange_carpet
      name: '&6RS LATCH'
      lore:
        - '&7Creates an RS Latch gate'
        - '&7A basic memory element'

  # Configuration for the Timer gate carpet item.
  TIMER:
    item:
      material: brown_carpet
      name: '&8TIMER'
      lore:
        - '&7Creates a Timer gate'
        - '&7Generates signal every 1 second'
```