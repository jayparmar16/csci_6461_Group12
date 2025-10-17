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

    // Memory
    private int[] memory = new int[2048];

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
}