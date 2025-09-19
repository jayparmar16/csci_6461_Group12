import java.util.HashMap;

public final class ISA {

    // A map to store the instruction mnemonic and its integer opcode value.
    private static final HashMap<String, Integer> OPCODES = new HashMap<>();

    static {
        OPCODES.put("HLT", octal("00")); // Halts program execution.
        OPCODES.put("TRAP", octal("30")); // Executes a system trap routine.
        OPCODES.put("LDR", octal("01")); // Loads a general-purpose register from memory.
        OPCODES.put("STR", octal("02")); // Stores a general-purpose register to memory.
        OPCODES.put("LDA", octal("03")); // Loads a register with a memory address.
        OPCODES.put("LDX", octal("41")); // Loads an index register from memory.
        OPCODES.put("STX", octal("42")); // Stores an index register to memory.
        OPCODES.put("JZ", octal("10"));  // Jumps to an address if a register's value is zero.
        OPCODES.put("JNE", octal("11")); // Jumps if a register's value is not zero.
        OPCODES.put("JCC", octal("12")); // Jumps if a specific condition code bit is set.
        OPCODES.put("JMA", octal("13")); // Jumps unconditionally to an address.
        OPCODES.put("JSR", octal("14")); // Jumps to a subroutine, saving the return address.
        OPCODES.put("RFS", octal("15")); // Returns from a subroutine.
        OPCODES.put("SOB", octal("16")); // Decrements a register and branches if the result is not zero.
        OPCODES.put("JGE", octal("17")); // Jumps if a register's value is greater than or equal to zero.
        OPCODES.put("AMR", octal("04")); // Adds a value from memory to a register.
        OPCODES.put("SMR", octal("05")); // Subtracts a value from memory from a register.
        OPCODES.put("AIR", octal("06")); // Adds an immediate value to a register.
        OPCODES.put("SIR", octal("07")); // Subtracts an immediate value from a register.
        OPCODES.put("MLT", octal("70")); // Multiplies two registers.
        OPCODES.put("DVD", octal("71")); // Divides one register by another.
        OPCODES.put("TRR", octal("72")); // Tests for equality between two registers.
        OPCODES.put("AND", octal("73")); // Performs a bitwise AND operation on two registers.
        OPCODES.put("ORR", octal("74")); // Performs a bitwise OR operation on two registers.
        OPCODES.put("NOT", octal("75")); // Performs a bitwise NOT on a single register.
        OPCODES.put("SRC", octal("31")); // Shifts the bits in a register.
        OPCODES.put("RRC", octal("32")); // Rotates the bits in a register.
        OPCODES.put("IN", octal("61"));  // Inputs a character from a device into a register.
        OPCODES.put("OUT", octal("62")); // Outputs a character from a register to a device.
        OPCODES.put("CHK", octal("63")); // Checks the status of a device.
    }

    private ISA() {}

    /**
     * helper method to convert an octal string into its integer equivalent.
     * @param octalStr The string representing an octal number.
     * @return The integer value of the octal string.
     */
    private static int octal(String octalStr) {
        return Integer.parseInt(octalStr, 8);
    }

    /**
     * Retrieves the integer opcode for a given instruction mnemonic
     * @param mnemonic The instruction's text representation
     * @return The integer value of the opcode, null if the mnemonic is not found
     */
    public static Integer getOpcode(String mnemonic) {
        return OPCODES.get(mnemonic.toUpperCase());
    }

    /**
     * Checks if a given mnemonic corresponds to a valid instruction in the ISA
     * @param mnemonic The instruction's text representation
     * @return True if the instruction is valid, otherwise false.
     */
    public static boolean isInstruction(String mnemonic) {
        return OPCODES.containsKey(mnemonic.toUpperCase());
    }
}