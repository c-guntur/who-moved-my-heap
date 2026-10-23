package tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs the stages of instructions.md (section P), all of them or one at a time.
 *
 * Usage (Java 25, from the project root):
 *   java src/main/java/tools/Pipeline.java <stage> [RUN_TS] [shakers|movers] [--dry-run] [--resume] [--slides-dir=<folder>]
 * Stages:
 *   run      build the benchmark jar and run the benchmarks into generated/runs/<RUN_TS>/<part>/raw/
 *            (a new RUN_TS unless one is given; a given one reuses its meta.txt)
 *   reports  write the reports, charts, Mermaid files and summaries from the raw files of RUN_TS
 *            (default: the newest run folder)
 *   slides   build the slide HTML pages and the talk PDF into assets/presentation (mvn -Pslides process-resources);
 *            RUN_TS and part are ignored. --slides-dir=<folder> takes the slide sources from another folder
 *            (relative to the project root, default assets/docs)
 *   all      run, then reports, then slides
 * Without a part, a stage does shakers and movers. --dry-run prints the commands without running them.
 * --resume (needs a RUN_TS) continues an interrupted run: the jar, the benchmark and object-count steps and the
 * movers tests that already finished are skipped, the rest run again (instructions.md P7).
 * Environment variables passed through: CLASSES (shakers classes to run and report), MMDC (Mermaid check),
 * CHROME (browser for the slide PDFs).
 */
public class Pipeline
{
    private static final List<String> PARTS = List.of("shakers", "movers");
    private static final List<String> SHAKERS = List.of("AnyMatchUtility", "CountUtility", "FindFirstUtility",
            "AnyMatchEagerLazy", "CountEagerLazy", "FindFirstEagerLazy", "CollectorsAndCollectors2");
    private static final List<String> MOVERS = List.of("ListMemoryTest", "SetMemoryTest", "MapMemoryTest",
            "StreamVsLazyIterableMemoryTest");
    private static final String COH_ON = "-XX:+UseCompactObjectHeaders";
    private static final String COH_OFF = "-XX:-UseCompactObjectHeaders";
    private static final String EA_OFF = "-XX:+UseCompactObjectHeaders -XX:-DoEscapeAnalysis";
    private static final String JAR = "target-bench/microbenchmarks.jar";

    // JMH writes this line to the -o console file when a benchmark run completes
    private static final String JMH_DONE = "Benchmark result is saved to";

    private static boolean dryRun;
    private static boolean resume;
    private static final String JAVA = Path.of(System.getProperty("java.home"), "bin", "java").toString();

    public static void main(String[] args) throws Exception
    {
        String stage = null;
        String runTs = null;
        String slidesDir = null;
        List<String> parts = PARTS;
        for (String a : args)
        {
            if (a.equals("--dry-run"))
            {
                dryRun = true;
            }
            else if (a.equals("--resume"))
            {
                resume = true;
            }
            else if (a.startsWith("--slides-dir="))
            {
                slidesDir = a.substring("--slides-dir=".length());
            }
            else if (PARTS.contains(a))
            {
                parts = List.of(a);
            }
            else if (a.matches("\\d{8}-\\d{4}"))
            {
                runTs = a;
            }
            else if (stage == null && List.of("run", "reports", "slides", "all").contains(a))
            {
                stage = a;
            }
            else
            {
                usage("unknown argument: " + a);
            }
        }
        if (stage == null)
        {
            usage("no stage given");
        }
        if (resume && runTs == null)
        {
            usage("--resume needs the RUN_TS of the run to continue");
        }
        if (stage.equals("run") || stage.equals("all"))
        {
            if (runTs == null)
            {
                runTs = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
            }
            run(runTs, parts);
        }
        if (stage.equals("reports") || stage.equals("all"))
        {
            if (runTs == null)
            {
                runTs = newestRun();
            }
            reports(runTs, parts);
        }
        if (stage.equals("slides") || stage.equals("all"))
        {
            List<String> mvn = new ArrayList<>(List.of("mvn", "-B", "-Pslides", "process-resources"));
            if (slidesDir != null)
            {
                mvn.add("-Dslides.dir=" + slidesDir);
            }
            exec(mvn, null);
        }
    }

    private static void usage(String problem)
    {
        System.err.println(problem);
        System.err.println("usage: java src/main/java/tools/Pipeline.java <run|reports|slides|all> [RUN_TS] [shakers|movers] [--dry-run] [--resume] [--slides-dir=<folder>]");
        System.exit(2);
    }

    // ---- Stage 1: run (instructions.md P1)

    private static void run(String runTs, List<String> parts) throws Exception
    {
        System.out.println("RUN_TS=" + runTs);
        List<Path> raws = new ArrayList<>();
        for (String part : parts)
        {
            raws.add(raw(runTs, part));
        }
        writeMeta(runTs, raws);
        if (parts.contains("shakers"))
        {
            Path r = raw(runTs, "shakers");
            if (resume && Files.exists(Path.of(JAR)))
            {
                System.out.println("# skip (jar exists): mvn -B -Pbench clean package");
            }
            else
            {
                exec(List.of("mvn", "-B", "-Pbench", "clean", "package"), null);
            }
            List<String> classes = classes();
            for (String[] cfg : new String[][] {{"coh-on", COH_ON}, {"coh-off", COH_OFF}, {"ea-off", EA_OFF}})
            {
                for (String cls : classes)
                {
                    if (skipFinished(r.resolve(cls + "-" + cfg[0] + "-console.txt")))
                    {
                        continue;
                    }
                    exec(List.of(JAVA, "-jar", JAR, "conf\\.session\\.shakers\\." + cls + "Benchmark\\.",
                            "-prof", "gc", "-jvmArgsAppend", "-XX:+UseG1GC -Xms2g -Xmx2g " + cfg[1],
                            "-rf", "text", "-rff", r.resolve(cls + "-" + cfg[0] + ".txt").toString(),
                            "-o", r.resolve(cls + "-" + cfg[0] + "-console.txt").toString()), null);
                }
            }
            for (String[] cfg : new String[][] {{"histo-coh-on", COH_ON}, {"histo-coh-off", COH_OFF}, {"histo-ea-off", EA_OFF}})
            {
                if (skipFinished(r.resolve("all-" + cfg[0] + "-console.txt")))
                {
                    continue;
                }
                // HistoProfiler prints its HISTO lines from the forked VM to the console, not to the -o file
                exec(List.of(JAVA, "-jar", JAR, "conf\\.session\\.shakers\\.",
                        "-f", "1", "-wi", "5", "-w", "1s", "-i", "3", "-r", "100ms",
                        "-jvmArgs", "-XX:+UseParallelGC -Xms12g -Xmx12g -Xmn10g " + cfg[1],
                        "-prof", "prof.HistoProfiler", "-prof", "gc",
                        "-rf", "text", "-rff", r.resolve("all-" + cfg[0] + ".txt").toString(),
                        "-o", r.resolve("all-" + cfg[0] + "-console.txt").toString()),
                        r.resolve("all-" + cfg[0] + "-histo.txt"));
            }
        }
        if (parts.contains("movers"))
        {
            Path mr = raw(runTs, "movers");
            for (String[] cfg : new String[][] {{"coh-off", COH_OFF}, {"coh-on", COH_ON}})
            {
                for (String test : MOVERS)
                {
                    Path out = mr.resolve(test + "-" + cfg[0] + ".txt");
                    if (resume && Files.exists(out) && Files.readString(out, StandardCharsets.UTF_8).contains("BUILD "))
                    {
                        System.out.println("# skip (finished): " + out);
                        continue;
                    }
                    // JOL attaches to its own VM; without these two flags it fails under surefire
                    execTo(List.of("mvn", "-B", "test", "-Dtest=" + test, "-Dsurefire.failIfNoSpecifiedTests=false",
                            "-DargLine=" + cfg[1] + " -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading"),
                            out);
                }
            }
        }
    }

    // With --resume, a JMH step counts as finished when its console file has JMH's closing line; the result file
    // is not a test because JMH creates it when the step starts. An interrupted step is run again from the start.
    private static boolean skipFinished(Path console) throws IOException
    {
        if (!resume || !Files.exists(console))
        {
            return false;
        }
        if (!Files.readString(console, StandardCharsets.UTF_8).contains(JMH_DONE))
        {
            System.out.println("# re-run (unfinished): " + console);
            return false;
        }
        System.out.println("# skip (finished): " + console);
        return true;
    }

    private static Path raw(String runTs, String part) throws IOException
    {
        Path p = Path.of("generated", "runs", runTs, part, "raw");
        if (!dryRun)
        {
            Files.createDirectories(p);
        }
        return p;
    }

    private static List<String> classes()
    {
        String env = System.getenv("CLASSES");
        return env != null && !env.isBlank() ? List.of(env.split(",")) : SHAKERS;
    }

    // meta.txt (instructions.md P1.2): machine, JDK, Maven, GC flags, git commit; kept when the run already has one
    private static void writeMeta(String runTs, List<Path> raws) throws Exception
    {
        Path existing = null;
        for (Path dir : List.of(raw(runTs, "shakers"), raw(runTs, "movers")))
        {
            if (Files.exists(dir.resolve("meta.txt")))
            {
                existing = dir.resolve("meta.txt");
            }
        }
        String script = """
                echo "run_ts: %s"
                case "$(uname)" in Darwin) WHERE="local Mac" ;; *) WHERE="cloud sandbox" ;; esac
                CPU=$(sysctl -n machdep.cpu.brand_string 2>/dev/null || lscpu | sed -n 's/^Model name:[[:space:]]*//p')
                echo "environment: ${WHERE} (${CPU})"
                "%s" -version 2>&1
                mvn -v
                sw_vers 2>/dev/null || uname -a
                sysctl -n machdep.cpu.brand_string hw.ncpu hw.memsize 2>/dev/null || lscpu
                "%s" -XX:+UseCompactObjectHeaders -XX:+PrintFlagsFinal -version 2>/dev/null \\
                  | grep -E ' Use(G1|Parallel|Serial|Z|Shenandoah)GC |UseCompactObjectHeaders|DoEscapeAnalysis'
                git rev-parse HEAD; git status --short
                """.formatted(runTs, JAVA, JAVA);
        if (dryRun)
        {
            System.out.println(existing != null ? "# keep " + existing : "# write meta.txt to " + raws);
            return;
        }
        String meta;
        if (existing != null)
        {
            meta = Files.readString(existing, StandardCharsets.UTF_8);
        }
        else
        {
            Process p = new ProcessBuilder("bash", "-c", script).redirectErrorStream(true).start();
            meta = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
        }
        for (Path dir : raws)
        {
            if (!Files.exists(dir.resolve("meta.txt")))
            {
                Files.writeString(dir.resolve("meta.txt"), meta, StandardCharsets.UTF_8);
            }
        }
    }

    // ---- Stage 2: reports (instructions.md P2)

    private static void reports(String runTs, List<String> parts) throws Exception
    {
        System.out.println("RUN_TS=" + runTs);
        Path run = Path.of("generated", "runs", runTs);
        if (parts.contains("shakers") && hasFiles(run.resolve("shakers/raw"), "-coh-on.txt"))
        {
            exec(List.of(JAVA, tool("analysis/ShakersDocs.java"), ".", runTs), null);
            if (hasFiles(run.resolve("shakers/raw"), "-histo.txt"))
            {
                exec(List.of(JAVA, tool("analysis/JdkOpsDocs.java"), ".", runTs), null);
            }
        }
        else if (parts.contains("shakers"))
        {
            System.out.println("# no shakers raw files in " + run.resolve("shakers/raw") + ", skipped");
        }
        if (parts.contains("movers") && hasFiles(run.resolve("movers/raw"), "Test-coh-on.txt"))
        {
            exec(List.of(JAVA, tool("analysis/MoversDocs.java"), ".", runTs), null);
        }
        else if (parts.contains("movers"))
        {
            System.out.println("# no movers raw files in " + run.resolve("movers/raw") + ", skipped");
        }
        exec(List.of(JAVA, tool("analysis/RunSummary.java"), ".", runTs), null);
    }

    private static String tool(String file)
    {
        return Path.of("src", "main", "java", "tools").resolve(file).toString();
    }

    private static boolean hasFiles(Path dir, String suffix) throws IOException
    {
        if (!Files.isDirectory(dir))
        {
            return false;
        }
        try (Stream<Path> s = Files.list(dir))
        {
            return s.anyMatch(p -> p.getFileName().toString().endsWith(suffix));
        }
    }

    private static String newestRun() throws IOException
    {
        Path runs = Path.of("generated", "runs");
        try (Stream<Path> s = Files.isDirectory(runs) ? Files.list(runs) : Stream.empty())
        {
            return s.map(p -> p.getFileName().toString())
                    .filter(n -> n.matches("\\d{8}-\\d{4}"))
                    .max(Comparator.naturalOrder())
                    .orElseThrow(() -> new IllegalStateException("no run folder in generated/runs; give a RUN_TS"));
        }
    }

    // ---- Running commands

    // Runs a command on the console; with histo set, keeps only its HISTO lines and writes them there
    private static void exec(List<String> command, Path histo) throws Exception
    {
        System.out.println("$ " + String.join(" ", quote(command)) + (histo != null ? " | grep '^HISTO' > " + histo : ""));
        if (dryRun)
        {
            return;
        }
        ProcessBuilder pb = new ProcessBuilder(windows(command)).redirectErrorStream(true);
        if (histo == null)
        {
            pb.inheritIO();
            check(command, pb.start().waitFor());
            return;
        }
        Process p = pb.start();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(Files.newBufferedWriter(histo, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = in.readLine()) != null)
            {
                if (line.startsWith("HISTO"))
                {
                    out.println(line);
                }
            }
        }
        check(command, p.waitFor());
    }

    // Runs a command with its whole output (stdout and stderr) in a file; a failing test is reported, not fatal
    private static void execTo(List<String> command, Path file) throws Exception
    {
        System.out.println("$ " + String.join(" ", quote(command)) + " > " + file + " 2>&1");
        if (dryRun)
        {
            return;
        }
        Process p = new ProcessBuilder(windows(command)).redirectErrorStream(true)
                .redirectOutput(file.toFile()).start();
        int code = p.waitFor();
        if (code != 0)
        {
            System.out.println("# exit code " + code + ", see " + file);
        }
    }

    private static void check(List<String> command, int code)
    {
        if (code != 0)
        {
            System.err.println("failed (exit code " + code + "): " + String.join(" ", command));
            System.exit(code);
        }
    }

    // mvn is mvn.cmd on Windows
    private static List<String> windows(List<String> command)
    {
        if (File.separatorChar == '\\' && command.get(0).equals("mvn"))
        {
            List<String> c = new ArrayList<>(command);
            c.set(0, "mvn.cmd");
            return c;
        }
        return command;
    }

    private static List<String> quote(List<String> command)
    {
        List<String> q = new ArrayList<>();
        for (String c : command)
        {
            q.add(c.matches("[\\w./:=+,-]+") ? c : "'" + c + "'");
        }
        return q;
    }
}
