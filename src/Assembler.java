import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

public class Assembler {

    private final HashMap<String, Integer> symbolTable;

    /**
     * initializes an empty symbol table, ready for the first pass
     */
    public Assembler() {
        this.symbolTable = new HashMap<>();
    }

    /**
     * main method which orchestrates the two-pass assembly process
     *
     * @param inputFilePath The path to the assembly language source file
     */
    public void assemble(String inputFilePath) {
        try {
            List<String> sourceLines = readSourceFile(inputFilePath);

            System.out.println("Starting Pass 1...");
            boolean pass1Success = passOne(sourceLines);
            if (!pass1Success) {
                System.out.println("Errors found in Pass 1. Stopping assembly....");
                return;
            }
            System.out.println("Pass 1 Complete. Symbol Table:");
            symbolTable.forEach((key, value) -> System.out.println("  " + key + ": " + value));

            System.out.println("\nStarting Pass 2...");
            passTwo(sourceLines, inputFilePath);
            System.out.println("Pass 2 Complete.");

        } catch (Exception e) {
            System.err.println("An error occurred during processing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reads the specified source file and returns its content as a list of strings
     *
     * @param filePath The path to the source file
     * @return A list of strings with each string representing a line from the file
     */
    private List<String> readSourceFile(String filePath) throws FileNotFoundException {
        List<String> lines = new ArrayList<>();
        Scanner scanner = new Scanner(new File(filePath));
        while (scanner.hasNextLine()) {
            lines.add(scanner.nextLine());
        }
        scanner.close();
        return lines;
    }

    /**
     * 1st pass reads the entire source file to identify all labels and record their memory addresses in the symbol table.
     *
     * @param sourceLines The source code with each line as an element in a list
     * @return True if the pass completes without errors, otherwise false
     */
    private boolean passOne(List<String> sourceLines) {
        // To be implemented
        return true;
    }

    /**
     * 2nd pass generates the machine code for each instruction and writes the listing and load files.
     * It uses the symbol table from Pass 1 to resolve label addresses.
     *
     * @param sourceLines   The source code, with each line as an element in a list.
     * @param inputFilePath The original file path, used to create output files in the same directory.
     */
    private void passTwo(List<String> sourceLines, String inputFilePath) throws Exception {
        // To be implemented in a future commit
    }

    /**
     * method which parses operands and packs them into the correct bit fields based on the
     * instruction's format as defined by the ISA
     *
     * @param tokens The tokens for a single line of code (instruction and operands)
     * @return The generated 16-bit machine code as an integer
     */
    private int buildMachineCode(List<String> tokens) {
        // To be implemented
        return 0;
    }

    /**
     * utility method to strip comments from a line of code
     *
     * @param line The full line of source code
     * @return The line with the comment part removed
     */
    private String removeComments(String line) {
        if (line.contains(";")) {
            return line.substring(0, line.indexOf(';'));
        }
        return line;
    }
}