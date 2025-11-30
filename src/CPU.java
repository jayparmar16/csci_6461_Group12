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
    // Toggle for verbose debug logging
    private static final boolean DEBUG = false;

    // Registers
    private short[] gpr = new short[4];
    private short[] ixr = new short[4]; // Index 0 is unused
    private short pc, mar, mbr, ir, mfr, cc;

    // Memory
    private final short[] memory = new short[2048];
    
    // === NEW: Cache and I/O Components ===
    private final Cache cache;
    private String kbdBuffer = "";
    private volatile boolean isWaitingForInput = false;
    // Tracks whether the CPU was running when it entered a keyboard wait so we can auto-resume
    private boolean wasRunningBeforeWait = false;
    // === TRAP paragraph search state ===
    private boolean trapActive = false;          // true while paragraph search TRAP executing phases
    private int trapPhase = 0;                   // 0=print paragraph,1=read word,2=search,3=done
    private StringBuilder trapWordBuffer = new StringBuilder(); // collected user word
    private int trapSentenceNum = 1;            
    private int trapWordNum = 0;                
    private int trapStartAddr = -1;              
    private int trapEndAddr = -1;                
    // Remember last text loaded via GUI 'Load Text'
    private int lastLoadedTextStart = -1;
    private int lastLoadedTextLength = 0;
    // Diagnostic watch range for paragraph memory (broad by default)
    private static final int PAR_CANONICAL_START = 64;
    private static final int PAR_WATCH_END = 500; // inclusive upper scan bound for diagnostic logging
    // ===================================

    // Fault Codes
    private static final short FAULT_ILLEGAL_MEM_ADDR = 1; // 0001
    // private static final short FAULT_RESERVED_LOCATION = 2; // 0010 (unused)
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
        if (DEBUG) System.out.println("\n=== Initializing Machine (IPL) ===");
        resetMachine();
        if (programFile != null) {
            loadProgram(programFile);
        } else {
            if (DEBUG) System.out.println("No program file selected for IPL.");
        }
        if (DEBUG) System.out.println("\nMachine ready. PC may be set to start address if a program was loaded.");
        if (DEBUG) printRegisterState();
        gui.updateAllDisplays();
    }

    /**
     * Report a memory write to GUI/System.out when it touches the paragraph/watch region.
     * This is a lightweight, temporary diagnostic helper.
     */
    private void reportMemWrite(int addr, short val, String context) {
        int a = addr & 0xFFFF;
        boolean inWatch = false;
        if (lastLoadedTextStart >= 0 && lastLoadedTextLength > 0) {
            if (a >= lastLoadedTextStart && a < lastLoadedTextStart + lastLoadedTextLength) inWatch = true;
        }
        if (a >= PAR_CANONICAL_START && a <= PAR_WATCH_END) inWatch = true;
        if (!inWatch) return;
        int byteVal = val & 0xFF;
        char ch = (char) byteVal;
        String chDisp = Character.isISOControl(ch) ? String.format("\\x%02x", byteVal) : String.valueOf(ch);
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        String caller = (st.length > 3) ? st[3].toString() : "unknown";
        String msg = String.format("[MEM-WRITE] %s addr=%04o val=%06o char='%s' ctx=%s", Thread.currentThread().getName(), a, val & 0xFFFF, chDisp, context + " caller=" + caller);
        gui.appendToPrinter(msg);
        System.out.println(msg);
    }

    /**
     * Normalize a token for comparison: strip leading/trailing non-alphanumeric
     * characters (punctuation) so that words like "outside," compare equal to "outside".
     */
    private String normalizeToken(String s) {
        if (s == null) return "";
        int st = 0, en = s.length() - 1;
        while (st <= en && !Character.isLetterOrDigit(s.charAt(st))) st++;
        while (en >= st && !Character.isLetterOrDigit(s.charAt(en))) en--;
        if (st > en) return "";
        return s.substring(st, en + 1);
    }
    
    public void resetMachine() {
        halt();
        if (DEBUG) System.out.println("Clearing all registers and memory...");
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
            if (DEBUG) System.out.println("\nLoading program from " + programFile.getName());
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
                            reportMemWrite(address, memory[address], "loadProgram");
                            if (DEBUG) System.out.printf("Loaded memory[%04o] with value %06o\n", address, value);
                            if (firstAddress == -1) {
                                firstAddress = (short) address;
                            }
                        } else {
                            throw new IOException("Memory address " + String.format("%04o", address) + " is out of bounds.");
                        }
                    }
                } catch (NumberFormatException e) {
                    if (DEBUG) System.out.println("Warning: Skipping invalid line in program file: " + line);
                }
            }
            
            // === NEW: Invalidate cache after loading program ===
            cache.invalidate();
            // =================================================

            if (firstAddress != -1) {
                pc = firstAddress; // Set PC to the address of the very first instruction loaded
                isHalted = false;
                if (DEBUG) System.out.printf("\nProgram loaded. PC set to the first address: %04o\n", pc);
            } else {
                if (DEBUG) System.out.println("Program file was empty or contained no valid data. PC remains 0.");
            }
            
            if (DEBUG) System.out.println("Program loading complete.");
            gui.updateAllDisplays();
        } catch (IOException e) {
            System.err.println("Error loading program: " + e.getMessage());
            gui.showError("Load Error", "Failed to load program: " + e.getMessage());
            resetMachine(); // Reset on error
        }
    }
    
    private void printRegisterState() {
        if (!DEBUG) return;
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
    // gui.appendToPrinter(status); // Too noisy for normal runs
    if (DEBUG) System.out.println(status);
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
        
        // // 2. Increment PC for next instruction
        // pc++;
        
        // // Decode instruction fields (Common Load/Store Format)
        // int opcode = (ir >>> 10) & 0x3F; // Bits 15-10
        // int r      = (ir >>> 8)  & 0x3;  // Bits 9-8
        // int ix     = (ir >>> 6)  & 0x3;  // Bits 7-6
        // int i      = (ir >>> 5)  & 0x1;  // Bit 5
        // int address = ir & 0xFF;         // Bits 4-0
        
        // // Log instruction details
        // gui.appendToPrinter("=== Instruction Execution ===");
        // gui.appendToPrinter(String.format("Location: %06o  Instruction: %06o", mar, ir));
        // gui.appendToPrinter("Decoded fields (octal):");
        // gui.appendToPrinter(String.format("  Opcode: %02o", opcode));
        
        // // 4. Execute based on opcode
        // // Effective Address Calculation (only for relevant instructions)
        // int effectiveAddr = address;
        
        // // EA logic is not needed for HLT, RFS, AIR, SIR, MLT, DVD, TRR, AND, ORR, NOT, SRC, RRC, IN, OUT, CHK
        // // Only calculate EA if it's a memory-addressing instruction
        // switch(opcode) {
        //      case 1: // LDR
        //      case 2: // STR
        //      case 3: // LDA
        //      case 4: // AMR
        //      case 5: // SMR
        //      case 8: // JZ
        //      case 9: // JNE
        //      case 10: // JCC
        //      case 11: // JMA
        //      case 12: // JSR
        //      case 14: // SOB
        //      case 15: // JGE
        //      case 33: // LDX
        //      case 34: // STX
        //         gui.appendToPrinter(String.format("  R: %o, IX: %o, I: %o, Addr: %o", r, ix, i, address));
        //         if (ix > 0 && ix < 4) {
        //             short ixrValue = ixr[ix];
        //             gui.appendToPrinter(String.format("Using IX%d: base %o + ixr %o", ix, address, ixrValue));
        //             effectiveAddr += ixrValue;
        //         }

        //         if (i == 1) { // Indirect Addressing
        //             if (!checkMemoryFault((short)effectiveAddr)) {
        //                 gui.appendToPrinter(String.format("Indirect addressing used. Getting final EA from memory[%06o]", effectiveAddr));
        //                 // === MODIFIED: Use Cache for indirect fetch ===
        //                 effectiveAddr = cache.read((short)effectiveAddr);
        //                 // ============================================
        //             } else {
        //                 return; // Fault occurred
        //             }
        //         }
        //         System.out.printf("Final Effective Address: %06o\n", effectiveAddr);
        //         break;
        //      default:
        //         // This instruction doesn't use the standard R,IX,I,Addr format
        //         break;
        // }

        // 2. Increment PC for next instruction
        pc++;

        // Decode instruction fields (NEW 8-BIT ADDRESS FORMAT)
        int opcode = (ir >>> 10) & 0x3F; // Bits 15-10 (6 bits)
        int r      = (ir >>> 8)  & 0x3;  // Bits 9-8  (2 bits)
        int address = ir & 0xFF;         // Bits 7-0  (8 bits)

    // These fields are no longer used in this design
    int ix     = 0;

        // Log instruction details (debug only)
        if (DEBUG) {
            gui.appendToPrinter("=== Instruction Execution ===");
            gui.appendToPrinter(String.format("Location: %06o  Instruction: %06o", mar, ir));
            gui.appendToPrinter("Decoded fields (octal):");
        }

        // 4. Execute based on opcode
        // Effective Address Calculation (only for relevant instructions)
        int effectiveAddr = address;

        // This switch now only needs to log R and Address
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
                if (DEBUG) gui.appendToPrinter(String.format("  Opcode: %02o, R: %o, Addr: %03o", opcode, r, address));
                break;
        }


        switch(opcode) {
            case 0: // HLT
                gui.appendToPrinter("HLT - Halting machine");
                // Dump a small set of registers to the printer and system console (debug only)
                if (DEBUG) {
                    gui.appendToPrinter("=== HLT REGISTER DUMP ===");
                    gui.appendToPrinter(String.format("GPR0: %06o GPR1: %06o GPR2: %06o GPR3: %06o", gpr[0], gpr[1], gpr[2], gpr[3]));
                    gui.appendToPrinter(String.format("IXR1: %06o IXR2: %06o IXR3: %06o PC: %06o", ixr[1], ixr[2], ixr[3], pc));
                    System.out.println(String.format("HLT DUMP GPRs: %06o %06o %06o %06o", gpr[0], gpr[1], gpr[2], gpr[3]));
                }
                // Keep PC pointing at the HLT instruction so pressing Run again
                // won't step into the data segment after the program ends.
                pc--; // pc was pre-incremented after fetch
                halt();
                break;
                

            // ISA Opcode for TRAP is 30 octal = 24 decimal
            case 24: // TRAP - Multi-service
            {
                int serviceId = address & 0xFF;
                switch (serviceId) {
                    case 0: {
                        // Legacy single-service: print paragraph (R0 sentinel-terminated), prompt, read, search
                        // Kept for backward compatibility
                        // R0 expected to contain start address of paragraph (sentinel 0 terminated)
                        if (!trapActive) {
                            trapActive = true;
                            trapPhase = 0;
                            trapWordBuffer.setLength(0);
                            trapSentenceNum = 1;
                            trapWordNum = 0;
                            trapStartAddr = gpr[0] & 0xFFFF;
                            trapEndAddr = trapStartAddr;
                            while (trapEndAddr < memory.length && memory[trapEndAddr] != 0) {
                                trapEndAddr++;
                            }
                        }
                        if (trapPhase == 0) {
                            for (int addr2 = trapStartAddr; addr2 < trapEndAddr; addr2++) {
                                char c = (char)(memory[addr2] & 0xFF);
                                gui.printToConsole(String.valueOf(c));
                            }
                            gui.printToConsole("\n?");
                            trapPhase = 1;
                            pc--; return;
                        }
                        if (trapPhase == 1) {
                            if (kbdBuffer.isEmpty()) {
                                wasRunningBeforeWait = (runWorker != null && !runWorker.isDone());
                                isWaitingForInput = true;
                                pc--; return;
                            }
                            char c = kbdBuffer.charAt(0);
                            kbdBuffer = kbdBuffer.substring(1);
                            if (c == '\n' || c == '\r') {
                                while (trapWordBuffer.length() > 0 && trapWordBuffer.charAt(trapWordBuffer.length()-1) == ' ') {
                                    trapWordBuffer.setLength(trapWordBuffer.length()-1);
                                }
                                trapPhase = 2;
                            } else {
                                trapWordBuffer.append(c);
                            }
                            pc--; return;
                        }
                        if (trapPhase == 2) {
                            String targetWord = trapWordBuffer.toString().trim();
                            if (targetWord.isEmpty()) {
                                gui.printToConsole("\nNOTFOUND");
                                trapPhase = 3; break;
                            }
                            boolean inWord = false;
                            int currentWordStart = -1;
                            trapSentenceNum = 1; trapWordNum = 0;
                            for (int a = trapStartAddr; a < trapEndAddr; a++) {
                                char c = (char)(memory[a] & 0xFF);
                                boolean delim = (c == ' ' || c == '\n' || c == '\r' || c == '\t');
                                boolean sentenceEnd = (c == '.');
                                if (!delim && !sentenceEnd) {
                                    if (!inWord) { inWord = true; currentWordStart = a; trapWordNum++; }
                                } else {
                                    if (inWord) {
                                        int len = a - currentWordStart;
                                        if (len == targetWord.length()) {
                                            boolean match = true;
                                            for (int i2 = 0; i2 < len; i2++) {
                                                char pcChar = (char)(memory[currentWordStart + i2] & 0xFF);
                                                if (pcChar != targetWord.charAt(i2)) { match = false; break; }
                                            }
                                            if (match) { gui.printToConsole("\n" + targetWord + " " + trapSentenceNum + " " + trapWordNum); trapPhase = 3; break; }
                                        }
                                        inWord = false;
                                    }
                                    if (sentenceEnd) { trapSentenceNum++; trapWordNum = 0; }
                                }
                            }
                            if (trapPhase != 3) {
                                if (inWord) {
                                    int len = trapEndAddr - currentWordStart;
                                    if (len == targetWord.length()) {
                                        boolean match = true;
                                        for (int i2 = 0; i2 < len; i2++) {
                                            char pcChar = (char)(memory[currentWordStart + i2] & 0xFF);
                                            if (pcChar != targetWord.charAt(i2)) { match = false; break; }
                                        }
                                        if (match) { gui.printToConsole("\n" + targetWord + " " + trapSentenceNum + " " + trapWordNum); trapPhase = 3; }
                                    }
                                }
                                if (trapPhase != 3) { gui.printToConsole("\nNOTFOUND"); trapPhase = 3; }
                            }
                            if (trapPhase != 3) { pc--; return; }
                            break;
                        }
                        if (trapPhase == 3) { trapActive = false; trapPhase = 0; }
                        break;
                    }
                    case 1: {
                        // Print memory from R0 for R1 bytes
                        int start = gpr[0] & 0xFFFF;
                        int len = gpr[1] & 0xFFFF;
                        int end = Math.min(memory.length, start + len);
                        for (int a = start; a < end; a++) {
                            char c = (char)(memory[a] & 0xFF);
                            gui.printToConsole(String.valueOf(c));
                        }
                        break;
                    }
                    case 2: {
                        // Read a word into memory at R0; stop at newline; return R1=len
                        if (kbdBuffer.indexOf("\n") == -1 && kbdBuffer.indexOf("\r") == -1) {
                            wasRunningBeforeWait = (runWorker != null && !runWorker.isDone());
                            isWaitingForInput = true;
                            pc--; return;
                        }
                        int start = gpr[0] & 0xFFFF;
                        int idx = start;
                        while (!kbdBuffer.isEmpty()) {
                            char c = kbdBuffer.charAt(0);
                            kbdBuffer = kbdBuffer.substring(1);
                            if (c == '\n' || c == '\r') { break; }
                            if (idx < memory.length) { memory[idx] = (short)(c & 0xFF); reportMemWrite(idx, memory[idx], "TRAP2-readword"); idx++; }
                        }
                        gpr[1] = (short)(idx - start);
                        cache.invalidate();
                        break;
                    }
                    case 3: {
                        // Search paragraph: R0=start, R1=len, R2=wordStart, R3=wordLen
                        int pStart = gpr[0] & 0xFFFF;
                        int pLen = gpr[1] & 0xFFFF;
                        int wStart = gpr[2] & 0xFFFF;
                        int wLen = gpr[3] & 0xFFFF;
                        // Build target word string
                        StringBuilder w = new StringBuilder();
                        for (int i2 = 0; i2 < wLen && (wStart + i2) < memory.length; i2++) {
                            w.append((char)(memory[wStart + i2] & 0xFF));
                        }
                        String target = w.toString();
                        gui.appendToPrinter("[TRAP3] target='" + target + "' len=" + wLen);
                        if (target.isEmpty()) { gpr[0] = 0; gpr[1] = 0; break; }
                        int sent = 1; int wnum = 0;
                        boolean inWord = false; int currentStart = -1;
                        int end = Math.min(memory.length, pStart + pLen);
                        if (pLen <= 0) {
                            int a2 = pStart;
                            while (a2 < memory.length && memory[a2] != 0) a2++;
                            end = a2;
                        }
                        gui.appendToPrinter("[TRAP3] range=" + pStart + ".." + end);
                        boolean found = false;
                        for (int a = pStart; a < end; a++) {
                            char c = (char)(memory[a] & 0xFF);
                            boolean delim = (c == ' ' || c == '\n' || c == '\r' || c == '\t');
                            boolean sentenceEnd = (c == '.');
                            if (!delim && !sentenceEnd) {
                                if (!inWord) { inWord = true; currentStart = a; wnum++; }
                            } else {
                                if (inWord) {
                                    int len = a - currentStart;
                                    // Diagnostic: report each discovered word, its sentence and word index
                                    StringBuilder wordSb = new StringBuilder();
                                    for (int j = 0; j < len && (currentStart + j) < memory.length; j++) {
                                        wordSb.append((char)(memory[currentStart + j] & 0xFF));
                                    }
                                    String foundWord = wordSb.toString();
                                    String diag = String.format("[TRAP3-DIAG] word='%s' sent=%d wnum=%d start=%04o len=%d", foundWord, sent, wnum, currentStart, len);
                                    gui.appendToPrinter(diag);
                                    System.out.println(diag);
                                    // Compare normalized tokens so punctuation doesn't prevent matches
                                    String normFound = normalizeToken(foundWord);
                                    String normTarget = normalizeToken(target);
                                    if (!normFound.isEmpty() && normFound.equals(normTarget)) { found = true; break; }
                                    inWord = false;
                                }
                                if (sentenceEnd) { sent++; wnum = 0; }
                            }
                        }
                        if (!found && inWord) {
                            int len = end - currentStart;
                            // Diagnostic for trailing word at end of range
                            StringBuilder tailSb = new StringBuilder();
                            for (int j = 0; j < len && (currentStart + j) < memory.length; j++) {
                                tailSb.append((char)(memory[currentStart + j] & 0xFF));
                            }
                            String tailWord = tailSb.toString();
                            String tailDiag = String.format("[TRAP3-DIAG] tailWord='%s' sent=%d wnum=%d start=%04o len=%d", tailWord, sent, wnum, currentStart, len);
                            gui.appendToPrinter(tailDiag);
                            System.out.println(tailDiag);
                            // Compare normalized tokens for trailing word
                            String normTail = normalizeToken(tailWord);
                            String normTarget = normalizeToken(target);
                            if (!normTail.isEmpty() && normTail.equals(normTarget)) { found = true; }
                        }
                        if (found) {
                            // Return sentence# in R0 and word# in R1; also print the standard line
                            gpr[0] = (short) sent;
                            gpr[1] = (short) wnum;
                            gui.printToConsole("\n" + target + " " + sent + " " + wnum);
                        } else {
                            gpr[0] = 0; gpr[1] = 0;
                            gui.printToConsole("\nNOTFOUND");
                        }
                        break;
                    }
                    case 4: {
                        // Load paragraph from default file into memory
                        // Inputs: R0=start addr, R1=max bytes
                        // Output: R1=actual bytes loaded; zero-terminate if space remains
                        int start = gpr[0] & 0xFFFF;
                        int cap = gpr[1] & 0xFFFF;
                        if (cap <= 0) { gpr[1] = 0; break; }
                        // Prefer GUI-loaded text if present. The GUI's Load Text writes into memory
                        // and records `lastLoadedTextStart`/`lastLoadedTextLength` so TRAP can prefer it.
                        // Diagnostic log of TRAP4 inputs
                        String lastInfo = (lastLoadedTextStart >= 0) ? String.format("lastLoadedStart=%04o,lastLoadedLen=%d", lastLoadedTextStart, lastLoadedTextLength) : "lastLoaded=none";
                        gui.appendToPrinter(String.format("TRAP4: start=%04o cap=%d (%s)", start, cap, lastInfo));
                        if (lastLoadedTextStart >= 0 && lastLoadedTextLength > 0) {
                            // If program expects the paragraph at the same address the GUI used,
                            // just use it.
                            if (start == lastLoadedTextStart) {
                                // If the GUI loaded text at the exact address the program expects,
                                // detect the full in-memory length (not capped) and prefer the
                                // GUI-recorded length if it's longer. Previously we limited the
                                // scan by `cap` which could miss GUI-loaded text beyond that
                                // limit and cause TRAP4 to erroneously report a shorter length.
                                int a3 = start;
                                while (a3 < memory.length && memory[a3] != 0) { a3++; }
                                int existingLen = a3 - start;
                                // Prefer GUI's recorded length when available (the GUI wrote the
                                // canonical buffer), even if it is larger than the program cap.
                                int guiLen = lastLoadedTextLength;
                                int usedLen = Math.max(existingLen, guiLen);
                                gpr[1] = (short) usedLen;
                                gui.appendToPrinter(String.format("TRAP4: Using paragraph at %04o, len=%d (memLen=%d, guiLen=%d)", start, usedLen, existingLen, lastLoadedTextLength));
                                break;
                            }
                            // If program expects paragraph somewhere else, consider copying the GUI-loaded
                            // text into the program's expected buffer. Prefer the GUI-loaded text when it
                            // is longer than the existing content at `start` (helps avoid truncated prints).
                            if (start >= 0 && start < memory.length) {
                                // compute existing length at start
                                int existingLen = 0;
                                int a2 = start;
                                while (a2 < memory.length && existingLen < cap && memory[a2] != 0) { a2++; existingLen++; }
                                int n = Math.min(cap, lastLoadedTextLength);
                                // Always copy GUI-loaded text into program buffer when GUI has a recent load.
                                // This prevents truncation when the GUI-loaded region differs from `start`.
                                if (n > 0) {
                                    for (int i2 = 0; i2 < n; i2++) {
                                        if (start + i2 < memory.length && lastLoadedTextStart + i2 < memory.length) {
                                            memory[start + i2] = memory[lastLoadedTextStart + i2];
                                            reportMemWrite(start + i2, memory[start + i2], "TRAP4-copy");
                                        }
                                    }
                                    if (start + n < memory.length) memory[start + n] = 0;
                                    gpr[1] = (short) n;
                                    cache.invalidate();
                                    gui.appendToPrinter(String.format("TRAP4: Copied GUI-loaded paragraph from %04o(len=%d) -> %04o(len=%d)", lastLoadedTextStart, lastLoadedTextLength, start, n));
                                    break;
                                }
                                // otherwise, leave existing content as-is and prefer it below
                            }
                            // Otherwise, fall through to check explicit memory at `start`.
                        }
                        // If memory at start already contains data (e.g. other preloaded content),
                        // prefer the in-memory content and avoid overwriting from disk.
                        if (start >= 0 && start < memory.length && memory[start] != 0) {
                            int a2 = start;
                            int wroteExisting = 0;
                            while (a2 < memory.length && wroteExisting < cap && memory[a2] != 0) { a2++; wroteExisting++; }
                            gpr[1] = (short) wroteExisting;
                            gui.appendToPrinter(String.format("TRAP4: Using paragraph already present in memory at %04o, len=%d", start, wroteExisting));
                            break;
                        }

                        String content = null;
                        try {
                            File f = new File("source.txt");
                            if (!f.exists()) {
                                f = new File("..\\source.txt");
                            }
                            if (!f.exists()) {
                                // Fallback: test.txt
                                f = new File("test.txt");
                                if (!f.exists()) f = new File("..\\test.txt");
                            }
                            if (f.exists()) {
                                StringBuilder sb = new StringBuilder();
                                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                                    String line;
                                    boolean first = true;
                                    while ((line = br.readLine()) != null) {
                                        if (!first) sb.append('\n');
                                        sb.append(line);
                                        first = false;
                                    }
                                }
                                content = sb.toString();
                            } else {
                                content = ""; // no file found -> empty
                            }
                        } catch (IOException ioe) {
                            content = "";
                        }
                        int wrote = 0;
                        if (content != null) {
                            int n = Math.min(cap, content.length());
                            for (int i2 = 0; i2 < n; i2++) {
                                if (start + i2 < memory.length) {
                                    memory[start + i2] = (short)(content.charAt(i2) & 0xFF);
                                    reportMemWrite(start + i2, memory[start + i2], "TRAP4-filecopy");
                                    wrote++;
                                } else { break; }
                            }
                            // Zero-terminate if space remains and memory not exceeded
                            if (start + wrote < memory.length && wrote < cap) {
                                memory[start + wrote] = 0;
                            }
                        }
                        gpr[1] = (short) wrote;
                        cache.invalidate();
                        break;
                    }
                    default: {
                        gui.appendToPrinter("Warning: Unknown TRAP service " + serviceId);
                        break;
                    }
                }
                break;
            }

            case 1: // LDR
                if (!checkMemoryFault((short)effectiveAddr)) {
                    // === MODIFIED: Use Cache ===
                    gpr[r] = cache.read((short)effectiveAddr);
                    // Targeted debug: when loading the saved paragraph length slot (PAR_LEN_SAVED at 242)
                    if (effectiveAddr == 242) {
                        short memVal = cache.read((short)effectiveAddr);
                        gui.appendToPrinter(String.format("DEBUG LDR: Loaded PAR_LEN_SAVED from mem[%04o] -> R%o = %d", effectiveAddr, r, memVal & 0xFFFF));
                        System.out.println(String.format("DEBUG LDR: Loaded PAR_LEN_SAVED from mem[%04o] -> R%o = %d", effectiveAddr, r, memVal & 0xFFFF));
                    }
                    // Debug: report loads from data area (129..146)
                    if (DEBUG) {
                        // Log loads from our data regions (old: 129..146, new: 239..251)
                        if ((effectiveAddr >= 129 && effectiveAddr <= 146) || (effectiveAddr >= 239 && effectiveAddr <= 251)) {
                            short memVal = cache.read((short)effectiveAddr);
                            int byteVal = memVal & 0xFF;
                            char ch = (char) byteVal;
                            String dbg = String.format("DEBUG LDR: mem[%03o] -> R%o = %06o  dec:%d  char:'%s'", effectiveAddr, r, memVal, byteVal, (Character.isISOControl(ch) ? "?" : String.valueOf(ch)));
                            gui.appendToPrinter(dbg);
                            // Also echo to System.out so terminal runs capture the debug
                            System.out.println(dbg);
                        }
                    }
                    // ===========================
                }
                break;
                
            case 2: // STR
                if (!checkMemoryFault((short)effectiveAddr)) {
                    // === MODIFIED: Use Cache ===
                    mbr = gpr[r]; // MBR gets value to be stored
                    cache.write((short)effectiveAddr, mbr);
                    // Targeted debug: when storing the saved paragraph length slot (PAR_LEN_SAVED at 242)
                    if (effectiveAddr == 242) {
                        short memVal = cache.read((short)effectiveAddr);
                        gui.appendToPrinter(String.format("DEBUG STR: Saved PAR_LEN_SAVED to mem[%04o] <= %d", effectiveAddr, memVal & 0xFFFF));
                        System.out.println(String.format("DEBUG STR: Saved PAR_LEN_SAVED to mem[%04o] <= %d", effectiveAddr, memVal & 0xFFFF));
                    }
                    // Debug: if program updates target/best-diff/best-value locations (0201/0202/0203), print a diagnostic
                    // Octal 0201 = decimal 129, 0202 = 130, 0203 = 131
                    // Debug: if program updates candidate/data locations (addresses in data area) print a diagnostic
                    if (DEBUG) {
                        // Log stores to our data regions (old: 129..146, new: 239..251)
                        if ((effectiveAddr >= 129 && effectiveAddr <= 146) || (effectiveAddr >= 239 && effectiveAddr <= 251)) {
                            short memVal = cache.read((short)effectiveAddr);
                            int byteVal = memVal & 0xFF;
                            char ch = (char) byteVal;
                            String dbg = String.format("DEBUG STR: mem[%03o] <= %06o  dec:%d  char:'%s'", effectiveAddr, memVal, byteVal, (Character.isISOControl(ch) ? "?" : String.valueOf(ch)));
                            gui.appendToPrinter(dbg);
                            // Also echo to System.out so terminal runs capture the debug
                            System.out.println(dbg);
                        }
                    }
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
                if (DEBUG) gui.appendToPrinter(String.format("  R: %o, Immed: %o", r, address));
                gpr[r] += address;
                break;

            case 7: // SIR - Subtract Immediate from Register
                // For AIR/SIR, 'address' field (5 bits) is the immediate value
                if (DEBUG) gui.appendToPrinter(String.format("  R: %o, Immed: %o", r, address));
                gpr[r] -= address;
                break;

            case 8: // JZ - Jump if Zero
                if (gpr[r] == 0) {
                    if (DEBUG) System.out.println(String.format("BRANCH JZ taken @ PC=%06o -> %06o (R%o==0)", pc, effectiveAddr, r));
                    pc = (short)effectiveAddr;
                }
                break;

            case 9: // JNE - Jump if Not Equal (to zero)
                if (gpr[r] != 0) {
                    if (DEBUG) System.out.println(String.format("BRANCH JNE taken @ PC=%06o -> %06o (R%o!=0)", pc, effectiveAddr, r));
                    pc = (short)effectiveAddr;
                }
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
                if (gpr[r] >= 0) {
                    if (DEBUG) System.out.println(String.format("BRANCH JGE taken @ PC=%06o -> %06o (R%o>=0)", pc, effectiveAddr, r));
                    pc = (short)effectiveAddr;
                }
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
                
            case 56: // MLT - Multiply Register by Register (opcode octal 70)
                // Format: Op(6), Rx(2), Ry(2) in bits. Multiply Rx *= Ry (lower 16 bits stored)
                {
                    int rx = (ir >>> 8) & 0x3;
                    int ry = (ir >>> 6) & 0x3;
                    int result = (gpr[rx] & 0xFFFF) * (gpr[ry] & 0xFFFF);
                    gpr[rx] = (short) result; // keep low 16 bits
                    if (DEBUG) gui.appendToPrinter(String.format("MLT: R%o = R%o * R%o => %06o", rx, rx, ry, gpr[rx]));
                }
                break;

            case 57: // DVD - Divide Register by Register (opcode octal 71)
                // Rx = Rx / Ry ; Ry = Rx % Ry (quotient in Rx, remainder in Ry)
                {
                    int rx = (ir >>> 8) & 0x3;
                    int ry = (ir >>> 6) & 0x3;
                    int dividend = gpr[rx];
                    int divisor = gpr[ry];
                    if (divisor == 0) {
                        mfr = FAULT_ILLEGAL_OPERATION;
                        gui.appendToPrinter("ERROR: Division by zero");
                        halt();
                        return;
                    }
                    int quot = dividend / divisor;
                    int rem = dividend % divisor;
                    gpr[rx] = (short) quot;
                    gpr[ry] = (short) rem;
                    if (DEBUG) gui.appendToPrinter(String.format("DVD: R%o = %06o, R%o = %06o (rem)", rx, gpr[rx], ry, gpr[ry]));
                }
                break;

            case 58: // TRR - Test the Relation of Register and Register (opcode octal 72)
                // Set condition codes: bit0 = equal, bit1 = less-than, bit2 = greater-than
                {
                    int rx = (ir >>> 8) & 0x3;
                    int ry = (ir >>> 6) & 0x3;
                    int lhs = gpr[rx];
                    int rhs = gpr[ry];
                    // Clear low 3 bits
                    cc &= ~0x7;
                    if (lhs == rhs) cc |= 0x1; // equal
                    else if (lhs < rhs) cc |= 0x2; // less-than
                    else cc |= 0x4; // greater-than
                    if (DEBUG) gui.appendToPrinter(String.format("TRR: compare R%o(%06o) vs R%o(%06o) => CC=%s", rx, lhs, ry, rhs, utils.shortToBinary(cc, 4)));
                }
                break;

            case 59: // AND - Logical AND of Register and Register (opcode octal 73)
                {
                    int rx = (ir >>> 8) & 0x3;
                    int ry = (ir >>> 6) & 0x3;
                    gpr[rx] = (short) (gpr[rx] & gpr[ry]);
                    if (DEBUG) gui.appendToPrinter(String.format("AND: R%o &= R%o => %06o", rx, ry, gpr[rx]));
                }
                break;

            case 60: // ORR - Logical OR of Register and Register (opcode octal 74)
                {
                    int rx = (ir >>> 8) & 0x3;
                    int ry = (ir >>> 6) & 0x3;
                    gpr[rx] = (short) (gpr[rx] | gpr[ry]);
                    if (DEBUG) gui.appendToPrinter(String.format("ORR: R%o |= R%o => %06o", rx, ry, gpr[rx]));
                }
                break;

            // ISA Opcode for NOT is 75 octal = 61 decimal
            case 61: // NOT
                // Format: Op(6), R(2), ...
                if (DEBUG) gui.appendToPrinter(String.format("  R: %o", r));
                gpr[r] = (short)(~gpr[r]); // Perform bitwise NOT
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
                
                if (DEBUG) gui.appendToPrinter(String.format("  R: %o, A/L: %o, L/R: %o, Count: %d", r, al, lr, count));
                
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

                if (DEBUG) gui.appendToPrinter(String.format("  R: %o, L/R: %o, Count: %d", r, lr, count));
                
                if (count > 0) {
                    count %= 16; // Handle excessive rotates
                    if (left) {
                        gpr[r] = (short)((val << count) | ((val & 0xFFFF) >>> (16 - count)));
                    } else { // Right
                        gpr[r] = (short)(((val & 0xFFFF) >>> count) | (val << (16 - count)));
                    }
                }
                break;

            // === NEW: I/O Instruction Implementation ===
            
            // ISA Opcode for IN is 61 octal = 49 decimal
            case 49: // IN - Input from Device
            {
                // Format: Op(6), R(2), DevID(8)
                // DevID is in the lower 8 bits (per Assembler.java)
                int devId = ir & 0xFF; 
                if (DEBUG) gui.appendToPrinter(String.format("  R: %o, DevID: %d", r, devId));

                if (devId == 0) { // Console Keyboard
                    if (kbdBuffer.isEmpty()) {
                        // Wait for input
                        // Record whether we were running so we can auto-resume when input arrives
                        wasRunningBeforeWait = (runWorker != null && !runWorker.isDone());
                        isWaitingForInput = true;
                        pc--; // Re-execute this instruction
                        gui.appendToPrinter(String.format("Program is waiting for input from Console Keyboard... (PC=%06o)", pc));
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
                if (DEBUG) {
                    System.out.println(String.format("EXECUTE OUT @ PC=%06o  IR=%06o  R=%o  DevID=%d", pc, ir, r, devId));
                    gui.appendToPrinter(String.format("  R: %o, DevID: %d", r, devId));
                }

                if (devId == 1) { // Console Printer
                    // Output the lower 8 bits of the register as a character
                    // Per spec and our assembly conventions, OUT must use R3.
                    // Enforce using R3 regardless of provided R field to be robust.
                    if (r != 3) {
                        gui.appendToPrinter(String.format("[WARN] OUT expected R3, but got R%o — using R3 anyway", r));
                    }
                    int value = gpr[3] & 0xFF;
                    char c = (char) value;

                    // Print the character to the printer area
                    gui.printToConsole(String.valueOf(c)); // Use new method
                    // Optional verbose OUT log for debugging (disabled by default)
                    if (DEBUG) {
                        gui.appendToPrinter("[OUT] " + (Character.isISOControl(c) ? String.format("<%03o>", value) : String.valueOf(c)));
                    }
                    if (DEBUG) {
                        int numericValue = -1;
                        if (Character.isDigit(c)) {
                            numericValue = value - '0';
                        }
                        String printable = Character.isISOControl(c) ? "?" : String.valueOf(c);
                        if (numericValue >= 0) {
                            gui.appendToPrinter(String.format("    OUT -> char: '%s'  dec(ascii): %d  num: %d  oct: %03o", printable, value, numericValue, value));
                            System.out.println(String.format("OUT -> char: '%s'  dec(ascii): %d  num: %d  oct: %03o", printable, value, numericValue, value));
                        } else {
                            gui.appendToPrinter(String.format("    OUT -> char: '%s'  dec: %d  oct: %03o", printable, value, value));
                            System.out.println(String.format("OUT -> char: '%s'  dec: %d  oct: %03o", printable, value, value));
                        }
                    }
                } else {
                    gui.appendToPrinter("Warning: OUT to unimplemented device " + devId);
                }
                break;
            }

            // ISA Opcode for CHK is 63 octal = 51 decimal
            case 51: // CHK - Check Device Status
            {
                int devId = ir & 0xFF;
                if (DEBUG) gui.appendToPrinter(String.format("  R: %o, DevID: %d", r, devId));

                gpr[r] = 0; // Default status = 0
                if (devId == 0) { // Console Keyboard
                    // Set GPR[r] to 1 if input is available, 0 otherwise
                    gpr[r] = kbdBuffer.isEmpty() ? (short)0 : (short)1;
                    if (DEBUG) {
                        System.out.println("CHK -> kbdBuffer.length=" + kbdBuffer.length() + ", setting GPR" + r + "=" + gpr[r]);
                        gui.appendToPrinter("DEBUG CHK: kbdBuffer.length=" + kbdBuffer.length() + ", GPR" + r + "=" + gpr[r]);
                    }
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
                // Gracefully handle unknown/illegal opcodes. This commonly happens when
                // execution falls through into the data segment after program end.
                mfr = FAULT_ILLEGAL_OPCODE;
                // Back up PC so repeated Run/Step won't keep advancing into data
                pc--; // pc was pre-incremented after fetch
                String msg = String.format("Unimplemented or Illegal Instruction - Opcode: %02o at PC=%06o IR=%06o", opcode, pc, ir);
                gui.appendToPrinter("ERROR: " + msg + " — Halting.");
                System.err.println(msg);
                halt();
                // Return early to avoid post-exec UI spam
                updateDisplayAndLog();
                return;
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
                            Thread.sleep(1); // Faster execution for headless tests
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
                    if (DEBUG) {
                        if (isHalted) {
                            System.out.println("Program execution completed or halted.");
                        }
                        if (isWaitingForInput) {
                             System.out.println("Program execution paused for input.");
                        }
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
        // Do not append an automatic newline. Treat Enter as a submit action, not as input.
        // Programs that need an explicit newline can include it in their input string.
        this.kbdBuffer += text;
    if (DEBUG) System.out.println("submitConsoleInput() called with: '" + text + "'  (kbdBuffer now length=" + kbdBuffer.length() + ")");
        gui.appendToPrinter("-> Input '" + text + "' received.");
        if (isWaitingForInput) {
            // Clear wait state and conditionally resume if the program was previously running
            isWaitingForInput = false;
            if (wasRunningBeforeWait) {
                wasRunningBeforeWait = false;
                gui.appendToPrinter("Resuming program execution.");
                // Start/resume the run worker
                runProgram();
            } else {
                gui.appendToPrinter("Press Run or Step to continue.");
            }
        }
    }
    // ================================================

    /**
     * Public getter so the GUI / test harness can detect when the CPU
     * is waiting for keyboard input. This is used by the auto-loader
     * to feed tokens one-by-one while the program runs.
     */
    public synchronized boolean isWaitingForInput() {
        return isWaitingForInput;
    }

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
        if (DEBUG) System.out.println("Execution halted.");
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
    // Expose last GUI-loaded text metadata for debugging/GUI display
    public int getLastLoadedTextStart() { return lastLoadedTextStart; }
    public int getLastLoadedTextLength() { return lastLoadedTextLength; }
    
    // Tiny helper: peek memory (via cache) at an absolute address for debugging/tests
    public short peekMemory(short address) {
        if (address < 0 || address >= memory.length) return 0;
        return cache.read(address);
    }
    
    // Setters
    public void setGPR(int i, short value) { gpr[i] = value; }
    public void setIXR(int i, short value) { ixr[i] = value; }
    public void setPC(short value) { pc = value; }
    public void setMAR(short value) { mar = value; }
    public void setMBR(short value) { mbr = value; }
    public void setIR(short value) { ir = value; }

    /**
     * Load a text file into main memory starting at the given address.
     * Each character is stored in the low 8 bits of a memory word.
     * Writes a trailing 0 sentinel after the last character (if space permits).
     * Returns the number of characters written.
     */
    public int loadTextIntoMemory(File file, int startAddr) throws IOException {
        if (file == null) throw new IOException("File is null");
        if (startAddr < 0 || startAddr >= memory.length) {
            throw new IOException("Start address out of bounds: " + startAddr);
        }
        int addr = startAddr;
        int written = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            int ch;
            while ((ch = br.read()) != -1) {
                if (addr >= memory.length) {
                    break; // stop if we run out of memory
                }
                memory[addr] = (short) (ch & 0xFF);
                reportMemWrite(addr, memory[addr], "loadTextIntoMemory");
                addr++;
                written++;
            }
        }
        // Append 0 sentinel if there is room
        if (addr < memory.length) {
            memory[addr] = 0;
            reportMemWrite(addr, memory[addr], "loadTextSentinel");
        }
        // Invalidate cache so GUI/memory view stays consistent
        cache.invalidate();
        // Update displays for immediate visual feedback
        gui.updateAllDisplays();
        // Record last loaded text region for TRAP4 to prefer GUI-loaded content
        lastLoadedTextStart = startAddr;
        lastLoadedTextLength = written;
        // Inform cache of the watch range so cache writes are also logged
        cache.setWatchRange(lastLoadedTextStart, lastLoadedTextLength);
        // If this is the sample paragraph buffer (start 64 / octal 0100), update PAR_LEN_SAVED (mem[242])
        int PAR_LEN_SAVED_ADDR = 242;
        if (startAddr == 64 && PAR_LEN_SAVED_ADDR >= 0 && PAR_LEN_SAVED_ADDR < memory.length) {
            memory[PAR_LEN_SAVED_ADDR] = (short) written;
            cache.invalidate();
            gui.appendToPrinter(String.format("GUI: Updated PAR_LEN_SAVED mem[%04o] <= %d", PAR_LEN_SAVED_ADDR, written));
        }
        return written;
    }
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