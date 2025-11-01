import java.util.Arrays;

/**
 * A simple, 16-line, fully associative cache for the C6461 CPU.
 * Uses a FIFO (First-In, First-Out) replacement policy.
 * Uses a Write-Through (and Write-Around) policy:
 * - Write-Through: Writes go to both cache (if hit) and main memory.
 * - Write-Around (No-Write-Allocate): A write miss does NOT load the block into cache.
 */
public class Cache {

    /**
     * Inner class representing a single line in the cache.
     */
    private static class CacheLine {
        short tag;    // For fully associative, the tag is the full memory address.
        short data;   // The 16-bit data word stored in this line.
        boolean valid;

        CacheLine() {
            this.valid = false;
        }
    }

    private final CacheLine[] lines;
    private final short[] mainMemory; // A direct reference to the CPU's main memory.
    private final SimulatorGUI gui; // For logging hits and misses.
    private final Utils utils;
    private int fifoPointer; // Points to the next line to be replaced (FIFO).

    private static final int CACHE_SIZE = 16;

    public Cache(short[] mainMemory, SimulatorGUI gui, Utils utils) {
        this.mainMemory = mainMemory;
        this.gui = gui;
        this.utils = utils;
        this.lines = new CacheLine[CACHE_SIZE];
        for (int i = 0; i < CACHE_SIZE; i++) {
            lines[i] = new CacheLine();
        }
        this.fifoPointer = 0;
    }

    /**
     * Reads a word from a given memory address.
     * Checks cache first. On miss, fetches from main memory and loads into cache.
     *
     * @param address The 11-bit memory address to read from.
     * @return The 16-bit data word.
     */
    public short read(short address) {
        short tag = address;

        // 1. Check for Cache Hit
        for (int i = 0; i < CACHE_SIZE; i++) {
            if (lines[i].valid && lines[i].tag == tag) {
                gui.appendToPrinter("-> CACHE HIT (Read) @ " + utils.shortToOctal(address, 4));
                return lines[i].data;
            }
        }

        // 2. Cache Miss
        gui.appendToPrinter("-> CACHE MISS (Read) @ " + utils.shortToOctal(address, 4));
        
        // 3. Fetch from Main Memory
        short dataFromMem = mainMemory[address];

        // 4. Load into Cache (FIFO Replacement)
        gui.appendToPrinter("   (Loading mem[" + utils.shortToOctal(address, 4) + "] into cache line " + fifoPointer + ")");
        lines[fifoPointer].valid = true;
        lines[fifoPointer].tag = tag;
        lines[fifoPointer].data = dataFromMem;

        // 5. Move FIFO pointer
        fifoPointer = (fifoPointer + 1) % CACHE_SIZE;

        return dataFromMem;
    }

    /**
     * Writes a word to a given memory address using a Write-Through policy.
     *
     * @param address The 11-bit memory address to write to.
     * @param data The 16-bit data word to write.
     */
    public void write(short address, short data) {
        short tag = address;
        
        // 1. Write-Through: Always write data to main memory.
        mainMemory[address] = data;

        // 2. Check if the block is in the cache (Cache Hit)
        for (int i = 0; i < CACHE_SIZE; i++) {
            if (lines[i].valid && lines[i].tag == tag) {
                // 3a. Cache Hit: Update the data in the cache as well.
                gui.appendToPrinter("-> CACHE HIT (Write) @ " + utils.shortToOctal(address, 4));
                lines[i].data = data;
                return;
            }
        }

        // 3b. Cache Miss (Write-Around): Do nothing. The block is not loaded on a write miss.
        gui.appendToPrinter("-> CACHE MISS (Write) @ " + utils.shortToOctal(address, 4) + ". (Write-Through only)");
    }

    /**
     * Invalidates all lines in the cache.
     * Called during IPL or Reset.
     */
    public void invalidate() {
        for (int i = 0; i < CACHE_SIZE; i++) {
            lines[i].valid = false;
        }
        fifoPointer = 0;
        gui.appendToPrinter("Cache invalidated.");
    }

    /**
     * Generates a formatted string representing the current state of the cache.
     *
     * @return A string for display in the GUI.
     */
    public String getFormattedCache() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("FIFO Pointer -> Line %d\n", fifoPointer));
        sb.append("----------------------------------\n");
        sb.append("LN | V | TAG (OCT) | DATA (OCT)\n");
        sb.append("----------------------------------\n");

        for (int i = 0; i < CACHE_SIZE; i++) {
            CacheLine line = lines[i];
            sb.append(String.format("%02d | %s | %-9s | %-10s\n",
                    i,
                    line.valid ? "1" : "0",
                    line.valid ? utils.shortToOctal(line.tag, 4) : "-",
                    line.valid ? utils.shortToOctal(line.data, 6) : "-"
            ));
        }
        return sb.toString();
    }
}