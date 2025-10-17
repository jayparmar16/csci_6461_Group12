import javax.swing.*;
import java.awt.*;

public class SimulatorGUI {

    private JFrame frame;
    private CPU cpu;

    // Register display fields
    private JTextField[] gprFields = new JTextField[4];
    private JTextField[] ixrFields = new JTextField[3];
    private JTextField pcField, marField, mbrField, irField, mfrField, ccField;

    public SimulatorGUI(CPU cpu) {
        this.cpu = cpu;
        initialize();
    }

    private void initialize() {
        frame = new JFrame("CSCI-6461 Simulator");
        frame.setBounds(100, 100, 800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Create and add panels
        frame.getContentPane().add(createRegisterPanel(), gbc);

        frame.setVisible(true);
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