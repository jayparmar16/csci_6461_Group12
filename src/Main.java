public class Main {
    public static void main(String[] args) {

        String filePath = "source.txt";
        System.out.println("Assembling file: " + filePath);

        Assembler assembler = new Assembler();
        assembler.assemble(filePath);
    }
}
