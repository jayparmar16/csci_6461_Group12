public class AsmBuild {
    public static void main(String[] args) {
        String fname = args != null && args.length > 0 ? args[0] : "closest_n_unrolled.asm";
        Assembler asm = new Assembler();
        asm.run(fname);
        System.out.println("Assembled " + fname + " -> listing_output.txt, load_file.txt");
    }
}
