import java.util.HashMap;

public final class ISA {
    private static final HashMap<String, Integer> OPCODES = new HashMap<>();

    static {
        OPCODES.put("HLT", 0);
        OPCODES.put("LDR", 1);
        OPCODES.put("STR", 2);
    }

    private ISA() {
    }

    /**
     * Retrieves the integer opcode for a given instruction mnemonic
     * 
     * @param mnemonic The instruction's text representation
     * @return The integer value of the opcode, null if the mnemonic is not found
     */
    public static Integer getOpcode(String mnemonic) {
        return OPCODES.get(mnemonic.toUpperCase());
    }

    /**
     * Checks if a given mnemonic corresponds to a valid instruction in the ISA
     * 
     * @param mnemonic The instruction's text representation
     * @return True if the instruction is valid, otherwise false.
     */
    public static boolean isInstruction(String mnemonic) {
        return OPCODES.containsKey(mnemonic.toUpperCase());
    }
}