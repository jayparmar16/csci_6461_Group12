import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.awt.event.ActionListener;

/**
 * The main Graphical User Interface for the CSCI 6461 Machine Simulator.
 * This class builds the window, panels, and all the interactive components,
 * and it communicates with the CPU to run the simulation.
 */
public class SimulatorGUI extends JFrame {

    private final CPU cpu;
    private final StringBuilder consoleOut = new StringBuilder();

    // GUI Components
    private final JTextField[] gprTextFields = new JTextField[4];
    private final JTextField[] ixrTextFields = new JTextField[3];
    // PC and MAR are 12 bits -> 4 octal digits
    private final JTextField pcTextField = new JTextField(4);
    private final JTextField marTextField = new JTextField(4);
    // MBR and IR are 16 bits -> 6 octal digits
    private final JTextField mbrTextField = new JTextField(6);
    private final JTextField irTextField = new JTextField(6);
    // CC and MFR are 4 bits -> 4 binary digits
    private final JTextField ccTextField = new JTextField(4);
    private final JTextField mfrTextField = new JTextField(4);
    private final JTextField binaryDisplayField = new JTextField(20);
    private final JTextField octalInputField = new JTextField(12);
    private final JTextArea cacheContentArea = new JTextArea(35, 70);
    private final JTextArea printerArea = new JTextArea(15, 80);
    private final JTextField consoleInputTextField = new JTextField(70);
    // The programFileTextField is no longer needed here as IPL will handle file selection.

    public SimulatorGUI() {
        this.cpu = new CPU(this);

        setTitle("CSCI 6461 Machine Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLayout(new GridBagLayout());
        getContentPane().setBackground(new Color(200, 220, 240));

        setupComponents();
        addListeners();

        pack();
        setLocationRelativeTo(null);
        cpu.resetMachine(); // Perform initial reset on startup
        updateAllDisplays();
    // NOTE: automatic IPL/run on startup was removed to require explicit
    // user action (click IPL and select the load file). This prevents the
    // program from auto-executing without user intent.
    }

    private void setupComponents() {
        setPreferredSize(new Dimension(1200, 650));
        getContentPane().setBackground(new Color(200, 220, 240));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;
        
        // Left Panel (GPRs, IXRs)
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0.20;
        gbc.weighty = 1.0;
        JPanel registerPanel = createRegisterPanel();
        registerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(5, 5, 5, 5),
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 150, 255), 2),
                "Registers"
            )
        ));
        add(registerPanel, gbc);

        // Center Panel (PC, MAR, MBR, IR, etc.)
        gbc.gridx = 1;
        gbc.weightx = 0.30;
        JPanel centerPanel = createCenterPanel();
        centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(centerPanel, gbc);

        // Right Panel (Cache, Printer, Console)
        gbc.gridx = 2;
        gbc.weightx = 0.50;
        gbc.gridheight = 3;
        JPanel rightPanel = createRightPanel();
        rightPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(rightPanel, gbc);
        
        // Bottom panel removed
    }

    private JPanel createRegisterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Style for register labels
        Font labelFont = new Font("SansSerif", Font.BOLD, 12);
        
        // GPR Registers
        for (int i = 0; i < 4; i++) {
            int index = i; // Create a copy of i
            gbc.gridy = i;
            
            JLabel label = new JLabel("GPR " + i);
            label.setFont(labelFont);
            gbc.gridx = 0;
            gbc.weightx = 0.2;
            panel.add(label, gbc);
            
            gprTextFields[i] = createRegisterTextField();
            gbc.gridx = 1;
            gbc.weightx = 0.6;
            panel.add(gprTextFields[i], gbc);
            
            JButton button = createLoadButton(e -> loadRegisterValue("GPR", index));
            gbc.gridx = 2;
            gbc.weightx = 0.2;
            panel.add(button, gbc);
        }

        // Add some spacing between GPR and IXR
        gbc.gridy++;
        panel.add(Box.createVerticalStrut(10), gbc);

        // IXR Registers
        for (int i = 0; i < 3; i++) {
            int index = i; // Create a copy of i
            gbc.gridy = i + 5;
            
            JLabel label = new JLabel("IXR " + (i + 1));
            label.setFont(labelFont);
            gbc.gridx = 0;
            gbc.weightx = 0.2;
            panel.add(label, gbc);
            
            ixrTextFields[i] = createRegisterTextField();
            gbc.gridx = 1;
            gbc.weightx = 0.6;
            panel.add(ixrTextFields[i], gbc);
            
            JButton button = createLoadButton(e -> loadRegisterValue("IXR", index));
            gbc.gridx = 2;
            gbc.weightx = 0.2;
            panel.add(button, gbc);
        }

        // Add padding at the bottom
        gbc.gridy++;
        gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);
        
        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;

        // Internal Registers Panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        JPanel internalRegPanel = createInternalRegisterPanel();
        internalRegPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(0, 0, 10, 0),
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 150, 255), 2),
                "Internal Registers"
            )
        ));
        panel.add(internalRegPanel, gbc);

        // Binary/Octal Panel
        gbc.gridy = 1;
        gbc.insets = new Insets(10, 5, 10, 5);
        JPanel binaryOctalPanel = createBinaryOctalPanel();
        binaryOctalPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(0, 0, 10, 0),
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 150, 255), 2),
                "Data Input/Output"
            )
        ));
        panel.add(binaryOctalPanel, gbc);

        // Operation Buttons Panel
        gbc.gridy = 2;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.NORTH;
        JPanel buttonsPanel = createOperationButtonsPanel();
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        panel.add(buttonsPanel, gbc);

        return panel;
    }

    private JPanel createInternalRegisterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.gridx = 0; panel.add(new JLabel("PC"), gbc);
        gbc.gridx = 1; panel.add(pcTextField, gbc);
        gbc.gridx = 2; panel.add(createLoadButton(e -> loadRegisterValue("PC", 0)), gbc);
        gbc.gridy = 1;
        gbc.gridx = 0; panel.add(new JLabel("MAR"), gbc);
        gbc.gridx = 1; panel.add(marTextField, gbc);
        gbc.gridx = 2; panel.add(createLoadButton(e -> loadRegisterValue("MAR", 0)), gbc);
        gbc.gridy = 2;
        gbc.gridx = 0; panel.add(new JLabel("MBR"), gbc);
        gbc.gridx = 1; panel.add(mbrTextField, gbc);
        gbc.gridx = 2; panel.add(createLoadButton(e -> loadRegisterValue("MBR", 0)), gbc);
        gbc.gridy = 3;
        gbc.gridx = 0; panel.add(new JLabel("IR"), gbc);
        gbc.gridx = 1; panel.add(irTextField, gbc);
        // IR is not meant to be loaded
        gbc.gridx = 2; panel.add(createBlueButton()); 

        return panel;
    }

    private JPanel createBinaryOctalPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridy = 0; panel.add(new JLabel("BINARY"), gbc);
        gbc.gridy = 1; panel.add(binaryDisplayField, gbc);
        gbc.gridy = 2; panel.add(new JLabel("OCTAL INPUT"), gbc);
        gbc.gridy = 3; panel.add(octalInputField, gbc);
        octalInputField.setText("000000");
        binaryDisplayField.setEditable(false);
        binaryDisplayField.setFont(new Font("Monospaced", Font.PLAIN, 12));

        return panel;
    }

    private JPanel createOperationButtonsPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 4, 8, 8));
        panel.setBackground(new Color(200, 220, 240));
        
        // Define common button dimensions
        Dimension buttonSize = new Dimension(80, 30);
        Font buttonFont = new Font("SansSerif", Font.BOLD, 12);
        
        // Create and style each button
        JButton[] buttons = {
            createOperationButton("Load"),
            createOperationButton("Run"),
            createOperationButton("Halt", Color.RED),
            createOperationButton("Load+"),
            createOperationButton("Step"),
            createOperationButton("IPL", Color.RED),
            createOperationButton("Store"),
            createOperationButton("Store+"),
            // === NEW: Runtime text loader ===
            createOperationButton("Load Text")
        };
        
        for (JButton button : buttons) {
            button.setPreferredSize(buttonSize);
            button.setFont(buttonFont);
            button.setFocusPainted(false);
            button.setBorderPainted(true);
            button.setBackground(new Color(240, 240, 240));
            panel.add(button);
        }
        
        return panel;
    }

    private JTextField createRegisterTextField() {
        JTextField field = new JTextField(12);
        field.setFont(new Font("Monospaced", Font.PLAIN, 14));
        field.setHorizontalAlignment(JTextField.CENTER);
        field.setEditable(false);
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 150, 255)),
            BorderFactory.createEmptyBorder(2, 5, 2, 5)
        ));
        return field;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0; // Allow horizontal expansion

        // CC and MFR Panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        statusPanel.setBackground(new Color(200, 220, 240));
        
        // Style for CC and MFR
        Font statusFont = new Font("SansSerif", Font.BOLD, 12);
        ccTextField.setFont(new Font("Monospaced", Font.PLAIN, 14));
        mfrTextField.setFont(new Font("Monospaced", Font.PLAIN, 14));
        ccTextField.setPreferredSize(new Dimension(60, 25));
        mfrTextField.setPreferredSize(new Dimension(60, 25));
        
        JLabel ccLabel = new JLabel("CC");
        JLabel mfrLabel = new JLabel("MFR");
        ccLabel.setFont(statusFont);
        mfrLabel.setFont(statusFont);
        
        statusPanel.add(ccLabel);
        statusPanel.add(ccTextField);
        statusPanel.add(Box.createHorizontalStrut(15));
        statusPanel.add(mfrLabel);
        statusPanel.add(mfrTextField);
        
        gbc.gridy = 0;
        gbc.weighty = 0;
        gbc.insets = new Insets(5, 5, 5, 5);
        panel.add(statusPanel, gbc);

        // Cache Content
        cacheContentArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        cacheContentArea.setEditable(false);
        cacheContentArea.setBackground(new Color(250, 250, 250));
        JScrollPane cacheScrollPane = new JScrollPane(cacheContentArea);
        cacheScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(5, 0, 5, 0),
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 150, 255), 2),
                "Cache Content"
            )
        ));
        gbc.gridy = 1;
        gbc.weighty = 1.0;
        panel.add(cacheScrollPane, gbc);

        // Printer and Console Input
        JPanel ioPanel = new JPanel(new BorderLayout(0, 5));
        ioPanel.setBackground(new Color(200, 220, 240));
        
        // Printer Area
        printerArea.setEditable(false);
        printerArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        printerArea.setBackground(new Color(250, 250, 250));
        JScrollPane printerScrollPane = new JScrollPane(printerArea);
        printerScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(5, 0, 5, 0),
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 150, 255), 2),
                "Printer"
            )
        ));
        
        // Console Input
        consoleInputTextField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        consoleInputTextField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(0, 0, 5, 0),
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 150, 255), 2),
                "Console Input"
            )
        ));
        
        ioPanel.add(printerScrollPane, BorderLayout.CENTER);
        ioPanel.add(consoleInputTextField, BorderLayout.SOUTH);
        
        gbc.gridy = 2;
        gbc.weighty = 0.5;
        gbc.insets = new Insets(5, 0, 5, 0);
        panel.add(ioPanel, gbc);

        return panel;
    }

    /**
     * Appends a log message to the printer area with a newline.
     * Use this for simulator messages, not for the OUT instruction.
     * @param text The log message.
     */
    public void appendToPrinter(String text) {
        // Ensure Swing updates happen on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            // Ensure simulator log lines appear on a new line, even if the last
            // printed OUT character did not include a newline.
            try {
                int len = printerArea.getDocument().getLength();
                if (len > 0) {
                    String last = printerArea.getText(len - 1, 1);
                    if (!"\n".equals(last)) {
                        printerArea.append("\n");
                    }
                }
            } catch (Exception ignored) { /* safe to ignore */ }
            printerArea.append(text + "\n");
            printerArea.setCaretPosition(printerArea.getDocument().getLength());
        });
    }
    
    /**
     * === NEW: Appends text to the printer area *without* a newline. ===
     * This is for the OUT instruction to use.
     * @param text The character or string to print.
     */
    public void printToConsole(String text) {
        // OUT instruction may be invoked from a background thread.
        // Update the Swing component on the EDT and make non-printable
        // characters visible by rendering them as octal in angle brackets.
        SwingUtilities.invokeLater(() -> {
            // Record raw console output for headless testing
            consoleOut.append(text);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c >= 32 && c <= 126) {
                    sb.append(c);
                } else if (c == '\n' || c == '\r' || c == '\t') {
                    sb.append(c);
                } else {
                    sb.append(String.format("<%03o>", (int) c));
                }
            }
            // Append to the visible printer area without forcing a newline; this
            // keeps multi-digit or negative numbers on a single line. Simulator
            // log lines (via appendToPrinter) will insert a newline first if needed.
            printerArea.append(sb.toString());
            printerArea.setCaretPosition(printerArea.getDocument().getLength());
        });
    }

    /**
     * === NEW: Clears the printer area. ===
     */
    public void clearPrinter() {
        SwingUtilities.invokeLater(() -> printerArea.setText(""));
        consoleOut.setLength(0);
    }

    // === Added: Test harness helpers ===
    /**
     * Expose the CPU instance for headless testing.
     */
    public CPU getCpu() {
        return this.cpu;
    }

    /**
     * Get the current content of the printer area as plain text.
     * Note: This returns the Swing text content, which may update asynchronously.
     */
    public String getPrinterText() {
        return printerArea.getText();
    }

    /**
     * Get only the characters printed by OUT (raw, no extra logs).
     */
    public String getConsoleOut() {
        return consoleOut.toString();
    }

    private void addListeners() {
        // Octal Input field listener
        octalInputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                updateBinaryDisplayFromOctalInput();
            }
        });
        
        // === NEW: Console Input field listener ===
        // Triggers when the user presses Enter in the console input box
        consoleInputTextField.addActionListener(e -> {
            String text = consoleInputTextField.getText();
            if (!text.isEmpty()) {
                // Send the input text to the CPU, appending a newline so
                // TRAP-based routines can detect end-of-input. Newline also
                // serves as a delimiter for numeric input flows.
                cpu.submitConsoleInput(text + "\n");
                // Clear the input field
                consoleInputTextField.setText("");
            }
        });
        // =========================================
    }

    private void updateBinaryDisplayFromOctalInput() {
        try {
            short value = cpu.getUtils().octalToShort(octalInputField.getText());
            binaryDisplayField.setText(cpu.getUtils().shortToBinary(value, 16));
        } catch (NumberFormatException ex) {
            binaryDisplayField.setText("Invalid Octal Input");
        }
    }

    private JButton createOperationButton(String text, Color... color) {
        JButton button = new JButton(text);
        if (color.length > 0) {
            button.setForeground(color[0]);
            button.setFont(new Font("SansSerif", Font.BOLD, 12));
        }
        button.addActionListener(e -> handleButtonPress(text));
        return button;
    }

    public void handleButtonPress(String command) {
        try {
            System.out.println("\n=== Button Press: " + command + " ===");
            
            // Only parse octal value if needed
            short octalValue = 0;
            if (command.equals("Load") || command.equals("Load+") || command.equals("Store") || command.equals("Store+")) {
                 octalValue = cpu.getUtils().octalToShort(octalInputField.getText());
                 System.out.printf("Octal Input Value: %06o\n", octalValue);
            }
            
            switch (command) {
                case "Load" -> {
                    System.out.println("Executing LOAD operation");
                    cpu.load(octalValue);
                }
                case "Load+" -> {
                    System.out.println("Executing LOAD+ operation");
                    cpu.loadPlus(octalValue);
                }
                case "Store" -> {
                    System.out.println("Executing STORE operation");
                    cpu.store(octalValue);
                }
                case "Store+" -> {
                    System.out.println("Executing STORE+ operation");
                    cpu.storePlus(octalValue);
                }
                case "Run" -> {
                    System.out.println("Starting program execution");
                    cpu.runProgram();
                }
                case "Step" -> {
                    System.out.println("Executing single instruction step");
                    cpu.singleStep();
                }
                case "Halt" -> {
                    System.out.println("Halting machine execution");
                    cpu.halt();
                }
                case "IPL" -> {
                    System.out.println("Initializing machine (IPL)");
                    loadProgramFromFile();
                }
                case "Load Text" -> {
                    System.out.println("Runtime load: selecting text file to write into memory");
                    loadTextIntoMemoryFromFile();
                }
            }
        } catch (NumberFormatException ex) {
            System.out.println("ERROR: Invalid octal input - " + octalInputField.getText());
            showError("Invalid Octal Input", "Please enter a valid octal string (0-7, up to 6 digits).");
        }
    }

    private void loadProgramFromFile() {
        JFileChooser fileChooser = new JFileChooser(".");
        fileChooser.setDialogTitle("Select Program File for IPL");
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File programFile = fileChooser.getSelectedFile();
            cpu.ipl(programFile); // Pass the selected file to the CPU's IPL process
            // After IPL, attempt to auto-detect a paragraph/text buffer in memory
            try {
                Integer detected = detectParagraphBufferAddress();
                if (detected != null) {
                    octalInputField.setText(cpu.getUtils().shortToOctal((short)(detected & 0xFFFF), 6));
                    appendToPrinter(String.format("Auto-detected probable paragraph start at %04o", detected));
                } else {
                    appendToPrinter("No paragraph buffer auto-detected after IPL.");
                }
            } catch (Exception ex) {
                appendToPrinter("Paragraph detection failed: " + ex.getMessage());
            }
        }
    }

    /**
     * Heuristic: scan memory for a run of printable characters (letters, digits, space, punctuation)
     * of minimum length and return the starting address. Returns null if none found.
     */
    private Integer detectParagraphBufferAddress() {
        final int MIN_RUN = 10; // minimum consecutive printable chars
        final int MEM_LIMIT = 2048;
        for (int addr = 0; addr < MEM_LIMIT; addr++) {
            int run = 0;
            for (int a = addr; a < MEM_LIMIT; a++) {
                short val = cpu.peekMemory((short)a);
                int ch = val & 0xFF;
                boolean printable = (ch >= 32 && ch <= 126) || ch == 10 || ch == 13 || ch == 9 || ch == 46 || ch == 63; // include '.' and '?'
                if (printable && ch != 0) { run++; } else { break; }
            }
            if (run >= MIN_RUN) return addr;
            // skip ahead to end of this non-printable run to speed up
        }
        return null;
    }

    // === NEW: Runtime text loader using octal input as start address ===
    private void loadTextIntoMemoryFromFile() {
        try {
            String oct = octalInputField.getText();
            if (oct == null || oct.trim().isEmpty()) {
                showError("Start Address Required", "Enter start address (octal) in OCTAL INPUT before loading text.");
                return;
            }
            int startAddr = cpu.getUtils().octalToShort(oct) & 0xFFFF;

            JFileChooser fc = new JFileChooser(".");
            fc.setDialogTitle("Select Text File to Load into Memory");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                int written = cpu.loadTextIntoMemory(file, startAddr);
                // Set R0=start address and R1=length for TRAP services
                cpu.setGPR(0, (short)startAddr);
                cpu.setGPR(1, (short)written);
                appendToPrinter(String.format("Loaded %d chars from '%s' into memory[%04o], sentinel at %04o", 
                    written, file.getName(), startAddr, Math.min(startAddr + written, 2047)));
                updateAllDisplays();
            }
        } catch (Exception ex) {
            showError("Text Load Error", "Failed to load text: " + ex.getMessage());
        }
    }

    public void updateAllDisplays() {
        // === MODIFIED: Call updateCacheView ===
        updateRegisters();
        updateCacheView();
        // ======================================
    }

    public void updateRegisters() {
        for (int i = 0; i < 4; i++) gprTextFields[i].setText(cpu.getUtils().shortToOctal(cpu.getGPR(i), 6));
        for (int i = 0; i < 3; i++) ixrTextFields[i].setText(cpu.getUtils().shortToOctal(cpu.getIXR(i + 1), 6));
        pcTextField.setText(cpu.getUtils().shortToOctal(cpu.getPC(), 4));
        marTextField.setText(cpu.getUtils().shortToOctal(cpu.getMAR(), 4));
        mbrTextField.setText(cpu.getUtils().shortToOctal(cpu.getMBR(), 6));
        irTextField.setText(cpu.getUtils().shortToOctal(cpu.getIR(), 6));
        ccTextField.setText(cpu.getUtils().shortToBinary(cpu.getCC(), 4));
        mfrTextField.setText(cpu.getUtils().shortToBinary(cpu.getMFR(), 4));
    }

    /**
     * === NEW: Renamed and implemented this method ===
     * Updates the Cache Content display area.
     */
    public void updateCacheView() {
        cacheContentArea.setText(cpu.getFormattedCache());
        cacheContentArea.setCaretPosition(0); // Scroll to top
    }

    // NOTE: the auto-load-and-run feature was intentionally removed. If you
    // want batch testing or auto-run behavior in the future, we can re-add a
    // small, explicit toggle or an "Auto IPL" configuration file. For now the
    // user must click IPL and select a load file to IPL the simulator.
    // ===========================================

    public void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private JButton createLoadButton(ActionListener listener) {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(20, 20));
        button.setBackground(new Color(100, 150, 255));
        button.setToolTipText("Load value from Octal Input");
        button.addActionListener(listener);
        return button;
    }

 private void loadRegisterValue(String registerName, int index) {
        try {
            String octalString = octalInputField.getText();
            if (octalString == null || octalString.trim().isEmpty()) {
                showError("Input Error", "Octal Input field cannot be empty.");
                return;
            }
            short value = cpu.getUtils().octalToShort(octalString);

            System.out.printf("Loading value %06o into %s%s\n", value, registerName, (index >= 0 ? " " + index : ""));

            switch (registerName) {
                case "GPR" -> cpu.setGPR(index, value);
                case "IXR" -> cpu.setIXR(index + 1, value); // IXRs are 1-based in CPU
                case "PC" -> cpu.setPC(value);
                case "MAR" -> cpu.setMAR(value);
                case "MBR" -> cpu.setMBR(value);
                // case "IR" -> cpu.setIR(value); // Don't allow loading IR
            }

            updateAllDisplays();

        } catch (NumberFormatException ex) {
            showError("Invalid Octal Input", "Please enter a valid octal string.");
        }
    }

    private JButton createBlueButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(20, 20));
        button.setBackground(new Color(100, 150, 255));
        button.setEnabled(false);
        return button;
    }

}