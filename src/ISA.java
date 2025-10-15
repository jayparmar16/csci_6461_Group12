import java.util.HashMap;
import java.util.Map;

/**
 * Defines the Instruction Set Architecture (ISA) for the C6461 computer.
 * This class holds all the official opcodes as specified in the ISA document.
 */
public final class ISA {
    private static final Map<String, Integer> OPCODES = new HashMap<>();

    static {
        OPCODES.put("HLT", 0b000000); OPCODES.put("TRAP", 0b011000);
        OPCODES.put("LDR", 0b000001); OPCODES.put("STR", 0b000010);
        OPCODES.put("LDA", 0b000011); OPCODES.put("LDX", 0b100001);
        OPCODES.put("STX", 0b100010); OPCODES.put("JZ",  0b001000);
        OPCODES.put("JNE", 0b001001); OPCODES.put("JCC", 0b001010);
        OPCODES.put("JMA", 0b001011); OPCODES.put("JSR", 0b001100);
        OPCODES.put("RFS", 0b001101); OPCODES.put("SOB", 0b001110);
        OPCODES.put("JGE", 0b001111); OPCODES.put("AMR", 0b000100);
        OPCODES.put("SMR", 0b000101); OPCODES.put("AIR", 0b000110);
        OPCODES.put("SIR", 0b000111); OPCODES.put("MLT", 0b011100);
        OPCODES.put("DVD", 0b011101); OPCODES.put("TRR", 0b011110);
        OPCODES.put("AND", 0b011111); OPCODES.put("ORR", 0b100000);
        OPCODES.put("NOT", 0b100001); OPCODES.put("SRC", 0b011001);
        OPCODES.put("RRC", 0b011010); OPCODES.put("IN",  0b110001);
        OPCODES.put("OUT", 0b110010); OPCODES.put("CHK", 0b110011);
    }

    private ISA() {} // Prevents instantiation

    public static Integer getOpcode(String mnemonic) {
        return OPCODES.get(mnemonic.toUpperCase());
    }
    
    public static boolean isValidOpcode(int opcode) {
        return OPCODES.containsValue(opcode);
    }
}