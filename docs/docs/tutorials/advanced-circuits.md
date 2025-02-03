# Advanced circuits

## Half adder

Using the plugin, you can create more advanced logic circuits. Below is an example of a half-adder circuit built with an AND gate and an XOR gate.

- The orange block symbolizes the output 'S' (sum).  
- The yellow block symbolizes the output 'C' (carry).  
- The purple block symbolizes the input 'A'.  
- The blue block symbolizes the input 'B'.

![Half adder](https://upload.wikimedia.org/wikipedia/commons/d/d9/Half_Adder.svg)

![Half adder in LogicGates](https://piotrmaciejbednarski.github.io/logicgates-docs/assets/half-adder.gif)

The above circuit is built in the mode with redstone compatibility disabled (by default).

## Simple 5-bit memory with full memory reset

A simple 5-bit memory built using SR Latch. It allows writing to selected memory cells and resetting the entire memory with a single signal.  

The gates are oriented in the same direction. On the right side of each gate (viewed from the **REDSTONE_LAMP** blocks) are the gate resets (blue particles).  

On the left side (write input section), there are write signals to the gates (red particles).  

At the output of each gate, an **observer** is placed, which updates its state whenever the gate's state changes.  

The observer transmits a Redstone signal downward to a single **Redstone dust**, which is placed on a block that becomes powered.  

From this powered block, on the right side, a **repeater** transmits the signal to the memory cell output (**REDSTONE_LAMP**).  

![type:video](https://piotrmaciejbednarski.github.io/logicgates-docs/assets/simple5bitmemory.mp4)

## SR Flip Flop

With the help of two NAND gates and manual NOT gates, an SR Flip-Flop (also known as an SR Latch) has been constructed. It is a sequential logic circuit with two output states used for storing binary information. It can be built using either NAND gates or NOR gates.

![type:video](https://piotrmaciejbednarski.github.io/logicgates-docs/assets/srflipflop.mp4)
