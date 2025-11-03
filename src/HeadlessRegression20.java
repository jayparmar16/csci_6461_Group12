import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HeadlessRegression20 {
    static class TestCase {
        final String name;
        final String input; // TARGET first, then 20 candidates
        final String expect;
        TestCase(String name, String input, String expect) {
            this.name = name; this.input = input; this.expect = expect;
        }
    }

    public static void main(String[] args) {
        try {
            // Assemble fixed-20 ASM
            String[] candidates = {
                "..\\closest_20_stream.asm",
                ".\\closest_20_stream.asm"
            };
            File asmFile = null;
            for (String c : candidates) {
                File f = new File(c);
                if (f.exists()) { asmFile = f.getCanonicalFile(); break; }
            }
            if (asmFile == null) {
                System.err.println("Assembly file closest_20_stream.asm not found (tried ../ and ./).");
                System.exit(2);
                return;
            }

            new Assembler().run(asmFile.getPath());
            File loadFile = new File(asmFile.getParentFile(), "load_file.txt");
            if (!loadFile.exists()) {
                System.err.println("load_file.txt not generated alongside " + asmFile);
                System.exit(2);
                return;
            }

            SimulatorGUI gui = new SimulatorGUI();
            CPU cpu = gui.getCpu();

            List<TestCase> tests = new ArrayList<>();
            // Input order: TARGET first, then 20 candidates
            tests.add(new TestCase("All zeros -> 0",
                "0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0", "0"));
            tests.add(new TestCase("Target 4, has 3 and 5 -> 3 (first closest)",
                "4 9 9 9 9 9 9 9 9 9 9 3 5 9 9 9 9 9 9 9 9", "3"));
            tests.add(new TestCase("Target -4, has -3 and -5 -> -3",
                "-4 9 9 9 9 9 9 9 9 9 9 -3 -5 9 9 9 9 9 9 9 9", "-3"));
            tests.add(new TestCase("Large: target 31999, has 32000",
                "31999 1 2 3 4 5 6 7 8 9 10 32000 11 12 13 14 15 16 17 18 19", "32000"));
            tests.add(new TestCase("Large negative: target -101, has -100",
                "-101 0 1 2 3 4 5 6 7 8 9 -100 11 12 13 14 15 16 17 18 19", "-100"));
            tests.add(new TestCase("Large max: target 20000, has 32767",
                "20000 0 1 2 3 4 5 6 32767 8 9 10 11 12 13 14 15 16 17 18 19", "32767"));
            tests.add(new TestCase("Large negative near min: target -30000, has -32767",
                "-30000 -32767 -5 -4 -3 -2 -1 0 1 2 3 4 5 6 7 8 9 10 11 12 13", "-32767"));

            int passed = 0, failed = 0;
            for (TestCase t : tests) {
                boolean ok = runCase(cpu, gui, loadFile, t.input, t.expect, 10_000);
                if (ok) {
                    System.out.println("PASS: " + t.name);
                    passed++;
                } else {
                    System.out.println("FAIL: " + t.name + "  (got='" + gui.getConsoleOut() + "', expect='" + t.expect + "')");
                    String logs = gui.getPrinterText();
                    if (logs != null && !logs.isEmpty()) {
                        System.out.println("--- Printer Log (last 500 chars) ---");
                        if (logs.length() > 500) logs = logs.substring(logs.length() - 500);
                        System.out.println(logs);
                        System.out.println("------------------------------------");
                    }
                    if (failed == 0) {
                        System.out.println("[DEBUG] Tracing memory for failing fixed-20 case: " + t.name);
                        trace20(cpu, gui, loadFile, t.input, 5_000);
                        // Also perform a stepwise token feed to observe when TARGET/WINNER/MIN_DIFF change
                        System.out.println("[DEBUG] Stepwise feed for failing fixed-20 case: " + t.name);
                        stepwise20(cpu, gui, loadFile, t.input);
                    }
                    failed++;
                }
            }
            System.out.println("\nSummary: " + passed + " passed, " + failed + " failed");
            if (failed > 0) System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static void trace20(CPU cpu, SimulatorGUI gui, File loadFile, String input, long timeoutMs) throws InterruptedException {
        cpu.resetMachine();
        gui.clearPrinter();
        cpu.ipl(loadFile);
        cpu.submitConsoleInput(input + " ");
        long deadline = System.currentTimeMillis() + timeoutMs;
        cpu.runProgram();

    int minDiff = Integer.parseInt("362", 8);
        int winner = Integer.parseInt("363", 8);
        int cnt = Integer.parseInt("364", 8);
        int target = Integer.parseInt("365", 8);
    int readbuf = Integer.parseInt("366", 8);
        int printTmp = Integer.parseInt("367", 8);
        int readval = Integer.parseInt("371", 8);

        short lastCnt = Short.MIN_VALUE;
        List<String> samples = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(1);
            short c = cpu.peekMemory((short) cnt);
            if (c != lastCnt) {
                lastCnt = c;
                short t = cpu.peekMemory((short) target);
                short w = cpu.peekMemory((short) winner);
                short md = cpu.peekMemory((short) minDiff);
                short rv = cpu.peekMemory((short) readval);
                samples.add(String.format("t=%dms CNT=%d TARGET=%d WINNER=%d MIN_DIFF=%d READVAL=%d OUT='%s' waiting=%s",
                    (timeoutMs - (deadline - System.currentTimeMillis())), c, t, w, md, rv, gui.getConsoleOut(), cpu.isWaitingForInput()));
                if (c == 0) break;
            }
        }
        System.out.println("[DEBUG] Samples:");
        for (String s : samples) System.out.println("  " + s);
        // Final dump of key slots
        System.out.println(String.format("[DEBUG] FINAL: TARGET=%d WINNER=%d MIN_DIFF=%d READVAL=%d READBUF=%d PRINT_TMP=%d",
            cpu.peekMemory((short) target), cpu.peekMemory((short) winner), cpu.peekMemory((short) minDiff), cpu.peekMemory((short) readval), cpu.peekMemory((short) readbuf), cpu.peekMemory((short) printTmp)));
    }

    private static void stepwise20(CPU cpu, SimulatorGUI gui, File loadFile, String input) throws InterruptedException {
        String[] tokens = input.trim().split("\\s+");
        cpu.resetMachine();
        gui.clearPrinter();
        cpu.ipl(loadFile);
        cpu.runProgram();

        int minDiff = Integer.parseInt("362", 8);
        int winner = Integer.parseInt("363", 8);
        int cnt = Integer.parseInt("364", 8);
        int target = Integer.parseInt("365", 8);

        for (int i = 0; i < tokens.length; i++) {
            cpu.submitConsoleInput(tokens[i] + " ");
            Thread.sleep(15);
            short cntV = cpu.peekMemory((short) cnt);
            short tgtV = cpu.peekMemory((short) target);
            short winV = cpu.peekMemory((short) winner);
            short mdV = cpu.peekMemory((short) minDiff);
            System.out.println(String.format("  [step %d token='%s'] CNT=%d TARGET=%d WINNER=%d MIN_DIFF=%d OUT='%s'", i, tokens[i], cntV, tgtV, winV, mdV, gui.getConsoleOut()));
        }
    }

    private static boolean runCase(CPU cpu, SimulatorGUI gui, File loadFile, String input, String expect, long timeoutMs) throws InterruptedException {
        cpu.resetMachine();
        gui.clearPrinter();
        cpu.ipl(loadFile);
        cpu.submitConsoleInput(input + " ");
        long deadline = System.currentTimeMillis() + timeoutMs;
        cpu.runProgram();
        String last = gui.getConsoleOut();
        int stable = 0;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(1);
            String out = gui.getConsoleOut();
            if (out.equals(expect)) return true;
            if (out.equals(last)) {
                stable++;
                if (stable > 3000) break;
            } else {
                last = out;
                stable = 0;
            }
        }
        return gui.getConsoleOut().equals(expect);
    }
}
