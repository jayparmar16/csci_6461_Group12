import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
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
    * @param inputFilePath The path to the assembly language source file
    */
    public void assemble(String inputFilePath) {
         try {
            // Read all lines from the source file into memory
            List<String> sourceLines = readSourceFile(inputFilePath);

            // Execute Pass 1 to build the symbol table
            System.out.println("Starting Pass 1...");
            boolean pass1Success = passOne(sourceLines);
            if (!pass1Success) {
                System.out.println("Errors found in Pass 1. Stopping assembly....");
                return;
            }
            System.out.println("Pass 1 Complete. Symbol Table:");
            symbolTable.forEach((key, value) -> System.out.println("  " + key + ": " + value));

            // Execute Pass 2 to generate the machine code and output files
            System.out.println("\nStarting Pass 2...");
            passTwo(sourceLines, inputFilePath);
            System.out.println("Pass 2 Complete.");
            System.out.println("Assembly finished successfully!");
            System.out.println("Output files 'listing_output.txt' and 'load_file.txt' have been created.");

        } catch (FileNotFoundException e) {
            System.err.println("Error: Input file not found: " + inputFilePath);
        } catch (Exception e) {
            System.err.println("An error occurred during processing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
    * Reads the specified source file and returns its content as a list of strings
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
    * @param sourceLines The source code with each line as an element in a list
    * @return True if the pass completes without errors, otherwise false
    */
    private boolean passOne(List<String> sourceLines) {
        int locationCounter = 0;
        boolean hasErrors = false;
        for (String line : sourceLines) {
            String cleanLine = removeComments(line).trim();
            if (cleanLine.isEmpty()) continue;
            List<String> tokens = new ArrayList<>(Arrays.asList(cleanLine.split("\\s+")));
            String label = null;
            if (tokens.get(0).contains(":")) {
                String[] parts = tokens.get(0).split(":", 2);
                label = parts[0];
                tokens.remove(0);
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    tokens.add(0, parts[1]);
                }
            } else if (tokens.size() > 1 && tokens.get(1).equals(":")) {
                label = tokens.get(0);
                tokens.remove(0);
                tokens.remove(0);
            }
            if (label != null) {
                if (symbolTable.containsKey(label)) {
                    System.err.println("Error: Duplicate label '" + label + "' found.");
                    hasErrors = true;
                } else {
                    symbolTable.put(label, locationCounter);
                }
            }
            if (tokens.isEmpty()) {
                continue;
            }
            String instruction = tokens.get(0).toUpperCase();
            if (instruction.equals("LOC")) {
                try {
                    locationCounter = Integer.parseInt(tokens.get(1));
                } catch (NumberFormatException e) {
                    System.err.println("Error: Invalid number for LOC directive.");
                    hasErrors = true;
                }
            } else {
                locationCounter++;
            }
        }
        return !hasErrors;
    }

    /**
    * 2nd pass generates the machine code for each instruction and writes the listing and load files.
    * It uses the symbol table from Pass 1 to resolve label addresses.
    * @param sourceLines The source code, with each line as an element in a list.
    * @param inputFilePath The original file path, used to create output files in the same directory.
    */
    private void passTwo(List<String> sourceLines, String inputFilePath) throws Exception {
        File inputFile = new File(inputFilePath);
        String outputDir = inputFile.getParent();
        PrintWriter listingWriter = new PrintWriter(new File(outputDir, "listing_output.txt"));
        PrintWriter loadWriter = new PrintWriter(new File(outputDir, "load_file.txt"));

        int locationCounter = 0;

        for (String originalLine : sourceLines) {
            String cleanLine = removeComments(originalLine).trim();
            if (cleanLine.isEmpty()) {
                listingWriter.println(originalLine);
                continue;
            }

            List<String> tokens = new ArrayList<>(Arrays.asList(cleanLine.split("\\s+")));
            int currentPc = locationCounter;

            if (tokens.get(0).contains(":")) {
                String[] parts = tokens.get(0).split(":", 2);
                tokens.remove(0);
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    tokens.add(0, parts[1]);
                }
            } else if (tokens.size() > 1 && tokens.get(1).equals(":")) {
                tokens.remove(0);
                tokens.remove(0);
            }
            
            if (tokens.isEmpty()) {
                 listingWriter.println(String.format("%06o %-8s %s", locationCounter, "", originalLine));
                 continue;
            }

            String instruction = tokens.get(0).toUpperCase();
            int machineCode = 0;
            boolean generatesCode = true;

            if (instruction.equals("LOC")) {
                locationCounter = Integer.parseInt(tokens.get(1));
                generatesCode = false;
                listingWriter.println("       " + originalLine);
            } else if (instruction.equals("DATA")) {
                String valueStr = tokens.get(1);
                
                if (symbolTable.containsKey(valueStr)) {
                    machineCode = symbolTable.get(valueStr);
                } else {
                    machineCode = Integer.parseInt(valueStr);
                }

                locationCounter++;
            } else if (ISA.isInstruction(instruction)) {
                try {
                    machineCode = buildMachineCode(tokens);
                } catch (Exception e) {
                    System.err.println("Error on line: \"" + originalLine + "\" -> " + e.getMessage());
                    machineCode = 0;
                }
                locationCounter++;
            } else {
                System.err.println("Error: Unknown instruction '" + instruction + "'");
                generatesCode = false;
            }
            
            if (generatesCode) {
                String octalLocation = String.format("%06o", currentPc);
                String octalMachineCode = String.format("%06o", machineCode);
                listingWriter.printf("%s %s %s%n", octalLocation, octalMachineCode, originalLine);
                loadWriter.printf("%s %s%n", octalLocation, octalMachineCode);
            }
        }
        listingWriter.close();
        loadWriter.close();
    }
    
    /**
    * method which parses operands and packs them into the correct bit fields based on the
    * instruction's format as defined by the ISA
    * @param tokens The tokens for a single line of code (instruction and operands)
    * @return The generated 16-bit machine code as an integer
    */
    private int buildMachineCode(List<String> tokens) {
        String instruction = tokens.get(0).toUpperCase();
        Integer opcode = ISA.getOpcode(instruction);
        if (opcode == null) {
            throw new IllegalArgumentException("Invalid instruction : " + instruction);
        }
        int r = 0, ix = 0, i = 0, address = 0;
        int rx = 0, ry = 0;
        int count = 0, lr = 0, al = 0, devId = 0;
        String[] operands = (tokens.size() > 1) ? tokens.get(1).split(",") : new String[0];
        switch(instruction) {
            case "LDR": case "STR": case "LDA": case "LDX": case "STX":
            case "JZ": case "JNE": case "JCC": case "JMA": case "JSR":
            case "SOB": case "JGE": case "AMR": case "SMR":
                String[] parts = tokens.get(1).split(",");
                if (instruction.equals("LDX") || instruction.equals("STX")) {
                    ix = Integer.parseInt(parts[0]);
                    address = Integer.parseInt(parts[1]);
                    if (parts.length > 2) i = Integer.parseInt(parts[2]); 
                } else if (instruction.equals("JMA") || instruction.equals("JSR")) {
                    ix = Integer.parseInt(parts[0]);
                    address = Integer.parseInt(parts[1]);
                    if (parts.length > 2) i = Integer.parseInt(parts[2]);
                }
                else {
                    r = Integer.parseInt(parts[0]);
                    ix = Integer.parseInt(parts[1]);
                    address = Integer.parseInt(parts[2]);
                    if (parts.length > 3) i = Integer.parseInt(parts[3]);
                }
                return (opcode << 10) | (r << 8) | (ix << 6) | (i << 5) | address;
            case "AIR": case "SIR":
                r = Integer.parseInt(operands[0]);
                address = Integer.parseInt(operands[1]);
                return (opcode << 10) | (r << 8) | address;
            case "RFS":
                address = Integer.parseInt(operands[0]);
                return (opcode << 10) | address;
            case "MLT": case "DVD": case "TRR": case "AND": case "ORR":
                rx = Integer.parseInt(operands[0]);
                ry = Integer.parseInt(operands[1]);
                return (opcode << 10) | (rx << 8) | (ry << 6);
            case "NOT":
                rx = Integer.parseInt(operands[0]);
                return (opcode << 10) | (rx << 8);
            case "SRC": case "RRC":
                r = Integer.parseInt(operands[0]);
                count = Integer.parseInt(operands[1]);
                lr = Integer.parseInt(operands[2]);
                al = (instruction.equals("SRC")) ? Integer.parseInt(operands[3]) : 0;
                return (opcode << 10) | (r << 8) | (al << 7) | (lr << 6) | count;
            case "IN": case "OUT": case "CHK":
                r = Integer.parseInt(operands[0]);
                devId = Integer.parseInt(operands[1]);
                return (opcode << 10) | (r << 8) | devId;
            case "HLT":
                return opcode;
            default:
                 throw new IllegalArgumentException("Unsupported instruction in buildMachineCode: " + instruction);
        }
    }

    /**
    * utility method to strip comments from a line of code
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