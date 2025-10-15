import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * The main Graphical User Interface for the CSCI 6461 Machine Simulator.
 * This class builds the window, panels, and all the interactive components,
 * and it communicates with the CPU to run the simulation.
 */
public class SimulatorGUI extends JFrame {

    private final CPU cpu;

    // GUI Components
    private final JTextField[] gprTextFields = new JTextField[4];
    private final JTextField[] ixrTextFields = new JTextField[3];
    private final JTextField pcTextField = new JTextField(8);
    private final JTextField marTextField = new JTextField(8);
    private final JTextField mbrTextField = new JTextField(8);
    private final JTextField irTextField = new JTextField(8);
    private final JTextField ccTextField = new JTextField(4);
    private final JTextField mfrTextField = new JTextField(4);
    private final JTextField binaryDisplayField = new JTextField(20);
    private final JTextField octalInputField = new JTextField(8);
    private final JTextArea cacheContentArea = new JTextArea(25, 40);
    private final JTextArea printerArea = new JTextArea(10, 35);
    private final JTextField consoleInputTextField = new JTextField(35);
    private final JTextField programFileTextField = new JTextField(25);

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
        cpu.ipl(); // Perform initial reset on startup
    }

    private void setupComponents() {
        setPreferredSize(new Dimension(800, 600));
        getContentPane().setBackground(new Color(200, 220, 240));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;
        
        // Left Panel (GPRs, IXRs)
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0.25;
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
        gbc.weightx = 0.35;
        JPanel centerPanel = createCenterPanel();
        centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(centerPanel, gbc);

        // Right Panel (Cache, Printer, Console)
        gbc.gridx = 2;
        gbc.weightx = 0.4;
        gbc.gridheight = 3;
        JPanel rightPanel = createRightPanel();
        rightPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(rightPanel, gbc);

        // Bottom Panel (Program File)
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.gridheight = 1;
        gbc.weightx = 0.6;
        gbc.weighty = 0.1;
        add(createBottomPanel(), gbc);
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
            
            JButton button = createBlueButton();
            gbc.gridx = 2;
            gbc.weightx = 0.2;
            panel.add(button, gbc);
        }

        // Add some spacing between GPR and IXR
        gbc.gridy++;
        panel.add(Box.createVerticalStrut(10), gbc);

        // IXR Registers
        for (int i = 0; i < 3; i++) {
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
            
            JButton button = createBlueButton();
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

        gbc.gridy = 0; panel.add(new JLabel("PC"), gbc); panel.add(pcTextField, gbc); panel.add(createBlueButton(), gbc);
        gbc.gridy = 1; panel.add(new JLabel("MAR"), gbc); panel.add(marTextField, gbc); panel.add(createBlueButton(), gbc);
        gbc.gridy = 2; panel.add(new JLabel("MBR"), gbc); panel.add(mbrTextField, gbc); panel.add(createBlueButton(), gbc);
        gbc.gridy = 3; panel.add(new JLabel("IR"), gbc); panel.add(irTextField, gbc); panel.add(createBlueButton(), gbc);

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
        octalInputField.setText("0");
        binaryDisplayField.setEditable(false);
        binaryDisplayField.setFont(new Font("Monospaced", Font.PLAIN, 12));

        return panel;
    }

    private JPanel createOperationButtonsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 4, 8, 8));
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
            createOperationButton("Store+")
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
        JTextField field = new JTextField(8);
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
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;
        
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
        panel.add(ioPanel, gbc);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("Program File"));
        panel.add(programFileTextField);
        JButton loadButton = new JButton("Select File");
        loadButton.addActionListener(e -> assembleFile());
        panel.add(loadButton);
        programFileTextField.setEditable(false);
        return panel;
    }

    private void addListeners() {
        octalInputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                updateBinaryDisplayFromOctalInput();
            }
        });
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
            short octalValue = cpu.getUtils().octalToShort(octalInputField.getText());
            System.out.printf("Octal Input Value: %06o\n", octalValue);
            
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
                    cpu.ipl();
                }
            }
        } catch (NumberFormatException ex) {
            System.out.println("ERROR: Invalid octal input - " + octalInputField.getText());
            showError("Invalid Octal Input", "Please enter a valid octal string (0-7, up to 6 digits).");
        }
    }

    private void assembleFile() {
        JFileChooser fileChooser = new JFileChooser(".");
        fileChooser.setDialogTitle("Select Load File");
        fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File loadFile = fileChooser.getSelectedFile();
            programFileTextField.setText(loadFile.getAbsolutePath());
            System.out.println("\n=== Selected Program File ===");
            System.out.println("File: " + loadFile.getName());
            System.out.println("Path: " + loadFile.getAbsolutePath());
            
            // Load the program immediately
            cpu.loadProgram(loadFile.getAbsolutePath());
            updateAllDisplays(); // Update GUI to show new register states
        }
    }
    
    public String getProgramFileName() {
        return programFileTextField.getText();
    }

    public void updateAllDisplays() {
        updateRegisters();
        updateMemoryView();
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

    public void updateMemoryView() {
        cacheContentArea.setText(cpu.getFormattedMemory());
    }

    public void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }



    private JButton createBlueButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(20, 20));
        button.setBackground(new Color(100, 150, 255));
        button.setEnabled(false);
        return button;
    }
}