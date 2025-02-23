<div align="center">
  <img src="https://raw.githubusercontent.com/piotrmaciejbednarski/piotrmaciejbednarski/refs/heads/main/header_dark.png#gh-dark-mode-only" width="640" alt="LogicGates Logo"/>
  <img src="https://raw.githubusercontent.com/piotrmaciejbednarski/piotrmaciejbednarski/refs/heads/main/header.png#gh-light-mode-only" width="640" alt="LogicGates Logo"/>
</div>

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![GitHub Release](https://img.shields.io/github/v/release/piotrmaciejbednarski/LogicGates)

This Spigot plugin provides a compact and efficient way to build logic gates directly within your Minecraft world.

## Features

- **Extensive Gate Selection**: Build one of the 7 basic logic gates (NOT, OR, AND, XOR, NOR, NAND, XNOR) and additional gates such as implication, TIMER (oscillator), or RS-Latch (memory gate).
- **Intuitive Interface**: Choose gates using a graphical interface (GUI).
- **High Performance**: The plugin runs efficiently thanks to a system of synchronization with server ticks, task queuing, batch processing, and many optimization techniques.
- **Lightweight and Efficient**: The plugin's compact size (under 60kB) contributes to its efficient performance and low resource usage.
- **Ready to Use**: After installation, the plugin is immediately ready to use, no configuration is required.
- **Easy Internationalization**: Add your own plugin translations by editing the messages.yml file.
- **JSON Saving**: Gates are saved in JSON format, which makes them easy to process by external software.
- **Unlimited Possibilities**: Develop and create even more advanced Redstone mechanisms.
- **WorldEdit Integration**: The plugin integrates with the WorldEdit API, allowing you to copy and paste gates.

🔗 **Online documentation**: [https://logicgates.bednarskiwsieci.pl](https://logicgates.bednarskiwsieci.pl/)

## Installation

1. Download the latest `LogicGates.jar` from [Releases](https://github.com/piotrmaciejbednarski/logicgates/releases)
2. Place the JAR file in your server's `plugins/` directory
3. Restart your Minecraft server

## How it works?

The core element of every logic gate is the `minecraft:glass` (Glass) block, which serves as the main component. This is where the input and output signals are connected.

Each gate performs a specific logical operation based on the redstone signals provided at its inputs. The inputs are marked with **particle colors** to help with identification and proper connection:

- **Red** – first input
- **Blue** – second input
- **Light Blue** – third input (for gates with three input signals)

The gate's output is marked with a **green color** and transmits the result of the logical operation to other components in the circuit using redstone signals.

<img src="https://logicgates.bednarskiwsieci.pl/_next/image?url=%2F_next%2Fstatic%2Fmedia%2Fandgate.4c0dfe77.png&w=640&q=75" width="640" alt="AND gate"/>

## Contributing

We welcome contributions! Please follow these steps:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

Report issues [here](https://github.com/piotrmaciejbednarski/logicgates/issues)

## Links

- [Spigotmc.org](https://www.spigotmc.org/resources/logicgates-1-16-1-21-redstone-logic-gates-in-one-block.122253/)
- [Curseforge.com](https://legacy.curseforge.com/minecraft/bukkit-plugins/logicgates)
- [Modrinth](https://modrinth.com/plugin/logicgates)
- [Hangar](https://hangar.papermc.io/piotrmaciejbednarski/LogicGates)

## License

Distributed under MIT License. See [LICENSE](https://mit-license.org/) for details.

<img src="https://bstats.org/signatures/bukkit/LogicGates.svg" alt="BStats"/>
