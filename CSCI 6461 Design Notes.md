# Design notes

## Java sources (src/)

### Assembler.java
A two‑pass assembler that converts assembly sources (.asm) into a loadable object file (`load_file.txt`) and listing (`listing_output.txt`).

Key functions:
- run(String filename) — Orchestrates reading, first/second passes, and output generation.
- readSourceFile(String filename) — Loads the source file into memory, preserving original lines.
- firstPass() — Builds the symbol table and computes addresses (default origin at octal 000006).
- secondPass() — Translates each line to machine code and builds the load file map and listing.
- translate(String instruction, String operands) — Encodes a single instruction into a 16‑bit word (octal string).
- parseValue(String token) — Resolves labels or parses numeric literals; tolerant fallback to 0 on malformed tokens.
- writeOutputFiles(String originalFilename) — Writes `listing_output.txt` and `load_file.txt` next to the source (skips addresses 0..5 in load file).

### CPU.java
Core CPU model: registers, memory, fetch‑decode‑execute loop, I/O (console in/out), and integration with the Cache and GUI.

Key functions:
- CPU(SimulatorGUI gui) — Initializes CPU state and constructs a Cache bound to main memory.
- ipl(File programFile) — Resets, loads `load_file.txt` (if provided), and prepares for execution.
- resetMachine() — Clears registers/memory, invalidates cache, clears printer, and readies CPU to run.
- loadProgram(File programFile) — Loads assembled octal address/value pairs into main memory; sets PC to first address.
- load(short address) / loadPlus(short address) — Memory reads (via cache) into internal buffers.
- store(short address) / storePlus(short value) — Memory writes (write‑through) from internal buffers to memory (and cache on hit).
- runProgram() — Starts the background execution loop.
- singleStep() — Executes one instruction (useful for debugging/stepping).
- halt() — Stops the CPU and ends the run loop.
- submitConsoleInput(String text) — Queues characters to the keyboard device; OUT prints through the GUI console.
- isWaitingForInput() — Indicates when the CPU is blocked awaiting keyboard input.
- getFormattedCache() / getFormattedMemory() — Renders cache/memory for the GUI view.
- Register/memory helpers: getGPR/getIXR/getPC/...; peekMemory(short address) for non‑intrusive reads.
- Utility formatting: octal/binary conversion helpers exposed via getUtils().

Notes:
- OUT uses R3 as the source register for the console device (enforced by the program and GUI conventions).
- Reserved addresses 0..5 are not used for user code/data; programs `LOC 6` to start after the reserved region.

### Cache.java
A simple, fully associative cache for the simulator with FIFO replacement and write‑through/write‑around behavior.

Key functions:
- Cache(short[] mainMemory, SimulatorGUI gui, Utils utils) — Binds the cache to the CPU’s main memory and GUI for diagnostics.
- read(short address) — Returns a word, hitting in cache when present; on miss, fetches from memory and inserts using FIFO.
- write(short address, short data) — Write‑through: always updates main memory; on cache hit, also updates the cached line (no write allocate on miss).
- invalidate() — Clears all cache lines and resets FIFO pointer (called on reset/IPL).
- getFormattedCache() — Returns a human‑readable view of cache state for display in the GUI.

Design details:
- Capacity: 16 lines.
- Associativity: fully associative (any line can hold any address).
- Block size: 1 word per line. Tag equals the full memory address.
- Replacement: FIFO (round‑robin pointer selects next victim line).
- Write policy: write‑through on hits; write‑around (no‑write‑allocate) on misses.

### ISA.java
Opcode table and helpers for encoding/validation.

Key functions:
- octal(String oct) — Parses a base‑8 string into an int.
- getOpcode(String mnemonic) — Looks up the 6‑bit opcode for a mnemonic (upper‑cased).
- isValidOpcode(int opcode) — Returns true if opcode exists in the ISA map.

### SimulatorGUI.java
Swing GUI hosting the CPU, cache/memory views, console input, and printer output.

Key functions:
- SimulatorGUI() — Builds the UI and constructs an attached CPU.
- handleButtonPress(String command) — Responds to IPL/Run/Step/Reset and other UI actions.
- appendToPrinter(String text) — Appends log/status lines to the printer area (ensures newline separation for logs).
- printToConsole(String text) — Appends characters emitted by OUT to the output buffer (no per‑character newline).
- clearPrinter() — Clears printer output and the console buffer.
- updateAllDisplays() / updateRegisters() / updateCacheView() — Refreshes UI panels from CPU state.
- getCpu() — Exposes the CPU instance used by the GUI.
- getPrinterText() / getConsoleOut() — Accessors for test harnesses and diagnostics.

### Main.java
Application entry point.

Key function:
- main(String[] args) — Launches the `SimulatorGUI` UI.

### AsmDriver.java
Small CLIs around the assembler for convenience.

Key functions:
- AsmDriver.main(args) — `java AsmDriver <file.asm>` assembles a single file to `load_file.txt`.
---

## Cache design (implemented)

- Organization: 16‑line, fully associative cache; each line stores one 16‑bit word with tag equal to the full address; a valid bit indicates presence.
- Replacement: FIFO pointer advances on each miss insertion to select the next victim line.
- Read behavior: On hit, returns cached data. On miss, reads from main memory and inserts the (address,data) pair into the line pointed by FIFO, then advances the pointer.
- Write policy: Write‑through on hits (updates both main memory and the matching cache line). On misses, write‑around (no write‑allocate): only main memory is updated; cache remains unchanged.
- Reset/IPL: `Cache.invalidate()` clears all valid bits and resets the FIFO pointer (called during `CPU.resetMachine()` and after program load).
- Visibility: `getFormattedCache()` produces a display showing FIFO pointer, valid bits, and octal TAG/DATA used by the GUI’s cache view.

---
