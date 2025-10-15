import javax.swing.SwingUtilities;

/**
 * The main entry point for the CSCI 6461 Simulator and Assembler application.
 */
public class Main {
    public static void main(String[] args) {
        // Use SwingUtilities.invokeLater to ensure the GUI is created on the Event Dispatch Thread,
        // which is the standard and safest way to start a Swing application.
        SwingUtilities.invokeLater(() -> {
            SimulatorGUI simulator = new SimulatorGUI();
            simulator.setVisible(true);
        });
    }
}