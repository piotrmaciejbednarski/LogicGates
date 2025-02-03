# FAQ

Below is a list of frequently asked questions (FAQ) regarding the operation of the LogicGates plugin.

## Why do I have to connect "expanders" to the output instead of connecting redstone directly?

The Bukkit/Spigot/PaperMC engines are designed to update redstone, comparator, and repeater changes every tick. This means that if the plugin attempts to override the state of an object, it will be changed again on the next update. Therefore, using a repeater on the output of a gate can lead to the creation of a "clock." However, if you use redstone, it only works for one block, and extending the circuit causes the signal to be lost. The solution is to use safe output expanders for the gates, such as buttons, levers, or observers—these retain their state and are not updated by the server every tick. This helps ensure safety and prevents bugs in your mechanisms.

## Does the plugin affect performance?

No. The plugin generally does not affect performance, although if the number of logic gates increases significantly, it may cause performance issues. A benchmark was conducted on a 5-bit memory built with an RS Latch, available on the [Advanced Circuits](tutorials/advanced-circuits.md) page. The benchmark results are available on the [Benchmark](benchmark.md) subpage. If you want to maintain performance, you can configure options such as particle display in `gates.yml` and the rendering distance.

## Can I build a CPU with this?

Absolutely! To build a simple 4-bit processor in the von Neumann architecture, you need several components:

- **Arithmetic Logic Unit (ALU)** – performs arithmetic operations (addition, subtraction) and logical operations (AND, OR, XOR, NOT).
- **Registers** – store temporary data and calculation results.
- **RAM** – holds the program and data (usually a few bytes for simple CPUs).
- **Instruction Decoder** – decodes instructions and generates control signals.
- **Control Unit (CU)** – manages the data flow and the sequence of instruction execution.

I estimate that such a processor could be built with around 300-400 logic gates.

## I’m having problems with the plugin and it’s not my fault

Errors do happen; the plugin is not completely stable, so bugs may occur. You can influence the behavior of the plugin by configuring update delays using `/logicgates cooldown <ms>` (default is 100ms). A value that is too high can completely break the logic gate circuits and cause synchronization issues, so setting values above 1000ms is pointless. I strive to improve the plugin and fix bugs — some issues you can solve with various tricks if you know how to think creatively. You can also suggest changes on GitHub and report problems through the Issues page on GitHub.

## I’m having problems with the gate output

Please review the redstone mechanics in [Redstone mechanics](features/redstone-mechanics.md) and check out the Output section. Verify that you are using the recommended output expander for the gate. In the case of gates with inverted logic (e.g., NOR, NOT, NAND, XNOR), issues may arise, and you will then be able to use only expanders in the form of levers, buttons, and observers.

## Can I set the TIMER time in milliseconds?

Although you cannot set the TIMER gate time in milliseconds via a command (only seconds are allowed), you can set the time in milliseconds by editing Gate Data in `gates.yml`, and then refreshing the config with `/logicgates reload`.