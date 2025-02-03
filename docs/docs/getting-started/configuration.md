# Configuration

## Core Configuration (`gates.yml`)

### Global Settings
```yaml
# Enable/disable visual particles for gate inputs/outputs (default: true)
particlesEnabled: true

# Maximum distance in blocks to render particles (default: 16)
particlesViewDistance: 16

# Enable strict Redstone logic compatibility (default: false)
redstoneCompatibility: false

# Server-wide language for messages (en/pl/de/es) (default: en)
language: en

# Global cooldown between gate updates in milliseconds (default: 100)
cooldownMs: 100
```

**Important Settings:**

**`redstoneCompatibility` when enabled (`true`):**

- Requires direct Redstone connections for inputs
- Disables non-standard powering methods
- Recommended for vanilla-style circuits

**`cooldownMs` key**:  

- Controls update frequency for all gates. Lower values = faster response but higher server load.

### Gate Persistence
```yaml
gates:
  world;123;64;-456:
    facing: NORTH
    type: AND
    state: false
    interval: 1000
    lastToggleTime: 0
  world;150;64;200:
    facing: EAST
    type: TIMER
    state: true
    interval: 3000
    lastToggleTime: 1625000000
```

**Structure Details:**

- Location format: `world;x;y;z` (automatically generated)
- Properties:
  - `facing`: Block direction (NORTH/EAST/SOUTH/WEST)
  - `type`: Gate type (AND/OR/XOR etc.)
  - `state`: Current output state
  - `interval`: Timer duration in ms (TIMER gates only)
  - `lastToggleTime`: Last state change timestamp

### Custom Carpets Configuration
```yaml
carpets:
  AND:
    item:
      material: RED_CARPET
      name: "&6AND Gate"
      lore:
        - "&7Input 1: Left side"
        - "&7Input 2: Right side"
  OR:
    item:
      material: BLUE_CARPET
      name: "&9OR Gate"
      lore:
        - "&7Output activates when"
        - "&7any input is powered"
```

**Customization Options:**

- `material`: Any carpet type (`RED_CARPET`, `BLUE_CARPET`, etc.)
- `name`: Display name with color codes (`&`-prefix)
- `lore`: Description lines (supports multi-line)

## Translation System (`messages.yml`)

### Structure Example
```yaml
en:
  errors:
    no_permission: "&cYou don't have permission!"
  inspect_header: "&6=== Gate Inspection ==="
pl:
  errors:
    no_permission: "&cBrak uprawnień!"
```

**Key Features:**

- Supports multiple languages simultaneously
- Built-in color codes using `&` symbol
- Modular structure for easy expansion

### Adding New Languages
1. Create new section with language code (e.g., `fr:`)
2. Translate all message keys under the section
3. Set default language in `gates.yml` or via `/logicgates language`

**Community Contribution:**  
Users can share custom translations through the plugin's community platforms.

## Advanced Configuration

### Performance Tuning
```yaml
# Recommended values for different server tiers:
# Small servers (20 players):
cooldownMs: 50
particlesViewDistance: 24

# Large servers (100+ players):
cooldownMs: 150
particlesViewDistance: 12
```

## Configuration Management

### Reloading Configuration
1. Edit files directly while server is running
2. Use `/logicgates reload` to write changes

### Best Practices
- Always back up configuration before editing
- Use YAML validators to check syntax
- Test changes in creative world first
- Incrementally adjust performance settings

## Technical Implementation Details

### Configuration Lifecycle
1. Load settings on server start
2. Maintain in-memory cache during runtime
3. Auto-save on graceful shutdown
4. Manual save with `/logicgates save`