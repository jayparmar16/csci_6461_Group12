import javax.swing.*;
import java.io.*;

/**
 * Simulates the Central Processing Unit (CPU) of the C6461 machine.
 * It holds registers, memory, and the logic for the fetch-decode-execute cycle.
 */
public class CPU {

    private final SimulatorGUI gui;
    private final Utils utils = new Utils();
    private SwingWorker<Void, Void> runWorker;
    private boolean isHalted = true;

    // Registers
    private short[] gpr = new short[4];
    private short[] ixr = new short[4]; // Index 0 is unused
    private short pc, mar, mbr, ir, mfr, cc;

    // Memory
    private final short[] memory = new short[2048];

    // Fault Codes
    private static final short FAULT_ILLEGAL_MEM_ADDR = 1; // 0001
    private static final short FAULT_RESERVED_LOCATION = 2; // 0010
    private static final short FAULT_ILLEGAL_OPCODE = 4; // 0100
    private static final short FAULT_ILLEGAL_OPERATION = 8; // 1000
    
    // Reserved memory locations (0-5)
    private static final int RESERVED_MEM_START = 0;
    private static final int RESERVED_MEM_END = 5;

    public CPU(SimulatorGUI gui) {
        this.gui = gui;
    }

    public void ipl(File programFile) {
        System.out.println("\n=== Initializing Machine (IPL) ===");
        resetMachine();
        if (programFile != null) {
            loadProgram(programFile);
        } else {
            System.out.println("No program file selected for IPL.");
        }
        System.out.println("\nMachine ready. PC may be set to start address if a program was loaded.");
        printRegisterState();
        gui.updateAllDisplays();
    }
    
    public void resetMachine() {
        halt();
        System.out.println("Clearing all registers and memory...");
        // Clear memory and registers
        for (int i = 0; i < memory.length; i++) memory[i] = 0;
        for (int i = 0; i < 4; i++) gpr[i] = 0;
        for (int i = 0; i < 4; i++) ixr[i] = 0;
        pc = mar = mbr = ir = mfr = cc = 0;
        isHalted = false;
    }
    
    public void loadProgram(File programFile) {
        // New add
        short firstAddress = -1;
        try (BufferedReader reader = new BufferedReader(new FileReader(programFile))) {
            System.out.println("\nLoading program from " + programFile.getName());
            String line;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    String[] parts = line.split("\\s+");
                    if (parts.length == 2) {
                        int address = Integer.parseInt(parts[0], 8);
                        short value = (short) Integer.parseInt(parts[1], 8);
                        
                        if (address >= 0 && address < memory.length) {
                            memory[address] = value;
                            System.out.printf("Loaded memory[%04o] = %06o\n", address, value);
                            // New add
                            if (firstAddress == -1) {
                                firstAddress = (short) address;
                            }
                        } else {
                            throw new IOException("Memory address " + String.format("%04o", address) + " is out of bounds.");
                        }
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Warning: Skipping invalid line: " + line);
                }
            }
            
            // // Set PC to the conventional starting address for this program (010 octal)
            // pc = 010; // Octal 10 is decimal 8.
            // isHalted = false;
            // System.out.printf("\nProgram loaded. PC set to start address %04o\n", pc);

            // *** MODIFIED PC SETTING START ***
            if (firstAddress != -1) {
                pc = firstAddress; // Set PC to the address of the very first instruction loaded
                isHalted = false;
                System.out.printf("\nProgram loaded. PC set to start address %04o\n", pc);
            } else {
                // Handle case of an empty load file
                System.out.println("Program loaded, but file was empty. PC remains 0.");
            }
            // *** MODIFIED PC SETTING END ***
            
            System.out.println("Program loading complete.");
            gui.updateAllDisplays();
        } catch (IOException e) {
            System.err.println("Error loading program: " + e.getMessage());
            gui.showError("Load Error", "Failed to load program: " + e.getMessage());
            resetMachine(); // Reset on error
        }
    }
    
    private void printRegisterState() {
        System.out.println("\n=== Current Register State ===");
        System.out.printf("PC:  %04o    MAR: %04o    MBR: %06o\n", pc, mar, mbr);
        System.out.printf("IR:  %06o    CC:  %04o    MFR: %04o\n", ir, cc, mfr);
        System.out.println("\nGeneral Purpose Registers:");
        for (int i = 0; i < 4; i++) {
            System.out.printf("GPR%d: %06o   ", i, gpr[i]);
            if (i % 2 == 1) System.out.println();
        }
        System.out.println("\nIndex Registers:");
        for (int i = 1; i < 4; i++) {
            System.out.printf("IXR%d: %06o   ", i, ixr[i]);
        }
        System.out.println("\n");
    }

    private void updateDisplayAndLog() {
        String status = String.format("\nRegisters after execution:\n" +
                                    "PC: %06o  MAR: %06o  MBR: %06o  IR: %06o\n" +
                                    "GPRs: %06o  %06o  %06o  %06o\n" +
                                    "IXRs: %06o  %06o  %06o\n",
                                    pc, mar, mbr, ir,
                                    gpr[0], gpr[1], gpr[2], gpr[3],
                                    ixr[1], ixr[2], ixr[3]);
        gui.appendToPrinter(status);
        gui.updateAllDisplays();
    }

    public void load(short address) {
        mar = address;
        if (checkMemoryFault(mar)) return;
        mbr = memory[mar];
        gui.updateAllDisplays();
    }

    public void loadPlus(short address) {
        load(address);
        mar++;
        gui.updateAllDisplays();
    }

    public void store(short address) {
        mar = address;
        if (checkMemoryFault(mar)) return;
        memory[mar] = mbr;
        gui.updateAllDisplays();
    }

    public void storePlus(short value) {
        store(value);
        mar++;
        gui.updateAllDisplays();
    }

    private void executeInstruction() throws Exception {
        // 1. Fetch instruction using current PC
        mar = pc;
        if (checkMemoryFault(mar)) return;
        mbr = memory[mar];
        ir = mbr;
        
        // 2. Increment PC for next instruction
        pc++;
        
        // Decode instruction fields
        int opcode = (ir >>> 10) & 0x3F; // Bits 15-10
        int r      = (ir >>> 8)  & 0x3;  // Bits 9-8
        int ix     = (ir >>> 6)  & 0x3;  // Bits 7-6
        int i      = (ir >>> 5)  & 0x1;  // Bit 5
        int address = ir & 0x1F;         // Bits 4-0
        
        // Log instruction details
        gui.appendToPrinter("=== Instruction Execution ===");
        gui.appendToPrinter(String.format("Location: %06o  Instruction: %06o", mar, ir));
        gui.appendToPrinter("Decoded fields (octal):");
        gui.appendToPrinter(String.format("  Opcode: %02o", opcode));
        gui.appendToPrinter(String.format("  R: %01o", r));
        gui.appendToPrinter(String.format("  IX: %01o", ix));
        gui.appendToPrinter(String.format("  I: %o", i));
        gui.appendToPrinter(String.format("  Address: %o", address));
        
        // 4. Execute based on opcode
        // Effective Address Calculation
        int effectiveAddr = address;
        if (ix > 0 && ix < 4) {
            effectiveAddr += ixr[ix];
            gui.appendToPrinter(String.format("Using IX%d: %o + %o = %o",
                              ix, address, ixr[ix], effectiveAddr));
        }
        if (i == 1) { // Indirect Addressing
            // The address field points to the location of the effective address
            if (!checkMemoryFault((short)effectiveAddr)) {
                effectiveAddr = memory[effectiveAddr];
                gui.appendToPrinter("Indirect addressing used. New EA: " + String.format("%06o", effectiveAddr));
            } else {
                return; // Fault occurred
            }
        }
        System.out.printf("Effective Address: %06o\n", effectiveAddr);

        // Log the raw instruction value for debugging
        gui.appendToPrinter(String.format("Raw instruction value: %06o", ir));
        gui.appendToPrinter(String.format("Decoded opcode: %02o", opcode));

        switch(opcode) {
            case 0: // HLT
                gui.appendToPrinter("HLT - Halting machine");
                halt();
                gui.appendToPrinter("Machine halted - Press IPL to reset");
                break;
                
            case 1: // LDR
                if (!checkMemoryFault((short)effectiveAddr)) {
                    mar = (short)effectiveAddr;
                    mbr = memory[effectiveAddr];
                    gpr[r] = mbr;
                    System.out.printf("LDR - Loaded GPR%d = %06o from memory[%06o]\n", 
                                    r, gpr[r], effectiveAddr);
                }
                break;
                
            case 2: // STR
                if (!checkMemoryFault((short)effectiveAddr)) {
                    mar = (short)effectiveAddr;
                    mbr = gpr[r];
                    memory[effectiveAddr] = mbr;
                    System.out.printf("STR - Stored GPR%d (%06o) to memory[%06o]\n", 
                                    r, mbr, effectiveAddr);
                }
                break;
                
            case 3: // LDA
                if (!checkMemoryFault((short)effectiveAddr)) {
                    mar = (short)effectiveAddr;
                    gpr[r] = (short)effectiveAddr;
                    System.out.printf("LDA - Loaded address %06o into GPR%d\n", 
                                    effectiveAddr, r);
                }
                break;

            case 4: // AMR - Add Memory to Register
                if (!checkMemoryFault((short)effectiveAddr)) {
                    mar = (short)effectiveAddr;
                    mbr = memory[effectiveAddr];
                    gpr[r] = (short)(gpr[r] + mbr);
                    System.out.printf("AMR - Added memory[%06o](%06o) to GPR%d = %06o\n", 
                                    effectiveAddr, mbr, r, gpr[r]);
                }
                break;

            case 5: // SMR - Subtract Memory from Register
                if (!checkMemoryFault((short)effectiveAddr)) {
                    mar = (short)effectiveAddr;
                    mbr = memory[effectiveAddr];
                    gpr[r] = (short)(gpr[r] - mbr);
                    System.out.printf("SMR - Subtracted memory[%06o](%06o) from GPR%d = %06o\n", 
                                    effectiveAddr, mbr, r, gpr[r]);
                }
                break;

            case 6: // AIR - Add Immediate to Register
                gpr[r] = (short)(gpr[r] + address);
                System.out.printf("AIR - Added immediate %o to GPR%d = %06o\n", 
                                address, r, gpr[r]);
                break;

            case 7: // SIR - Subtract Immediate from Register
                gpr[r] = (short)(gpr[r] - address);
                System.out.printf("SIR - Subtracted immediate %o from GPR%d = %06o\n", 
                                address, r, gpr[r]);
                break;

            case 8: // JZ - Jump if Zero
                if (gpr[r] == 0) {
                    pc = (short)effectiveAddr;
                    System.out.printf("JZ - Jumping to %06o (GPR%d is zero)\n", 
                                    effectiveAddr, r);
                } else {
                    System.out.printf("JZ - Not jumping (GPR%d is not zero)\n", r);
                }
                break;

            case 9: // JNE - Jump if Not Equal (to zero)
                if (gpr[r] != 0) {
                    pc = (short)effectiveAddr;
                    System.out.printf("JNE - Jumping to %06o (GPR%d is not zero)\n", 
                                    effectiveAddr, r);
                } else {
                    System.out.printf("JNE - Not jumping (GPR%d is zero)\n", r);
                }
                break;

            case 10: // JCC - Jump if Condition Code
                if (cc == r) {
                    pc = (short)effectiveAddr;
                    System.out.printf("JCC - Jumping to %06o (CC == %d)\n", 
                                    effectiveAddr, r);
                } else {
                    System.out.printf("JCC - Not jumping (CC != %d)\n", r);
                }
                break;

            case 11: // JMA - Unconditional Jump
                pc = (short)effectiveAddr;
                System.out.printf("JMA - Jumping to %06o\n", effectiveAddr);
                break;

            case 12: // JSR - Jump and Save Return
                gpr[3] = pc; // Save return address in R3
                pc = (short)effectiveAddr;
                System.out.printf("JSR - Saved PC (%06o) to R3, jumping to %06o\n", 
                                gpr[3], effectiveAddr);
                break;

            case 13: // RFS - Return From Subroutine
                pc = gpr[3]; // Return to saved address
                gpr[0] = (short)address; // Load R0 with data
                System.out.printf("RFS - Returning to %06o, R0 = %06o\n", 
                                pc, gpr[0]);
                break;

            case 14: // SOB - Subtract One and Branch
                gpr[r]--; // Decrement register
                if (gpr[r] > 0) {
                    pc = (short)effectiveAddr;
                    System.out.printf("SOB - Decremented GPR%d to %06o, branching to %06o\n", 
                                    r, gpr[r], effectiveAddr);
                } else {
                    System.out.printf("SOB - Decremented GPR%d to %06o, not branching\n", 
                                    r, gpr[r]);
                }
                break;

            case 15: // JGE - Jump Greater Than or Equal
                if (gpr[r] >= 0) {
                    pc = (short)effectiveAddr;
                    System.out.printf("JGE - Jumping to %06o (GPR%d >= 0)\n", 
                                    effectiveAddr, r);
                } else {
                    System.out.printf("JGE - Not jumping (GPR%d < 0)\n", r);
                }
                break;

            case 33: // LDX - Load Index Register from Memory
                if (!checkMemoryFault((short)effectiveAddr)) {
                    mar = (short)effectiveAddr;
                    mbr = memory[effectiveAddr];
                    ixr[r] = mbr;
                    System.out.printf("LDX - Loaded IXR%d = %06o from memory[%06o]\n", 
                                    r, ixr[r], effectiveAddr);
                }
                break;

            case 34: // STX - Store Index Register to Memory
                if (!checkMemoryFault((short)effectiveAddr)) {
                    mar = (short)effectiveAddr;
                    mbr = ixr[r];
                    memory[effectiveAddr] = mbr;
                    System.out.printf("STX - Stored IXR%d (%06o) to memory[%06o]\n", 
                                    r, mbr, effectiveAddr);
                }
                break;
                
            case 28: // MLT - Multiply Register by Register
                if (r % 2 != 0) {
                    throw new Exception("MLT requires an even register number for R");
                }
                int product = gpr[r] * gpr[ix];
                gpr[r] = (short)(product >> 16); // High order word
                gpr[r + 1] = (short)(product & 0xFFFF); // Low order word
                System.out.printf("MLT - GPR%d * GPR%d = %d (High: %06o, Low: %06o)\n", 
                                r, ix, product, gpr[r], gpr[r + 1]);
                break;

            case 29: // DVD - Divide Register by Register
                if (gpr[ix] == 0) {
                    throw new Exception("Division by zero");
                }
                if (r % 2 != 0) {
                    throw new Exception("DVD requires an even register number for R");
                }
                gpr[r] = (short)(gpr[r] / gpr[ix]); // Quotient
                gpr[r + 1] = (short)(gpr[r] % gpr[ix]); // Remainder
                System.out.printf("DVD - GPR%d / GPR%d = Q:%06o R:%06o\n", 
                                r, ix, gpr[r], gpr[r + 1]);
                break;

            case 30: // TRR - Test the Equality of Register and Register
                cc = (short)((gpr[r] == gpr[ix]) ? 1 : 0);
                System.out.printf("TRR - Testing GPR%d == GPR%d: %s\n", 
                                r, ix, (cc == 1 ? "Equal" : "Not Equal"));
                break;

            case 31: // AND - Logical AND of Register and Register
                gpr[r] = (short)(gpr[r] & gpr[ix]);
                System.out.printf("AND - GPR%d & GPR%d = %06o\n", 
                                r, ix, gpr[r]);
                break;

            case 32: // ORR - Logical OR of Register and Register
                gpr[r] = (short)(gpr[r] | gpr[ix]);
                System.out.printf("ORR - GPR%d | GPR%d = %06o\n", 
                                r, ix, gpr[r]);
                break;

            case 25: // SRC - Shift Register by Count
                int count = address & 0xF;       // Get count from bits 3-0
                boolean left = (address & 0x10) != 0;  // Get direction from bit 4
                boolean logical = (i == 0);      // Get shift type from I bit
                
                if (left) {
                    if (logical) {
                        gpr[r] = (short)(gpr[r] << count);
                    } else {
                        gpr[r] = (short)(gpr[r] << count);
                    }
                    System.out.printf("SRC - Shifted GPR%d left by %d (%s) = %06o\n", 
                                    r, count, logical ? "logical" : "arithmetic", gpr[r]);
                } else {
                    if (logical) {
                        gpr[r] = (short)((gpr[r] & 0xFFFF) >>> count);
                    } else {
                        gpr[r] = (short)(gpr[r] >> count);
                    }
                    System.out.printf("SRC - Shifted GPR%d right by %d (%s) = %06o\n", 
                                    r, count, logical ? "logical" : "arithmetic", gpr[r]);
                }
                break;

            case 26: // RRC - Rotate Register by Count
                count = address & 0xF;       // Get count from bits 3-0
                left = (address & 0x10) != 0;  // Get direction from bit 4
                short val = gpr[r];
                
                if (left) {
                    gpr[r] = (short)((val << count) | ((val & 0xFFFF) >>> (16 - count)));
                    System.out.printf("RRC - Rotated GPR%d left by %d = %06o\n", 
                                    r, count, gpr[r]);
                } else {
                    gpr[r] = (short)(((val & 0xFFFF) >>> count) | (val << (16 - count)));
                    System.out.printf("RRC - Rotated GPR%d right by %d = %06o\n", 
                                    r, count, gpr[r]);
                }
                break;

            case 24: // TRAP
                throw new Exception("TRAP instruction not yet implemented");

            case 49: // IN - Input from Device
                // r contains device ID
                // effectiveAddr contains the word count
                throw new Exception("IN instruction not yet implemented");

            case 50: // OUT - Output to Device
                // r contains device ID
                // effectiveAddr contains the word count
                throw new Exception("OUT instruction not yet implemented");

            case 51: // CHK - Check Device Status
                // r contains device ID
                // effectiveAddr is ignored
                throw new Exception("CHK instruction not yet implemented");

            default:
                throw new Exception(String.format("Unimplemented instruction - Opcode: %o", opcode));
        }
        
        // After every instruction, update display and log state
        gui.updateAllDisplays();
        updateDisplayAndLog();
    }

    public void runProgram() {
        if (!isHalted && (runWorker == null || runWorker.isDone())) {
            runWorker = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    while (!isHalted && !isCancelled()) {
                        singleStep();
                        try {
                            // Add delay for visibility of execution steps
                            Thread.sleep(100);
                            // Update GUI on EDT
                            SwingUtilities.invokeLater(() -> gui.updateAllDisplays());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    return null;
                }
                
                @Override
                protected void done() {
                    if (isHalted) {
                        System.out.println("Program execution completed.");
                    }
                }
            };
            runWorker.execute();
        }
    }

    public void singleStep() {
        if (isHalted) {
            gui.appendToPrinter("Machine is halted. Press IPL to restart.");
            return;
        }

        try {
            gui.appendToPrinter("\n=== Single Step Execution ===");
            executeInstruction();
        } catch (Exception e) {
            gui.appendToPrinter("ERROR: " + e.getMessage());
            mfr = FAULT_ILLEGAL_OPERATION;
            halt();
            gui.showError("Execution Error", "Failed to execute instruction: " + e.getMessage());
        }
    }

    public void halt() {
        isHalted = true;
        if (runWorker!= null &&!runWorker.isDone()) {
            runWorker.cancel(true);
        }
        System.out.println("Execution halted.");
    }

    private boolean checkMemoryFault(int address) {
        // First check for out of bounds memory access
        if (address < 0 || address > 2047) {
            mfr = FAULT_ILLEGAL_MEM_ADDR;
            halt();
            gui.appendToPrinter("ERROR: Illegal Memory Address: " + address);
            gui.showError("Memory Fault", "Illegal Memory Address to Non-existent Location (address must be 0-2047)");
            return true;
        }

        // For loading programs, we need to check if we're writing to reserved memory
        if (address >= RESERVED_MEM_START && address <= RESERVED_MEM_END) {
            String purpose;
            switch (address) {
                case 0 -> purpose = "Trap Instruction Vector Table";
                case 1 -> purpose = "Machine Fault Handler Address";
                case 2 -> purpose = "Trap PC Storage";
                case 3 -> purpose = "Reserved (Not Used)";
                case 4 -> purpose = "Machine Fault PC Storage";
                case 5 -> purpose = "Reserved (Not Used)";
                default -> purpose = "Reserved";
            }
            gui.appendToPrinter(String.format("WARNING: Accessing Reserved Location %d (%s)", address, purpose));
        }
        return false;
    }

    public String getFormattedMemory() {
        StringBuilder sb = new StringBuilder();
        // Show more memory locations, including the ones from your program
        for (int i = 0; i < 2048; i += 8) {
            boolean hasNonZero = false;
            // Check if this row has any non-zero values
            for (int j = 0; j < 8 && (i + j) < 2048; j++) {
                if (memory[i + j] != 0) {
                    hasNonZero = true;
                    break;
                }
            }
            
            // Only show rows that have non-zero values or are in the first few rows
            if (hasNonZero || i < 32) {
                sb.append(String.format("%04o: ", i));
                for (int j = 0; j < 8 && (i + j) < 2048; j++) {
                    sb.append(utils.shortToOctal(memory[i + j], 6)).append(" ");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    // Getters for GUI
    public short getGPR(int i) { return gpr[i]; }
    public short getIXR(int i) { return ixr[i]; }
    public short getPC() { return pc; }
    public short getMAR() { return mar; }
    public short getMBR() { return mbr; }
    public short getIR() { return ir; }
    public short getCC() { return cc; }
    public short getMFR() { return mfr; }
    public Utils getUtils() { return utils; }
    
    // Setters for GUI
    public void setGPR(int i, short value) { gpr[i] = value; }
    public void setIXR(int i, short value) { ixr[i] = value; }
    public void setPC(short value) { pc = value; }
    public void setMAR(short value) { mar = value; }
    public void setMBR(short value) { mbr = value; }
    public void setIR(short value) { ir = value; }
}

/**
 * Utility class for number conversions.
 */
class Utils {
    public short octalToShort(String octal) {
        return (short) Integer.parseInt(octal, 8);
    }

    public String shortToOctal(short value, int digits) {
        return String.format("%0" + digits + "o", value);
    }

    public String shortToBinary(short value, int bits) {
        return String.format("%" + bits + "s", Integer.toBinaryString(value & 0xFFFF)).replace(' ', '0');
    }
}