public class AsmMain {
    public static void main(String[] args) {
        String file = args.length > 0 ? args[0] : "closest_20_compact.asm";
        Assembler asm = new Assembler();
        asm.run(file);
        System.out.println("Assembly complete for: " + file);
    }
}
