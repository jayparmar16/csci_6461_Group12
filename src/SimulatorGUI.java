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

    // Control buttons
    private JButton btnIPL, btnRun, btnStep;
    private JTable memoryTable;

    public SimulatorGUI(CPU cpu) {
        this.cpu = cpu;
        initialize();
    }

    private void initialize() {
        frame = new JFrame("CSCI-6461 Simulator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());

        // Create and add panels
        JPanel topPanel = new JPanel(new FlowLayout());
        topPanel.add(createControlPanel());
        topPanel.add(createRegisterPanel());

        frame.getContentPane().add(topPanel, BorderLayout.NORTH);
        frame.getContentPane().add(createMemoryPanel(), BorderLayout.CENTER);

        addListeners();
        updateAllDisplays();
        frame.pack();
        frame.setVisible(true);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.setBorder(BorderFactory.createTitledBorder("Controls"));
        btnIPL = new JButton("IPL");
        btnRun = new JButton("Run");
        btnStep = new JButton("Single Step");
        panel.add(btnIPL);
        panel.add(btnRun);
        panel.add(btnStep);
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
            // Load a simple program for testing
            // LDR 1,0,5 -> 000001 01 00 0 00101 -> 0x445
            cpu.reset();
            cpu.setMemory(10, 0b0000010100000101); // LDR R1, 0, 5
            cpu.setMemory(5, 123); // Value to be loaded
            // HLT -> 0
            cpu.setMemory(11, 0b0000000000000000);
            cpu.setPC(10);
            updateAllDisplays();
        });

        btnStep.addActionListener(e -> {
            cpu.instructionCycle();
            updateAllDisplays();
        });

        btnRun.addActionListener(e -> {
            // Simple run loop, will be improved with a SwingWorker later
            while (cpu.getIR() != 0) {
                cpu.instructionCycle();
            }
            updateAllDisplays();
        });
    }

    private void updateAllDisplays() {
        updateRegisterPanels();
        updateMemoryDisplay();
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