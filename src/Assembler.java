import java.io.*;
import java.util.*;

/**
 * A self-contained, two-pass assembler for the C6461 computer architecture.
 * This class is used to convert assembly source files into machine-loadable code.
 */
public class Assembler {
    
    private final Map<String, Integer> symbolTable = new HashMap<>();
    private final List<String> sourceLines = new ArrayList<>();
    private final TreeMap<Integer, String> loadFileMap = new TreeMap<>();
    private final List<String> listingFileLines = new ArrayList<>();

    public void run(String filename) {
        sourceLines.clear();
        symbolTable.clear();
        loadFileMap.clear();
        listingFileLines.clear();

        try {
            readSourceFile(filename);
            firstPass();
            secondPass();
            writeOutputFiles(filename);
        } catch (IOException e) {
            System.err.println("Error during assembly: " + e.getMessage());
        }
    }

    private void readSourceFile(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine())!= null) {
                sourceLines.add(line);
            }
        }
    }

    private void firstPass() {
        int locationCounter = 0;
        for (String line : sourceLines) {
            String cleanLine = line.split(";")[0].trim();
            if (cleanLine.isEmpty()) continue;
            List<String> tokens = new ArrayList<>(Arrays.asList(cleanLine.split("\\s+")));
            
            String label = null;
            if (tokens.get(0).endsWith(":")) {
                label = tokens.get(0).substring(0, tokens.get(0).length() - 1);
                tokens.remove(0);
            }
            if (label!= null) symbolTable.put(label, locationCounter);
            if (tokens.isEmpty()) continue;

            String instruction = tokens.get(0).toUpperCase();
            if ("LOC".equals(instruction)) {
                locationCounter = Integer.parseInt(tokens.get(1));
            } else {
                locationCounter++;
            }
        }
    }

    private void secondPass() {
        int locationCounter = 0;
        for (String originalLine : sourceLines) {
            String cleanLine = originalLine.split(";")[0].trim();
            if (cleanLine.isEmpty()) {
                listingFileLines.add(originalLine);
                continue;
            }
            List<String> tokens = new ArrayList<>(Arrays.asList(cleanLine.split("\\s+")));
            if (tokens.get(0).endsWith(":")) tokens.remove(0);
            if (tokens.isEmpty()) {
                listingFileLines.add(String.format("              %s", originalLine));
                continue;
            }
            String instruction = tokens.get(0).toUpperCase();
            String operands = tokens.size() > 1? String.join(",", tokens.subList(1, tokens.size())) : "";

            if ("LOC".equals(instruction)) {
                locationCounter = Integer.parseInt(operands);
                listingFileLines.add(String.format("              %s", originalLine));
                continue;
            }
            int currentAddress = locationCounter;
            String machineCode = translate(instruction, operands);
            if (machineCode!= null) {
                loadFileMap.put(currentAddress, machineCode);
                listingFileLines.add(String.format("%06o %s %s", currentAddress, machineCode, originalLine));
            } else {
                listingFileLines.add(String.format("%06o?????? %s ; ERROR: Invalid", currentAddress, originalLine));
            }
            locationCounter++;
        }
    }

    private String translate(String instruction, String operands) {
        if ("DATA".equals(instruction)) return String.format("%06o", parseValue(operands));
        Integer opcode = ISA.getOpcode(instruction);
        if (opcode == null) return null;
        String[] params = operands.isEmpty() ? new String[0] : operands.split(",");
        try {
            int machineCode;
            switch (instruction) {
                case "LDR", "STR", "LDA", "AMR", "SMR", "JZ", "JNE", "JCC", "SOB", "JGE" -> {
                    int r = parseValue(params[0]);
                    int ix = parseValue(params[1]);
                    int address = parseValue(params[2]);
                    int i = (params.length == 4)? 1 : 0;
                    machineCode = (opcode << 10) | (r << 8) | (ix << 6) | (i << 5) | address;
                }
                case "LDX", "STX", "JMA", "JSR" -> {
                    int ix = parseValue(params[0]);
                    int address = parseValue(params[1]);
                    int i = (params.length == 3)? 1 : 0;
                    machineCode = (opcode << 10) | (ix << 6) | (i << 5) | address;
                }
                case "AIR", "SIR" -> {
                    int r = parseValue(params[0]);
                    int immed = parseValue(params[1]);
                    machineCode = (opcode << 10) | (r << 8) | immed;
                }
                case "RFS" -> machineCode = (opcode << 10) | parseValue(params[0]);
                case "MLT", "DVD", "TRR", "AND", "ORR" -> {
                    int rx = parseValue(params[0]);
                    int ry = parseValue(params[1]);
                    machineCode = (opcode << 10) | (rx << 8) | (ry << 6);
                }
                case "NOT" -> machineCode = (opcode << 10) | (parseValue(params[0]) << 8);
                case "SRC", "RRC" -> {
                    int r = parseValue(params[0]);
                    int count = parseValue(params[1]);
                    int lr = parseValue(params[2]);
                    int al = (params.length == 4)? parseValue(params[3]) : 0;
                    machineCode = (opcode << 10) | (r << 8) | (al << 7) | (lr << 6) | count;
                }
                case "IN", "OUT", "CHK" -> {
                    int r = parseValue(params[0]);
                    int devId = parseValue(params[1]);
                    machineCode = (opcode << 10) | (r << 8) | devId;
                }
                case "HLT" -> machineCode = opcode;
                default -> { return null; }
            }
            return String.format("%06o", machineCode);
        } catch (Exception e) { return null; }
    }

    private int parseValue(String token) {
        if (token == null) return 0;
        String trimmedToken = token.trim();
        try {
            return symbolTable.getOrDefault(trimmedToken, Integer.parseInt(trimmedToken));
        } catch (NumberFormatException e) {
            // If it's not a valid number and not in symbol table, return 0
            return symbolTable.getOrDefault(trimmedToken, 0);
        }
    }

    private void writeOutputFiles(String originalFilename) throws IOException {
        File inputFile = new File(originalFilename);
        String outputDir = inputFile.getParent()!= null? inputFile.getParent() : ".";
        try (PrintWriter writer = new PrintWriter(new FileWriter(new File(outputDir, "listing_output.txt")))) {
            listingFileLines.forEach(writer::println);
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(new File(outputDir, "load_file.txt")))) {
            loadFileMap.forEach((addr, code) -> writer.printf("%06o %s%n", addr, code));
        }
    }
}