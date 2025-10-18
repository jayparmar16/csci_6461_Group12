import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

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

    // I/O
    private Consumer<String> consolePrinter;
    private int keyboardBuffer = -1;

    // Memory & Cache
    private int[] memory = new int[2048];
    private final Map<Integer, Integer> cache = new HashMap<>();

    public CPU() {
        reset();
    }

    public void setConsolePrinter(Consumer<String> printer) {
        this.consolePrinter = printer;
    }

    public void setKeyboardBuffer(int value) {
        this.keyboardBuffer = value;
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
        if (mfr != 0) return; // Halt on machine fault

        // Fetch phase
        mar = pc;
        mbr = memoryRead(mar);
        if (mfr != 0) return; // Check for fault during memory read
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
        if (mfr != 0) return; // Check for fault during EA calculation

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
            case 0b000011: // LDA
                gpr[r] = effectiveAddress;
                break;
            case 0b101001: // LDX
                mar = effectiveAddress;
                mbr = memoryRead(mar);
                ixr[ix - 1] = mbr;
                break;
            case 0b101010: // STX
                mar = effectiveAddress;
                mbr = ixr[ix - 1];
                memoryWrite(mar, mbr);
                break;
            case 0b000100: // AMR
                mar = effectiveAddress;
                mbr = memoryRead(mar);
                gpr[r] += mbr;
                break;
            case 0b000101: // SMR
                mar = effectiveAddress;
                mbr = memoryRead(mar);
                gpr[r] -= mbr;
                break;
            case 0b011001: // IN
                if (keyboardBuffer != -1) {
                    gpr[r] = keyboardBuffer;
                    keyboardBuffer = -1;
                } else {
                    // In a real scenario, this would block. Here we just wait.
                    // The GUI will handle enabling input.
                }
                break;
            case 0b011010: // OUT
                if (consolePrinter != null) {
                    consolePrinter.accept(String.valueOf(gpr[r]));
                }
                break;
            case 0b000000: // HLT
                break;
            default:
                mfr = 1; // Set Machine Fault: Illegal Operation Code
                break;
        }
        // Check for overflow and set CC
        if (gpr[r] > 65535 || gpr[r] < -65536) {
            cc |= (1 << 3); // Set overflow bit
        }
    }

    private int getEffectiveAddress(int ix, int address, int i) {
        int ea;
        if (ix == 0) { // No indexing
            ea = address;
        } else { // Indexing
            ea = ixr[ix - 1] + address;
        }

        if (i == 1) { // Indirect addressing
            mar = ea;
            ea = memoryRead(mar); // The content of ea is the new address
        }
        return ea;
    }

    public int memoryRead(int address) {
        if (address < 0 || address >= 2048) {
            mfr = 2; // Illegal Memory Address
            return 0;
        }
        if (cache.containsKey(address)) {
            return cache.get(address);
        }
        int value = memory[address];
        cache.put(address, value);
        return value;
    }

    public void memoryWrite(int address, int value) {
        if (address < 0 || address >= 2048) {
            mfr = 2; // Illegal Memory Address
            return;
        }
        memory[address] = value;
        cache.put(address, value);
    }

    // Getter methods for GUI to display register values
    public int getPC() {
        return pc;
    }

    public int getMAR() {
        return mar;
    }

    public int getMBR() {
        return mbr;
    }

    public int getIR() {
        return ir;
    }

    public int getMFR() {
        return mfr;
    }

    public int getCC() {
        return cc;
    }

    public int getGPR(int index) {
        return gpr[index];
    }

    public int getIXR(int index) {
        return ixr[index];
    }

    public int getMemory(int address) {
        return memory[address];
    }

    // Setter for loading program
    public void setMemory(int address, int value) {
        memory[address] = value;
    }

    public void setPC(int value) {
        this.pc = value;
    }
}