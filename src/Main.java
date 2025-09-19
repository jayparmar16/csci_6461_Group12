import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Assembler Source File");
        fileChooser.setCurrentDirectory(new File(".")); // Start in current directory
        fileChooser.setFileFilter(new FileNameExtensionFilter("Text Files", "txt"));

        int result = fileChooser.showOpenDialog(frame);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            System.out.println("Selected file: " + selectedFile.getAbsolutePath());

            Assembler assembler = new Assembler();
            assembler.assemble(selectedFile.getAbsolutePath());
        } else {
            System.out.println("No file selected. Exiting.");
        }

        frame.dispose(); // Close the hidden frame
    }
}