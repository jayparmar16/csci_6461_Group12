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
    
    // === NEW: Cache and I/O Components ===
    private final Cache cache;
    private String kbdBuffer = "";
    private boolean isWaitingForInput = false;
    // ===================================

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
        // === NEW: Initialize the Cache ===
        this.cache = new Cache(this.memory, this.gui, this.utils);
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
        
        // === NEW: Clear Cache and I/O State ===
        cache.invalidate();
        kbdBuffer = "";
        isWaitingForInput = false;
        gui.clearPrinter(); // Clear the printer on reset
        // ======================================
        
        isHalted = false;
    }
    
    public void loadProgram(File programFile) {
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
                            // Load directly into main memory, bypassing cache
                            memory[address] = value; 
                            System.out.printf("Loaded memory[%04o] with value %06o\n", address, value);
                            if (firstAddress == -1) {
                                firstAddress = (short) address;
                            }
                        } else {
                            throw new IOException("Memory address " + String.format("%04o", address) + " is out of bounds.");
                        }
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Warning: Skipping invalid line in program file: " + line);
                }
            }
            
            // === NEW: Invalidate cache after loading program ===
            cache.invalidate();
            // =================================================

            if (firstAddress != -1) {
                pc = firstAddress; // Set PC to the address of the very first instruction loaded
                isHalted = false;
                System.out.printf("\nProgram loaded. PC set to the first address: %04o\n", pc);
            } else {
                System.out.println("Program file was empty or contained no valid data. PC remains 0.");
            }
            
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
        System.out.printf("IR:  %06o    CC:  %s (bin)    MFR: %s (bin)\n", ir, utils.shortToBinary(cc, 4), utils.shortToBinary(mfr, 4));
        System.out.println("\nGeneral Purpose Registers:");
        for (int i = 0; i < 4; i++) {
            System.out.printf("GPR%d: %06o   ", i, gpr[i]);
            if (i % 2 == 1) System.out.println();
        }
        System.out.println("\nIndex Registers:");
        for (int i = 1; i < 4; i++) {
            System.out.printf("IXR%d: %06o   ", i, ixr[i]);
        }
        System.out.println("\n==============================");
    }

    private void updateDisplayAndLog() {
        String status = String.format("\nRegisters after execution:\n" +
                                    "PC: %06o  MAR: %06o  MBR: %06o  IR: %06o\n" +
                                    "GPRs: %06o  %06o  %06o  %06o\n" +
                                    "IXRs: %06o  %06o  %06o\n",
                                    pc, mar, mbr, ir,
                                    gpr[0], gpr[1], gpr[2], gpr[3],
                                    ixr[1], ixr[2], ixr[3]);
        // gui.appendToPrinter(status); // This is too noisy, let's keep it in the console
        System.out.println(status);
        gui.updateAllDisplays();
    }

    public void load(short address) {
        mar = address;
        if (checkMemoryFault(mar)) return;
        // === MODIFIED: Use Cache ===
        mbr = cache.read(mar);
        // ===========================
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
        // === MODIFIED: Use Cache ===
        cache.write(mar, mbr);
        // ===========================
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
        
        // === MODIFIED: Use Cache for instruction fetch ===
        mbr = cache.read(mar);
        // ===============================================
        ir = mbr;
        
        // 2. Increment PC for next instruction
        pc++;
        
        // Decode instruction fields (Common Load/Store Format)
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
        
        // 4. Execute based on opcode
        // Effective Address Calculation (only for relevant instructions)
        int effectiveAddr = address;
        
        // EA logic is not needed for HLT, RFS, AIR, SIR, MLT, DVD, TRR, AND, ORR, NOT, SRC, RRC, IN, OUT, CHK
        // Only calculate EA if it's a memory-addressing instruction
        switch(opcode) {
             case 1: // LDR
             case 2: // STR
             case 3: // LDA
             case 4: // AMR
             case 5: // SMR
             case 8: // JZ
             case 9: // JNE
             case 10: // JCC
             case 11: // JMA
             case 12: // JSR
             case 14: // SOB
             case 15: // JGE
             case 33: // LDX
             case 34: // STX
                gui.appendToPrinter(String.format("  R: %o, IX: %o, I: %o, Addr: %o", r, ix, i, address));
                if (ix > 0 && ix < 4) {
                    short ixrValue = ixr[ix];
                    gui.appendToPrinter(String.format("Using IX%d: base %o + ixr %o", ix, address, ixrValue));
                    effectiveAddr += ixrValue;
                }

                if (i == 1) { // Indirect Addressing
                    if (!checkMemoryFault((short)effectiveAddr)) {
                        gui.appendToPrinter(String.format("Indirect addressing used. Getting final EA from memory[%06o]", effectiveAddr));
                        // === MODIFIED: Use Cache for indirect fetch ===
                        effectiveAddr = cache.read((short)effectiveAddr);
                        // ============================================
                    } else {
                        return; // Fault occurred
                    }
                }
                System.out.printf("Final Effective Address: %06o\n", effectiveAddr);
                break;
             default:
                // This instruction doesn't use the standard R,IX,I,Addr format
                break;
        }


        switch(opcode) {
            case 0: // HLT
                gui.appendToPrinter("HLT - Halting machine");
                halt();
                break;
                
            case 1: // LDR
                if (!checkMemoryFault((short)effectiveAddr)) {
                    // === MODIFIED: Use Cache ===
                    gpr[r] = cache.read((short)effectiveAddr);
                    // ===========================
                }
                break;
                
            case 2: // STR
                if (!checkMemoryFault((short)effectiveAddr)) {
                    // === MODIFIED: Use Cache ===
                    mbr = gpr[r]; // MBR gets value to be stored
                    cache.write((short)effectiveAddr, mbr);
                    // ===========================
                }
                break;
                
            case 3: // LDA
                gpr[r] = (short)effectiveAddr;
                break;

            case 4: // AMR - Add Memory to Register
                if (!checkMemoryFault((short)effectiveAddr)) {
                    // === MODIFIED: Use Cache ===
                    mbr = cache.read((short)effectiveAddr);
                    // ===========================
                    gpr[r] += mbr;
                }
                break;

            case 5: // SMR - Subtract Memory from Register
                if (!checkMemoryFault((short)effectiveAddr)) {
                    // === MODIFIED: Use Cache ===
                    mbr = cache.read((short)effectiveAddr);
                    // ===========================
                    gpr[r] -= mbr;
                }
                break;

            case 6: // AIR - Add Immediate to Register
                // For AIR/SIR, 'address' field (5 bits) is the immediate value
                gui.appendToPrinter(String.format("  R: %o, Immed: %o", r, address));
                gpr[r] += address;
                break;

            case 7: // SIR - Subtract Immediate from Register
                // For AIR/SIR, 'address' field (5 bits) is the immediate value
                gui.appendToPrinter(String.format("  R: %o, Immed: %o", r, address));
                gpr[r] -= address;
                break;

            case 8: // JZ - Jump if Zero
                if (gpr[r] == 0) pc = (short)effectiveAddr;
                break;

            case 9: // JNE - Jump if Not Equal (to zero)
                if (gpr[r] != 0) pc = (short)effectiveAddr;
                break;

            case 10: // JCC - Jump if Condition Code
                // The 'r' field holds the bit number to check (0, 1, 2, or 3)
                if ((cc & (1 << r)) != 0) {
                     pc = (short)effectiveAddr;
                }
                break;

            case 11: // JMA - Unconditional Jump
                pc = (short)effectiveAddr;
                break;

            case 12: // JSR - Jump and Save Return
                gpr[3] = pc; // Save return address in R3
                pc = (short)effectiveAddr;
                break;

            case 13: // RFS - Return From Subroutine
                pc = gpr[3]; // Return to saved address
                gpr[0] = (short)address; // Load R0 with immediate value (from 5-bit address field)
                break;

            case 14: // SOB - Subtract One and Branch
                gpr[r]--; // Decrement register
                if (gpr[r] > 0) pc = (short)effectiveAddr;
                break;

            case 15: // JGE - Jump Greater Than or Equal
                if (gpr[r] >= 0) pc = (short)effectiveAddr;
                break;
            
            // ISA Opcode for LDX is 41 octal = 33 decimal
            case 33: // LDX - Load Index Register from Memory
                 if (!checkMemoryFault((short)effectiveAddr)) {
                    // The 'ix' field specifies the target register for LDX/STX
                    if (ix > 0) {
                        // === MODIFIED: Use Cache ===
                        ixr[ix] = cache.read((short)effectiveAddr);
                        // ===========================
                    }
                }
                break;

            // ISA Opcode for STX is 42 octal = 34 decimal
            case 34: // STX - Store Index Register to Memory
                if (!checkMemoryFault((short)effectiveAddr)) {
                    // The 'ix' field specifies the source register for STX
                    if (ix > 0) {
                         mbr = ixr[ix];
                         // === MODIFIED: Use Cache ===
                         cache.write((short)effectiveAddr, mbr);
                         // ===========================
                    }
                }
                break;
                
            case 28: // MLT - Multiply Register by Register
                // implementation removed for brevity
                break;

            case 29: // DVD - Divide Register by Register
                // implementation removed for brevity
                break;

            case 30: // TRR - Test the Equality of Register and Register
                // implementation removed for brevity
                break;

            case 31: // AND - Logical AND of Register and Register
                // implementation removed for brevity
                break;

            case 32: // ORR - Logical OR of Register and Register
                // implementation removed for brevity
                break;

            case 25: // SRC - Shift Register by Count
                // Shift format: Op(6), R(2), A/L(1), L/R(1), Count(4)
                // CPU decode: Op(6), R(2), IX(2), I(1), Address(5)
                // This implies A/L+L/R are in IX, and Count is in Address
                // Let's fix this based on Assembler.java:
                // machineCode = (opcode << 10) | (r << 8) | (al << 7) | (lr << 6) | count;
                
                int count = ir & 0x1F;        // Bits 4-0
                int lr    = (ir >>> 6) & 0x1; // Bit 6 (Left/Right)
                int al    = (ir >>> 7) & 0x1; // Bit 7 (Arith/Logic)
                boolean left = (lr == 1);
                boolean logical = (al == 1);
                
                gui.appendToPrinter(String.format("  R: %o, A/L: %o, L/R: %o, Count: %d", r, al, lr, count));
                
                if (left) {
                    if (logical) {
                        gpr[r] = (short)(gpr[r] << count);
                    } else { // Arithmetic
                        gpr[r] = (short)(gpr[r] << count);
                    }
                } else { // Right
                    if (logical) {
                        gpr[r] = (short)((gpr[r] & 0xFFFF) >>> count);
                    } else { // Arithmetic
                        gpr[r] = (short)(gpr[r] >> count);
                    }
                }
                break;

            case 26: // RRC - Rotate Register by Count
                // Same format as SRC
                count = ir & 0x1F;        // Bits 4-0
                lr    = (ir >>> 6) & 0x1; // Bit 6 (Left/Right)
                al    = (ir >>> 7) & 0x1; // Bit 7 (Arith/Logic) -> Not used by RRC per ISA
                left = (lr == 1);
                short val = gpr[r];

                gui.appendToPrinter(String.format("  R: %o, L/R: %o, Count: %d", r, lr, count));
                
                if (count > 0) {
                    count %= 16; // Handle excessive rotates
                    if (left) {
                        gpr[r] = (short)((val << count) | ((val & 0xFFFF) >>> (16 - count)));
                    } else { // Right
                        gpr[r] = (short)(((val & 0xFFFF) >>> count) | (val << (16 - count)));
                    }
                }
                break;

            case 24: // TRAP
                throw new Exception("TRAP instruction not yet implemented");

            // === NEW: I/O Instruction Implementation ===
            
            // ISA Opcode for IN is 61 octal = 49 decimal
            case 49: // IN - Input from Device
            {
                // Format: Op(6), R(2), DevID(8)
                // DevID is in the lower 8 bits (per Assembler.java)
                int devId = ir & 0xFF; 
                gui.appendToPrinter(String.format("  R: %o, DevID: %d", r, devId));

                if (devId == 0) { // Console Keyboard
                    if (kbdBuffer.isEmpty()) {
                        // Wait for input
                        isWaitingForInput = true;
                        pc--; // Re-execute this instruction
                        gui.appendToPrinter("Program is waiting for input from Console Keyboard...");
                        gui.updateAllDisplays();
                        return; // Stop execution cycle
                    } else {
                        // Input is available
                        char c = kbdBuffer.charAt(0);
                        kbdBuffer = kbdBuffer.substring(1);
                        gpr[r] = (short) c;
                        gui.appendToPrinter("Read char '" + c + "' from keyboard into GPR" + r);
                    }
                } else {
                    gui.appendToPrinter("Warning: IN from unimplemented device " + devId);
                    gpr[r] = 0; // Return 0 for other devices
                }
                break;
            }

            // ISA Opcode for OUT is 62 octal = 50 decimal
            case 50: // OUT - Output to Device
            {
                int devId = ir & 0xFF;
                gui.appendToPrinter(String.format("  R: %o, DevID: %d", r, devId));
                
                if (devId == 1) { // Console Printer
                    // Output the lower 8 bits of the register as a character
                    char c = (char) (gpr[r] & 0xFF);
                    gui.printToConsole(String.valueOf(c)); // Use new method
                } else {
                    gui.appendToPrinter("Warning: OUT to unimplemented device " + devId);
                }
                break;
            }

            // ISA Opcode for CHK is 63 octal = 51 decimal
            case 51: // CHK - Check Device Status
            {
                int devId = ir & 0xFF;
                gui.appendToPrinter(String.format("  R: %o, DevID: %d", r, devId));

                gpr[r] = 0; // Default status = 0
                if (devId == 0) { // Console Keyboard
                    // Set GPR[r] to 1 if input is available, 0 otherwise
                    gpr[r] = kbdBuffer.isEmpty() ? (short)0 : (short)1;
                } else if (devId == 1) { // Console Printer
                    // Set GPR[r] to 1 (always ready)
                    gpr[r] = 1;
                } else {
                    gui.appendToPrinter("Warning: CHK for unimplemented device " + devId);
                }
                break;
            }
            // =======================================

            default:
                mfr = FAULT_ILLEGAL_OPCODE;
                halt();
                throw new Exception(String.format("Unimplemented or Illegal Instruction - Opcode: %02o", opcode));
        }
        
        // Update GUI and logs *after* successful execution
        updateDisplayAndLog();
    }

    public void runProgram() {
        // === MODIFIED: Don't run if waiting for input ===
        if (isWaitingForInput) {
             gui.appendToPrinter("Machine is waiting for input. Cannot run.");
             return;
        }
        
        if (!isHalted && (runWorker == null || runWorker.isDone())) {
            runWorker = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    // === MODIFIED: Check for halt/cancel/wait ===
                    while (!isHalted && !isCancelled() && !isWaitingForInput) {
                        singleStep(); // This will update isHalted or isWaitingForInput
                        
                        // Check again after step
                        if (isHalted || isWaitingForInput) {
                            break;
                        }

                        try {
                            Thread.sleep(10); // Reduced sleep time for faster execution
                            // SwingUtilities.invokeLater(() -> gui.updateAllDisplays()); // Too slow, update at end
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
                        System.out.println("Program execution completed or halted.");
                    }
                    if (isWaitingForInput) {
                         System.out.println("Program execution paused for input.");
                    }
                    // Final update
                    SwingUtilities.invokeLater(() -> gui.updateAllDisplays());
                }
            };
            runWorker.execute();
        }
    }
    
    // === NEW: Public method for GUI to submit input ===
    public void submitConsoleInput(String text) {
        this.kbdBuffer += text + "\n"; // Add newline as programs expect it
        if (isWaitingForInput) {
            isWaitingForInput = false;
            gui.appendToPrinter("-> Input '" + text + "' received. Press Run or Step to continue.");
        }
    }
    // ================================================

    public void singleStep() {
        if (isHalted) {
            gui.appendToPrinter("Machine is halted. Press IPL to restart.");
            return;
        }
        // === NEW: Check for input wait ===
        if (isWaitingForInput) {
            gui.appendToPrinter("Machine is waiting for input. Cannot step.");
            return;
        }

        try {
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
        if (address < 0 || address >= memory.length) {
            mfr = FAULT_ILLEGAL_MEM_ADDR;
            halt();
            gui.showError("Memory Fault", "Address " + address + " is out of bounds (0-2047).");
            return true;
        }

        // Check for *writes* to reserved memory (0-5)
        // Note: The prompt's program needs to read/write data. Let's relax this check
        // to a warning, as the original code did.
        if (address >= RESERVED_MEM_START && address <= RESERVED_MEM_END) {
            gui.appendToPrinter(String.format("WARNING: Accessing Reserved Location %d", address));
        }
        return false;
    }

    // === NEW: Getter for formatted cache content ===
    public String getFormattedCache() {
        return cache.getFormattedCache();
    }
    
    public String getFormattedMemory() {
        // This is still useful for a full memory dump if needed
        // But the main display will be the cache
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2048; i += 8) {
            boolean hasNonZero = false;
            for (int j = 0; j < 8 && (i + j) < 2048; j++) {
                if (memory[i + j] != 0) {
                    hasNonZero = true;
                    break;
                }
            }
            
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

    // Getters
    public short getGPR(int i) { return gpr[i]; }
    public short getIXR(int i) { return ixr[i]; }
    public short getPC() { return pc; }
    public short getMAR() { return mar; }
    public short getMBR() { return mbr; }
    public short getIR() { return ir; }
    public short getCC() { return cc; }
    public short getMFR() { return mfr; }
    public Utils getUtils() { return utils; }
    
    // Setters
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