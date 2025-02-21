package pl.bednarskiwsieci.logicgatesplugin.model;

/**
 * Enumeration representing different types of logic gates.
 */
public enum GateType {
    /**
     * Exclusive OR gate.
     */
    XOR,

    /**
     * AND gate.
     */
    AND,

    /**
     * OR gate.
     */
    OR,

    /**
     * NOT gate (inverter).
     */
    NOT,

    /**
     * NAND gate (NOT AND).
     */
    NAND,

    /**
     * NOR gate (NOT OR).
     */
    NOR,

    /**
     * Exclusive NOR gate.
     */
    XNOR,

    /**
     * Implication gate.
     */
    IMPLICATION,

    /**
     * RS Latch.
     */
    RS_LATCH,

    /**
     * Timer gate.
     */
    TIMER
}
