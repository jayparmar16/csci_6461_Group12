import java.io.*;

public class AsmDriver {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java AsmDriver <assembly-file>");
            System.exit(2);
        }
        String src = args[0];
        Assembler asm = new Assembler();
        asm.run(src);
        System.out.println("Assembly complete. Check load_file.txt in the same folder as the source.");
    }
}
