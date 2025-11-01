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
}