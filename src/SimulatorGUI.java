import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SimulatorGUI {

    private JFrame frame;
    private CPU cpu;

    // Register display fields
    private JTextField[] gprFields = new JTextField[4];
    private JTextField[] ixrFields = new JTextField[3];
    private JTextField pcField, marField, mbrField, irField, mfrField, ccField;

    // Control buttons and indicators
    private JButton btnIPL, btnRun, btnStep;
    private JPanel haltIndicator;
    private JTextArea consolePrinter;
    private JTextField consoleKeyboard;
    private JTable memoryTable;
    private SwingWorker<Void, Void> runWorker;

    public SimulatorGUI(CPU cpu) {
        this.cpu = cpu;
        initialize();
    }

    private void initialize() {
        frame = new JFrame("CSCI-6461 Simulator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout(5, 5));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(createControlPanel());
        topPanel.add(createRegisterPanel());

        frame.getContentPane().add(topPanel, BorderLayout.NORTH);
        frame.getContentPane().add(createMemoryPanel(), BorderLayout.CENTER);
        frame.getContentPane().add(createConsolePanel(), BorderLayout.SOUTH);

        addListeners();
        cpu.setConsolePrinter(text -> consolePrinter.append(text + "\n"));
        updateAllDisplays();
        frame.pack();
        frame.setVisible(true);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Controls"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);

        btnIPL = new JButton("IPL");
        btnRun = new JButton("Run");
        btnStep = new JButton("Single Step");
        haltIndicator = new JPanel();
        haltIndicator.setBackground(Color.RED);
        haltIndicator.setPreferredSize(new Dimension(20, 20));

        gbc.gridwidth = 2;
        panel.add(btnIPL, gbc);
        gbc.gridy = 1;
        panel.add(btnRun, gbc);
        gbc.gridy = 2;
        panel.add(btnStep, gbc);
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Halt"), gbc);
        gbc.gridx = 1;
        panel.add(haltIndicator, gbc);

        return panel;
    }

    private JPanel createMemoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Memory"));
        String[] columnNames = {"Address", "Value"};
        Object[][] data = new Object[2048][2];
        for (int i = 0; i < 2048; i++) {
            data[i][0] = i;
            data[i][1] = 0;
        }
        memoryTable = new JTable(data, columnNames);
        JScrollPane scrollPane = new JScrollPane(memoryTable);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Console"));

        consolePrinter = new JTextArea(5, 40);
        consolePrinter.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(consolePrinter);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        inputPanel.add(new JLabel("Input:"));
        consoleKeyboard = new JTextField(20);
        inputPanel.add(consoleKeyboard);
        panel.add(inputPanel, BorderLayout.SOUTH);

        consoleKeyboard.addActionListener(e -> {
            try {
                int value = Integer.parseInt(consoleKeyboard.getText().trim());
                cpu.setKeyboardBuffer(value);
                consoleKeyboard.setText("");
                // If waiting for input, resume execution
                if (runWorker != null && runWorker.isDone()) {
                    btnRun.doClick();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Invalid input. Please enter a number.");
            }
        });

        return panel;
    }

    private JPanel createRegisterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Registers"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);

        // GPRs
        for (int i = 0; i < 4; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            panel.add(new JLabel("GPR" + i), gbc);
            gprFields[i] = new JTextField(6);
            gprFields[i].setEditable(false);
            gbc.gridx = 1;
            panel.add(gprFields[i], gbc);
        }

        // IXRs
        for (int i = 0; i < 3; i++) {
            gbc.gridx = 2;
            gbc.gridy = i;
            panel.add(new JLabel("IXR" + (i + 1)), gbc);
            ixrFields[i] = new JTextField(6);
            ixrFields[i].setEditable(false);
            gbc.gridx = 3;
            panel.add(ixrFields[i], gbc);
        }

        // Other Registers
        pcField = new JTextField(6);
        marField = new JTextField(6);
        mbrField = new JTextField(6);
        irField = new JTextField(6);
        mfrField = new JTextField(4);
        ccField = new JTextField(4);

        addRegisterToPanel(panel, "PC", pcField, 0, 4);
        addRegisterToPanel(panel, "MAR", marField, 0, 5);
        addRegisterToPanel(panel, "MBR", mbrField, 0, 6);
        addRegisterToPanel(panel, "IR", irField, 2, 4);
        addRegisterToPanel(panel, "MFR", mfrField, 2, 5);
        addRegisterToPanel(panel, "CC", ccField, 2, 6);

        return panel;
    }

    private void addRegisterToPanel(JPanel panel, String label, JTextField field, int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.gridx = x;
        gbc.gridy = y;
        panel.add(new JLabel(label), gbc);
        field.setEditable(false);
        gbc.gridx = x + 1;
        panel.add(field, gbc);
    }

    private void addListeners() {
        btnIPL.addActionListener(e -> {
            cpu.reset();
            // Load a program that uses IN and OUT
            // OUT R1 -> 011010 01 00 0 00000 -> 0x1A40
            // IN R1 -> 011001 01 00 0 00000 -> 0x1940
            cpu.setMemory(10, 0x1940); // IN R1
            cpu.setMemory(11, 0x1A40); // OUT R1
            cpu.setMemory(12, 0);      // HLT
            cpu.setPC(10);
            updateAllDisplays();
            consolePrinter.setText("");
        });

        btnStep.addActionListener(e -> {
            if (cpu.getIR() != 0 && cpu.getMFR() == 0) {
                cpu.instructionCycle();
                updateAllDisplays();
            }
        });

        btnRun.addActionListener(e -> {
            btnRun.setEnabled(false);
            btnStep.setEnabled(false);
            runWorker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    while (cpu.getIR() != 0 && cpu.getMFR() == 0) {
                        cpu.instructionCycle();
                        publish(); // Triggers process() to update GUI
                        Thread.sleep(100); // Slow down for visualization
                    }
                    return null;
                }

                @Override
                protected void process(java.util.List<Void> chunks) {
                    updateAllDisplays();
                }

                @Override
                protected void done() {
                    updateAllDisplays();
                    btnRun.setEnabled(true);
                    btnStep.setEnabled(true);
                }
            };
            runWorker.execute();
        });
    }

    private void updateAllDisplays() {
        updateRegisterPanels();
        updateMemoryDisplay();
        haltIndicator.setBackground(cpu.getIR() == 0 || cpu.getMFR() != 0 ? Color.RED : Color.GREEN);
    }

    private void updateRegisterPanels() {
        for (int i = 0; i < 4; i++) gprFields[i].setText(String.valueOf(cpu.getGPR(i)));
        for (int i = 0; i < 3; i++) ixrFields[i].setText(String.valueOf(cpu.getIXR(i)));
        pcField.setText(String.valueOf(cpu.getPC()));
        marField.setText(String.valueOf(cpu.getMAR()));
        mbrField.setText(String.valueOf(cpu.getMBR()));
        irField.setText(String.valueOf(cpu.getIR()));
        mfrField.setText(String.valueOf(cpu.getMFR()));
        ccField.setText(String.valueOf(cpu.getCC()));
    }

    private void updateMemoryDisplay() {
        for (int i = 0; i < 2048; i++) {
            memoryTable.setValueAt(cpu.getMemory(i), i, 1);
        }
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                CPU cpu = new CPU();
                new SimulatorGUI(cpu);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}