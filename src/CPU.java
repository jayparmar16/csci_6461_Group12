import java.util.HashMap;
import java.util.Map;

public class CPU {
    // Registers
    private int[] gpr = new int[4]; // General Purpose Registers (R0-R3)
    private int[] ixr = new int[3]; // Index Registers (X1-X3)
    private int pc; // Program Counter
    private int mar; // Memory Address Register
    private int mbr; // Memory Buffer Register
    private int ir; // Instruction Register
    private int mfr; // Machine Fault Register
    private int cc; // Condition Code

    // Memory & Cache
    private int[] memory = new int[2048];
    // A simple cache placeholder
    private final Map<Integer, Integer> cache = new HashMap<>();


    public CPU() {
        reset();
    }

    /**
     * Resets all registers and memory to their initial state.
     */
    public void reset() {
        for (int i = 0; i < gpr.length; i++) {
            gpr[i] = 0;
        }
        for (int i = 0; i < ixr.length; i++) {
            ixr[i] = 0;
        }
        pc = 0;
        mar = 0;
        mbr = 0;
        ir = 0;
        mfr = 0;
        cc = 0;

        for (int i = 0; i < memory.length; i++) {
            memory[i] = 0;
        }
        cache.clear();
    }

    // Single instruction cycle
    public void instructionCycle() {
        // Fetch phase
        mar = pc;
        mbr = memoryRead(mar);
        ir = mbr;
        pc++;

        // Decode and Execute phase
        decodeAndExecute();
    }

    private void decodeAndExecute() {
        int opcode = (ir >> 10) & 0b111111;
        int r = (ir >> 8) & 0b11;
        int ix = (ir >> 6) & 0b11;
        int i = (ir >> 5) & 0b1;
        int address = ir & 0b11111;

        int effectiveAddress = getEffectiveAddress(ix, address, i);

        switch (opcode) {
            case 0b000001: // LDR
                mar = effectiveAddress;
                mbr = memoryRead(mar);
                gpr[r] = mbr;
                break;
            case 0b000010: // STR
                mar = effectiveAddress;
                mbr = gpr[r];
                memoryWrite(mar, mbr);
                break;
            case 0b000000: // HLT
                // Stop execution (handled by GUI)
                break;
            default:
                // Handle illegal opcode
                mfr = 1; // Set Machine Fault: Illegal Operation Code
                break;
        }
    }

    private int getEffectiveAddress(int ix, int address, int i) {
        if (i == 0) { // No indirect addressing
            if (ix == 0) { // No indexing
                return address;
            } else { // Indexing
                return ixr[ix - 1] + address;
            }
        } else { // Indirect addressing
            // To be implemented in next commit
            return 0;
        }
    }

    public int memoryRead(int address) {
        // Simple memory read, cache logic to be expanded
        if (cache.containsKey(address)) {
            return cache.get(address);
        }
        int value = memory[address];
        cache.put(address, value);
        return value;
    }

    public void memoryWrite(int address, int value) {
        memory[address] = value;
        cache.put(address, value);
    }

    // Getter methods for GUI to display register values
    public int getPC() { return pc; }
    public int getMAR() { return mar; }
    public int getMBR() { return mbr; }
    public int getIR() { return ir; }
    public int getMFR() { return mfr; }
    public int getCC() { return cc; }
    public int getGPR(int index) { return gpr[index]; }
    public int getIXR(int index) { return ixr[index]; }
    public int getMemory(int address) { return memory[address]; }

    // Setter for loading program
    public void setMemory(int address, int value) {
        memory[address] = value;
    }
    public void setPC(int value) { this.pc = value; }
}