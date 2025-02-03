# Permissions

## Administrative Permissions (`logicgates.admin`)
Required for managing plugin settings and system functions.  

| Command                                  | Description                                                         | Player Required? |
|------------------------------------------|---------------------------------------------------------------------|-----------------|
| `/logicgates debug`                      | Enables/disables debugging mode                                     | ✅ Yes          |
| `/logicgates save`                       | Saves all gates to the `gates.yml` file                            | ❌ No           |
| `/logicgates reload`                       | Reload plugin configuration                            | ❌ No           |
| `/logicgates redstonecompatibility <on/off>` | Toggles Redstone logic compatibility                       | ❌ No           |
| `/logicgates fixparticles`               | Fixes the particle system                                          | ❌ No           |
| `/logicgates particles <on/off>`         | Enables/disables input/output particles                            | ❌ No           |
| `/logicgates cooldown <time_in_ms>`      | Sets the global gate refresh time                                  | ❌ No           |
| `/logicgates language <language_code>`           | Changes the plugin's message language                             | ❌ No           |

---

## Gate Interaction Permissions
Required for physical interactions with gates in the world.

| Permission Node         | Description                                                                 | Requires Player? | Associated Game Actions |
|-------------------------|-----------------------------------------------------------------------------|------------------|-------------------------|
| `logicgates.place`      | Allows placing logic gates (carpet on glass)                               | ✅ Yes           | Block placement         |
| `logicgates.break`      | Allows breaking logic gates (glass base or carpet)                         | ✅ Yes           | Block breaking          |
| `logicgates.rotate`     | Allows rotating gates using `/logicgates rotate`                           | ✅ Yes           | Right-click interaction |
| `logicgates.inspect`    | Allows using gate inspection mode (`/logicgates inspect`)                  | ✅ Yes           | Right-click interaction |


## Player Permissions
Available to players with the appropriate permissions.  

| Permission                 | Command                                  | Description                                                         | Player Required? |
|----------------------------|-----------------------------------------|---------------------------------------------------------------------|-----------------|
| `logicgates.give`          | `/logicgates give <gate_type>`         | Gives an item for creating a gate                                  | ✅ Yes          |
| `logicgates.inspect`       | `/logicgates inspect`                   | Enables gate inspection mode                                       | ✅ Yes          |
| `logicgates.rotate`        | `/logicgates rotate`                    | Enables gate rotation mode                                         | ✅ Yes          |
| `logicgates.timer`         | `/logicgates timer <time_in_seconds>`  | Sets the TIMER duration for a selected gate                        | ✅ Yes          |

---

## Public Commands
Available to all players **without additional permissions**.  

| Command                   | Description                                  |
|---------------------------|----------------------------------------------|
| `/logicgates colors`      | Displays gate color schemes                 |
| `/logicgates help`        | Shows the list of available commands        |
| `/logicgates author`      | Displays information about the plugin author |

---

## Notes 
1. **Administrative permissions** include all sensitive system functions.  
2. Commands marked ✅ **Player Required** can only be used in-game (not via the console).  
3. By default, new players do not have access to the commands in the "Player Permissions" section – they must be granted the appropriate permissions (e.g., via LuckPerms).  
4. To grant all permissions to server administrators, use:  
   ```permissions
   logicgates.*
   ```