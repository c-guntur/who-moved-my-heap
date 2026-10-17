package tools.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Assembles the results files, analysis docs, charts and run summary for one shakers run.
 *
 * Usage (Java 25, from the project root): java src/main/java/tools/analysis/ShakersDocs.java . <RUN_TS>
 * Reads generated/runs/<RUN_TS>/shakers/raw/ and writes generated/runs/<RUN_TS>/shakers/ (Layout, instructions.md L).
 * Every sentence in the docs is written from the run's numbers; there is no hand-written input.
 * Optional environment variables:
 *   CLASSES=AnyMatchUtility,...  only these classes (default: every class with raw files in the run)
 *   SHAKERS_RUN_DIR=<folder>     raw run folder, for older runs kept elsewhere
 *   MMDC=<path to mmdc>          parse-check every .mmd with the Mermaid CLI (MMDC_PUPPETEER: its -p file)
 */
public class ShakersDocs
{
    private static final List<String> ALL_CLASSES = List.of("AnyMatchUtility", "CountUtility", "FindFirstUtility",
            "AnyMatchEagerLazy", "CountEagerLazy", "FindFirstEagerLazy", "CollectorsAndCollectors2");
    private static final List<String> CONFIGS = List.of("coh-on", "coh-off", "ea-off");
    private static final double MEASURE_S = 40.0;  // 2 forks x 10 iterations x 2 s
    // Analysis title per class; the stream classes are about lambdas, Collectors is about creating collectors
    private static final Map<String, String> TITLE = Map.of("CollectorsAndCollectors2", "creating collectors and garbage");
    private static final Map<String, String> FLAGS = Map.of(
            "coh-on", "-XX:+UseCompactObjectHeaders",
            "coh-off", "-XX:-UseCompactObjectHeaders",
            "ea-off", "-XX:+UseCompactObjectHeaders -XX:-DoEscapeAnalysis");

    /**
     * Numbers derived from one JMH row. bytes, gcCount and gcTime stay whole numbers (0) when JMH reported
     * nothing or zero, as the Python version did, so summary.json prints the same.
     */
    record Derived(double ns, Double nsErr, double mcalls, double calls, Number bytes, Number gcCount, Number gcTime,
                   double gcPerB, double gctPerB)
    {
        Map<String, Object> toJson()
        {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ns", ns);
            out.put("ns_err", nsErr);
            out.put("mcalls", mcalls);
            out.put("calls", calls);
            out.put("bytes", bytes);
            out.put("gc_count", gcCount);
            out.put("gc_time", gcTime);
            out.put("gc_per_b", gcPerB);
            out.put("gct_per_b", gctPerB);
            return out;
        }
    }

    private static String root;
    private static String runTs;
    private static Layout layout;
    private static Path runDir;
    private static String runRel;
    private static Path docs;
    private static String mmdc;
    private static String pp;
    private static List<String> histoCfgs;
    private static Map<String, Map<JmhParse.HistoKey, JmhParse.Histo>> histo;
    private static String meta;
    private static String env;
    private static String java;
    private static String caveats;

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws IOException
    {
        root = args[0];
        runTs = args[1];
        layout = Layout.shakers(root, runTs);
        runDir = layout.raw;
        runRel = layout.rawRel;
        docs = layout.reports;
        mmdc = System.getenv("MMDC");
        pp = System.getenv("MMDC_PUPPETEER");

        String classesEnv = System.getenv("CLASSES");
        List<String> classes = classesEnv != null && !classesEnv.isEmpty() ? List.of(classesEnv.split(",")) : ALL_CLASSES;
        // histo-ea-off (escape analysis off) is optional so older runs without it still regenerate.
        histoCfgs = new ArrayList<>();
        for (String c : List.of("histo-coh-on", "histo-coh-off", "histo-ea-off"))
        {
            if (Files.exists(runDir.resolve("all-" + c + "-histo.txt")))
            {
                histoCfgs.add(c);
            }
        }
        histo = new LinkedHashMap<>();
        for (String c : histoCfgs)
        {
            histo.put(c, JmhParse.parseHisto(runDir.resolve("all-" + c + "-histo.txt")));
        }
        // Classes added after a run have no raw files in it; without CLASSES, generate only what the run has.
        if (classesEnv == null || classesEnv.isEmpty())
        {
            List<String> present = new ArrayList<>();
            for (String c : classes)
            {
                if (Files.exists(runDir.resolve(c + "-coh-on.txt")))
                {
                    present.add(c);
                }
            }
            classes = present;
        }
        meta = Files.readString(runDir.resolve("meta.txt"), StandardCharsets.UTF_8);
        env = Layout.environment(meta);
        java = Layout.javaVersion(meta);
        caveats = "One machine (" + env + "), one JDK build (" + java + "), G1 with a fixed "
                + "2 GB heap. Microbenchmarks, not an application. Mechanisms are read from the class histograms where shown; "
                + "anything else is labelled inferred.";

        Map<String, Object> summary = new LinkedHashMap<>();
        for (String c : classes)
        {
            Map<String, Object> entry = new LinkedHashMap<>();
            Result result = genClass(c);
            for (Map.Entry<String, Map<JmhParse.Key, Derived>> cfg : result.d().entrySet())
            {
                for (Map.Entry<JmhParse.Key, Derived> v : cfg.getValue().entrySet())
                {
                    entry.put(cfg.getKey() + "|" + v.getKey().method() + "|" + pyTest(v.getKey().test()), v.getValue().toJson());
                }
            }
            for (Map.Entry<String, Map<JmhParse.Key, JmhParse.Histo>> cfg : result.h().entrySet())
            {
                for (Map.Entry<JmhParse.Key, JmhParse.Histo> v : cfg.getValue().entrySet())
                {
                    JmhParse.Histo h = v.getValue();
                    Map<String, Object> json = new LinkedHashMap<>();
                    // Python's sum() of no classes is the whole number 0
                    json.put("objs", h.classes.isEmpty() ? (Object) 0 : h.objs);
                    json.put("bytes", h.classes.isEmpty() ? (Object) 0 : h.bytes);
                    Map<String, Object> perClass = new LinkedHashMap<>();
                    for (Map.Entry<String, double[]> k : h.classes.entrySet())
                    {
                        perClass.put(k.getKey(), List.of(k.getValue()[0], k.getValue()[1]));
                    }
                    json.put("classes", perClass);
                    json.put("clean", h.cleanIters + "/" + h.iters);
                    entry.put(cfg.getKey() + "|" + v.getKey().method() + "|" + pyTest(v.getKey().test()) + "|histo", json);
                }
            }
            summary.put(c, entry);
        }
        Files.writeString(layout.out.resolve("summary.json"), Py.json(summary), StandardCharsets.UTF_8);
        writeRunSummary(classes);
        System.out.println("ok");
    }

    static String kind(String m)
    {
        if (m.equals("collectors"))
        {
            return "JDK Collectors";
        }
        if (m.equals("collectors2"))
        {
            return "EC Collectors2";
        }
        if (m.startsWith("jdk"))
        {
            return "JDK stream";
        }
        if (m.contains("With"))
        {
            return "EC fat-free (*With)";
        }
        if (m.startsWith("ecLazy"))
        {
            return "EC lazy, capturing";
        }
        return "EC eager, capturing";
    }

    // The f() of the Python version: "n/a", "0", "≈0", then 0, 1 or 2 decimals by size
    static String f(Double x, int d)
    {
        if (x == null)
        {
            return "n/a";
        }
        if (x == 0)
        {
            return "0";
        }
        if (Math.abs(x) < 0.01)
        {
            return "≈0";
        }
        if (Math.abs(x) >= 100)
        {
            return Py.f(x, 0, true);
        }
        if (Math.abs(x) >= 10)
        {
            return Py.f(x, Math.min(d, 1), true);
        }
        return Py.f(x, Math.max(d, 2), true);
    }

    static String f(double x)
    {
        return f(Double.valueOf(x), 1);
    }

    static String fb(double x)
    {
        return x < 0.5 ? "≈0" : Py.f(x, 0, true);
    }

    // Python's `value or 0`: a missing or zero score stays the whole number 0
    private static Number orZero(Map<String, JmhParse.Score> row, String metric)
    {
        JmhParse.Score s = row.get(metric);
        if (s == null || s.score() == null || s.score() == 0)
        {
            return 0;
        }
        return s.score();
    }

    static Derived derive(Map<String, JmhParse.Score> row)
    {
        double ns = row.get("ns").score();
        double calls = MEASURE_S * 1e9 / ns;
        Number gcc = orZero(row, "gc.count");
        Number gct = orZero(row, "gc.time");
        return new Derived(ns, row.get("ns").error(), 1000.0 / ns, calls, orZero(row, "gc.alloc.rate.norm"), gcc, gct,
                gcc.doubleValue() / calls * 1e9, gct.doubleValue() / calls * 1e9);
    }

    // Heading for one value of the benchmark's test parameter (null: the benchmark has no @Param)
    static String tl(Integer t)
    {
        return t != null ? "test = " + t : "one call (no test parameter)";
    }

    // The test value as Python printed it in summary.json keys ("None" when there is no @Param)
    static String pyTest(Integer t)
    {
        return t == null ? "None" : t.toString();
    }

    static String ts(Integer t)
    {
        return t == null ? "-" : t.toString();
    }

    static List<String> methodsOf(Map<JmhParse.Key, ?> table)
    {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (JmhParse.Key k : table.keySet())
        {
            set.add(k.method());
        }
        List<String> ms = new ArrayList<>(set);
        ms.sort(Comparator.<String, Boolean>comparing(m -> !kind(m).startsWith("JDK"))
                .thenComparing(m -> m.contains("Lazy"))
                .thenComparing(m -> m.contains("With"))
                .thenComparing(Comparator.naturalOrder()));
        return ms;
    }

    static String ratio(double a, double b)
    {
        return Py.f(b != 0 ? a / b : Double.POSITIVE_INFINITY, 1);
    }

    private static String pad(String s, int width)
    {
        return s.length() >= width ? s : " ".repeat(width - s.length()) + s;
    }

    record Result(Map<String, Map<JmhParse.Key, Derived>> d, Map<String, Map<JmhParse.Key, JmhParse.Histo>> h)
    {
    }

    @SuppressWarnings("unchecked")
    static Result genClass(String cls) throws IOException
    {
        Map<String, Map<JmhParse.Key, Map<String, JmhParse.Score>>> tables = new LinkedHashMap<>();
        for (String c : CONFIGS)
        {
            tables.put(c, JmhParse.parseTable(runDir.resolve(cls + "-" + c + ".txt")));
        }
        Map<JmhParse.Key, Map<String, JmhParse.Score>> on = tables.get("coh-on");
        List<String> ms = methodsOf(on);
        String jdk = null;
        for (String m : ms)
        {
            if (jdk == null && kind(m).startsWith("JDK"))
            {
                jdk = m;
            }
        }
        LinkedHashSet<Integer> testSet = new LinkedHashSet<>();
        for (JmhParse.Key k : on.keySet())
        {
            testSet.add(k.test());
        }
        List<Integer> tests = new ArrayList<>(testSet);
        tests.sort(Comparator.comparingInt(t -> t == null ? -1 : t));
        Map<String, Map<JmhParse.Key, Derived>> d = new LinkedHashMap<>();
        for (Map.Entry<String, Map<JmhParse.Key, Map<String, JmhParse.Score>>> t : tables.entrySet())
        {
            Map<JmhParse.Key, Derived> derived = new LinkedHashMap<>();
            for (Map.Entry<JmhParse.Key, Map<String, JmhParse.Score>> row : t.getValue().entrySet())
            {
                derived.put(row.getKey(), derive(row.getValue()));
            }
            d.put(t.getKey(), derived);
        }
        Map<String, Map<JmhParse.Key, JmhParse.Histo>> hh = new LinkedHashMap<>();
        for (Map.Entry<String, Map<JmhParse.HistoKey, JmhParse.Histo>> c : histo.entrySet())
        {
            Map<JmhParse.Key, JmhParse.Histo> mine = new LinkedHashMap<>();
            for (Map.Entry<JmhParse.HistoKey, JmhParse.Histo> e : c.getValue().entrySet())
            {
                if (e.getKey().benchClass().equals(cls + "Benchmark"))
                {
                    mine.put(new JmhParse.Key(e.getKey().method(), e.getKey().test()), e.getValue());
                }
            }
            hh.put(c.getKey(), mine);
        }
        String commands = COMMANDS.replace("${CLASS}", cls).replace("${RUN_TS}", runTs);

        // ---------- results ----------
        List<String> r = new ArrayList<>(List.of("= " + cls + "Benchmark results (run " + runTs + ")", ":toc:", "",
                "Assembled from the raw JMH output in `" + runRel + "/`. Numbers are copied verbatim.", "",
                "== Run metadata", "", "....", meta.stripTrailing(), "....", ""));
        for (String c : CONFIGS)
        {
            r.addAll(List.of("== " + c + " (`-XX:+UseG1GC -Xms2g -Xmx2g " + FLAGS.get(c) + "`)", "", "....",
                    Files.readString(runDir.resolve(cls + "-" + c + ".txt"), StandardCharsets.UTF_8).stripTrailing(), "....", ""));
        }
        for (String c : histoCfgs)
        {
            r.addAll(List.of("== Object counts: " + c, "",
                    "Per-class lines averaged over clean iterations (no GC during the iteration). "
                            + "Totals are the sum of the per-class lines (classes with at least 0.5 objects per call); "
                            + "the profiler's own `total` line is not used because it includes its own allocations.", "",
                    "[cols=\"3,1,1,1,1\",options=\"header\"]", "|===", "|Benchmark |test |clean iterations |objects per call |bytes per call"));
            for (String m : ms)
            {
                for (Integer t : tests)
                {
                    JmhParse.Histo h = hh.get(c).get(new JmhParse.Key(m, t));
                    if (h != null)
                    {
                        r.add("|" + m + " |" + ts(t) + " |" + h.cleanIters + "/" + h.iters + " |" + Py.f(h.objs, 3) + " |" + Py.f(h.bytes, 1));
                    }
                }
            }
            r.addAll(List.of("|===", "", ".Per-class breakdown", "...."));
            for (String m : ms)
            {
                for (Integer t : tests)
                {
                    JmhParse.Histo h = hh.get(c).get(new JmhParse.Key(m, t));
                    if (h == null)
                    {
                        continue;
                    }
                    r.add(t == null ? m : m + " test=" + t);
                    for (Map.Entry<String, double[]> k : byBytes(h))
                    {
                        r.add("    " + pad(Py.f(k.getValue()[0], 3), 7) + " obj " + pad(Py.f(k.getValue()[1], 1), 8) + " B  " + k.getKey());
                    }
                    if (h.classes.isEmpty())
                    {
                        r.add("    (nothing at or above 0.5 objects per call)");
                    }
                }
            }
            r.addAll(List.of("....", ""));
        }
        r.addAll(List.of("== Commands", "", "....", commands, "....", ""));
        Files.writeString(docs.resolve(runTs + "-" + cls + "Results.adoc"),
                String.join("\n", r), StandardCharsets.UTF_8);

        // ---------- charts ----------
        Map<String, ShakersCharts.Chart> charts = new LinkedHashMap<>();
        for (ShakersCharts.Chart chart : ShakersCharts.build(cls, ms, d, tests, layout.images, runTs, mmdc, pp, layout.mermaid))
        {
            charts.put(chart.key(), chart);
        }

        // ---------- analysis ----------
        Map<JmhParse.Key, Derived> dOn = d.get("coh-on");
        Map<JmhParse.Key, JmhParse.Histo> hOn = hh.get("histo-coh-on");
        List<String> a = new ArrayList<>(List.of("= " + cls + ": " + TITLE.getOrDefault(cls, "fat-free lambdas and garbage") + " (run " + runTs + ")",
                ":toc:", ":toclevels: 2", "",
                "Input: link:" + runTs + "-" + cls + "Results.adoc[" + runTs + "-" + cls + "Results.adoc]. "
                        + "Environment: " + env + " (" + java + "). Absolute timings depend on the machine; bytes and object counts do not.", "",
                "== Summary", ""));
        a.addAll(classSummary(cls, ms, jdk, tests, d, hh));
        // head to head
        a.addAll(List.of("== Head-to-head (compact headers on)", "",
                "Throughput = 1000 ÷ ns per call. Calls measured = 40 s ÷ ns per call. "
                        + "Objects per call come from the object-count run; bytes per call from `gc.alloc.rate.norm`.", "",
                "[cols=\"3,2,1,1,1,1,1,1\",options=\"header\"]", "|===",
                "|Implementation |Kind |Throughput (M calls/s) |Time per call (ns) |Objects per call |Bytes per call |GC runs per 1B calls |GC time per 1B calls (ms)"));
        for (Integer t : tests)
        {
            a.add("8+h|" + tl(t));
            for (String m : ms)
            {
                Derived x = dOn.get(new JmhParse.Key(m, t));
                JmhParse.Histo h = hOn.get(new JmhParse.Key(m, t));
                a.add("|`" + m + "` |" + kind(m) + " |" + f(x.mcalls()) + " |" + f(x.ns()) + " ± " + f(x.nsErr(), 1) + " |"
                        + (h != null ? f(h.objs) : "n/a") + " |" + fb(x.bytes().doubleValue()) + " |" + f(x.gcPerB()) + " |" + f(x.gctPerB()));
            }
            // comparison row: each EC version vs JDK
            Derived dj = dOn.get(new JmhParse.Key(jdk, t));
            JmhParse.Histo hj = hOn.get(new JmhParse.Key(jdk, t));
            List<String> parts = new ArrayList<>();
            for (String m : ms)
            {
                if (m.equals(jdk))
                {
                    continue;
                }
                Derived x = dOn.get(new JmhParse.Key(m, t));
                JmhParse.Histo h = hOn.get(new JmhParse.Key(m, t));
                Double fo = h != null && hj != null ? hj.objs - h.objs : null;
                parts.add("`" + m + "` vs JDK: " + ratio(dj.ns(), x.ns()) + "x faster, "
                        + (fo != null ? f(fo, 1) : "n/a") + " fewer objects and " + fb(dj.bytes().doubleValue() - x.bytes().doubleValue())
                        + " fewer bytes per call, " + f(x.gcPerB()) + " vs " + f(dj.gcPerB()) + " GC runs per billion calls");
            }
            a.add("8+a|" + String.join(" +\n", parts));
        }
        a.addAll(List.of("|===", ""));
        a.addAll(embed(charts.get("throughput")));
        a.addAll(embed(charts.get("gc-per-billion")));

        // garbage
        a.addAll(List.of("== Garbage: where it comes from", "",
                "=== Objects per call by class (object-count run, compact headers on)", "", "...."));
        for (Integer t : tests)
        {
            for (String m : ms)
            {
                JmhParse.Histo h = hOn.get(new JmhParse.Key(m, t));
                if (h == null)
                {
                    continue;
                }
                a.add(m + (t == null ? "" : " test=" + t) + ": " + Py.f(h.objs, 2) + " objects, " + Py.f(h.bytes, 0) + " B per call");
                for (Map.Entry<String, double[]> k : byBytes(h))
                {
                    a.add("    " + pad(Py.f(k.getValue()[0], 2), 6) + " x " + k.getKey() + "  (" + Py.f(k.getValue()[1], 0) + " B)");
                }
            }
        }
        a.addAll(List.of("....", ""));
        a.addAll(embed(charts.get("bytes-per-call")));
        a.addAll(List.of("=== Escape analysis off (upper bound for lambda garbage)", "",
                "Same benchmarks with `-XX:-DoEscapeAnalysis`. Real applications cannot count on escape analysis "
                        + "(megamorphic call sites, code that is not inlined), so this shows what each lambda shape costs when "
                        + "it does not help. It is an upper bound, not typical production behaviour.", "",
                "[cols=\"3,2,1,1,1,1\",options=\"header\"]", "|===",
                "|Implementation |Kind |Bytes per call, EA on |Bytes per call, EA off |Time per call EA on (ns) |Time per call EA off (ns)"));
        for (Integer t : tests)
        {
            a.add("6+h|" + tl(t));
            for (String m : ms)
            {
                Derived d1 = dOn.get(new JmhParse.Key(m, t));
                Derived d2 = d.get("ea-off").get(new JmhParse.Key(m, t));
                a.add("|`" + m + "` |" + kind(m) + " |" + fb(d1.bytes().doubleValue()) + " |" + fb(d2.bytes().doubleValue())
                        + " |" + f(d1.ns()) + " |" + f(d2.ns()));
            }
        }
        a.addAll(List.of("|===", ""));
        a.addAll(embed(charts.get("ea-off-bytes")));

        // spikes
        a.addAll(List.of("== Spikes and heap headroom (derived, not measured)", "",
                "Formulas: allocation rate (MB/s) = bytes per call × calls/s ÷ 10^6. "
                        + "Young collections per minute = allocation rate ÷ 512 MB (example young generation) × 60. "
                        + "Allocated during a 10 s spike = bytes per call × calls/s × 10 s: the young-generation space the spike needs, "
                        + "or the collections it causes when that space is not there. Loads are application-wide, across all threads.", "",
                "[cols=\"3,1,1,1,1,1\",options=\"header\"]", "|===",
                "|Implementation |Bytes per call |MB/s at 1 M calls/s |MB/s at 10 M calls/s |Young GCs per minute at 10 M calls/s |MB allocated in a 10 s spike at 10 M calls/s"));
        for (Integer t : tests)
        {
            a.add("6+h|" + tl(t));
            for (String m : ms)
            {
                Number bytes = dOn.get(new JmhParse.Key(m, t)).bytes();
                double b = bytes.doubleValue();
                // Python multiplies the int 0 as an int, so 0 * 10 / 512 * 60 is still a float 0.0: f() prints "0" either way
                a.add("|`" + m + "` |" + fb(b) + " |" + f(b) + " |" + f(b * 10) + " |" + f(b * 10 / 512 * 60) + " |" + f(b * 100));
            }
        }
        a.addAll(List.of("|===", "",
                "JMH also reports `gc.alloc.rate` (MB/s), but that number scales with how fast the benchmark runs, so a faster "
                        + "method can show a higher MB/s while allocating the same per call. `gc.alloc.rate.norm` (bytes per call) is the "
                        + "number to size heaps with.", ""));
        a.addAll(embed(charts.get("spike-headroom")));

        // COH
        a.addAll(List.of("== Compact object headers", "",
                "[cols=\"3,1,1,1,1,1,1\",options=\"header\"]", "|===",
                "|Implementation |Bytes per call on |Bytes per call off |Saved |Objects per call on |Objects per call off |Time per call on / off (ns)"));
        for (Integer t : tests)
        {
            a.add("7+h|" + tl(t));
            for (String m : ms)
            {
                Derived d1 = dOn.get(new JmhParse.Key(m, t));
                Derived d2 = d.get("coh-off").get(new JmhParse.Key(m, t));
                JmhParse.Histo h1 = hOn.get(new JmhParse.Key(m, t));
                JmhParse.Histo h2 = hh.get("histo-coh-off").get(new JmhParse.Key(m, t));
                double b1 = d1.bytes().doubleValue();
                double b2 = d2.bytes().doubleValue();
                String saved = b2 > 1 ? Py.f((1 - b1 / b2) * 100, 0) + "%" : "n/a";
                a.add("|`" + m + "` |" + fb(b1) + " |" + fb(b2) + " |" + saved + " |" + (h1 != null ? f(h1.objs) : "n/a") + " |"
                        + (h2 != null ? f(h2.objs) : "n/a") + " |" + f(d1.ns()) + " / " + f(d2.ns()));
            }
        }
        a.addAll(List.of("|===", "", "Bytes per call here are `gc.alloc.rate.norm` from the main runs. Where that is a few bytes below the "
                + "object-count total, the JIT scalar-replaced one pipeline object in some forks; the object-count run (exact "
                + "per-class counts) is the reference for what each call allocates.", ""));
        a.addAll(embed(charts.get("coh-bytes")));

        // supporting
        a.addAll(List.of("== Supporting analysis", "",
                "=== Raw GC totals (compact headers on, per 40 s measurement)", "",
                "Raw totals are shown for completeness only. Do not compare them across implementations: a faster "
                        + "method fits more calls into the same 40 s.", "",
                "[cols=\"3,1,1,1,1\",options=\"header\"]", "|===", "|Implementation |test |calls measured |gc.count |gc.time (ms)"));
        for (Integer t : tests)
        {
            for (String m : ms)
            {
                Derived x = dOn.get(new JmhParse.Key(m, t));
                a.add("|`" + m + "` |" + ts(t) + " |" + Py.g(x.calls(), 3) + " |" + f(x.gcCount().doubleValue()) + " |" + f(x.gcTime().doubleValue()));
            }
        }
        a.addAll(List.of("|===", "", "== Caveats", "", caveats, "",
                "== Reproduce", "", "Run folder: `" + runRel + "/`. Results: `" + layout.reportsRel + "/" + runTs + "-" + cls + "Results.adoc`.", "",
                "....", commands, "....", ""));
        Files.writeString(docs.resolve(runTs + "-" + cls + "Analysis.adoc"), String.join("\n", a), StandardCharsets.UTF_8);
        return new Result(d, hh);
    }

    // One row per class for the run summary, filled by classSummary
    private static final Map<String, String> RUN_ROWS = new LinkedHashMap<>();

    /** The Summary section of one class: short bullets written from the run's numbers. */
    static List<String> classSummary(String cls, List<String> ms, String jdk, List<Integer> tests,
            Map<String, Map<JmhParse.Key, Derived>> d, Map<String, Map<JmhParse.Key, JmhParse.Histo>> hh)
    {
        Map<JmhParse.Key, Derived> on = d.get("coh-on");
        List<String> ec = new ArrayList<>(ms);
        ec.remove(jdk);
        List<String> shown = new ArrayList<>();
        for (Integer t : tests)
        {
            if (t != null)
            {
                shown.add(t.toString());
            }
        }
        String where = shown.isEmpty() ? "" : " across test = " + String.join(", ", shown);

        List<Double> jdkBytes = new ArrayList<>();
        List<Double> jdkObjs = new ArrayList<>();
        List<Double> ecBytes = new ArrayList<>();
        List<Double> ratios = new ArrayList<>();
        Map<JmhParse.Key, JmhParse.Histo> hOn = hh.getOrDefault("histo-coh-on", Map.of());
        for (Integer t : tests)
        {
            Derived j = on.get(new JmhParse.Key(jdk, t));
            jdkBytes.add(j.bytes().doubleValue());
            JmhParse.Histo h = hOn.get(new JmhParse.Key(jdk, t));
            if (h != null)
            {
                jdkObjs.add(h.objs);
            }
            for (String m : ec)
            {
                Derived x = on.get(new JmhParse.Key(m, t));
                ecBytes.add(x.bytes().doubleValue());
                ratios.add(j.ns() / x.ns());
            }
        }

        List<String> out = new ArrayList<>();
        out.add("* `" + jdk + "` (" + kind(jdk) + ") allocates " + Layout.range(jdkBytes, ShakersDocs::fb) + " B"
                + (jdkObjs.isEmpty() ? "" : " (" + Layout.range(jdkObjs, x -> Py.f(x, 0, true)) + " objects)") + " per call" + where + ".");
        List<String> perMethod = new ArrayList<>();
        for (String m : ec)
        {
            perMethod.add("`" + m + "` " + Layout.range(values(on, m, tests), ShakersDocs::fb) + " B");
        }
        out.add("* The Eclipse Collections versions allocate per call: " + String.join(", ", perMethod) + ".");
        double ecMax = ecBytes.isEmpty() ? 0 : ecBytes.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
        out.add("* At 10 million calls per second the JDK version allocates " + Layout.range(scaled(jdkBytes, 0.01), ShakersDocs::f)
                + " GB/s and the EC versions " + (ecMax < 0.5 ? "nothing" : "up to " + f(ecMax * 0.01) + " GB/s")
                + ": the young-generation space a 10-second spike at that rate needs is 10 times that.");
        List<String> times = new ArrayList<>();
        for (Integer t : tests)
        {
            double best = Double.MAX_VALUE;
            for (String m : ec)
            {
                best = Math.min(best, on.get(new JmhParse.Key(m, t)).ns());
            }
            times.add((t == null ? "" : "test = " + t + ": ") + f(on.get(new JmhParse.Key(jdk, t)).ns()) + " vs " + f(best) + " ns");
        }
        out.add("* Time per call, JDK vs the fastest EC version: " + String.join("; ", times) + ". The JDK version takes "
                + Layout.range(ratios, x -> Py.f(x, 1, true)) + " times as long as an EC version.");
        Map<JmhParse.Key, Derived> eaOff = d.get("ea-off");
        if (eaOff != null)
        {
            List<String> ea = new ArrayList<>();
            for (String m : ms)
            {
                ea.add("`" + m + "` " + Layout.range(values(eaOff, m, tests), ShakersDocs::fb) + " B");
            }
            out.add("* With escape analysis off (what each lambda shape costs when the JIT cannot remove it): "
                    + String.join(", ", ea) + " per call.");
        }
        Map<JmhParse.Key, Derived> cohOff = d.get("coh-off");
        if (cohOff != null)
        {
            List<String> changes = new ArrayList<>();
            for (Integer t : tests)
            {
                double off = cohOff.get(new JmhParse.Key(jdk, t)).bytes().doubleValue();
                double onB = on.get(new JmhParse.Key(jdk, t)).bytes().doubleValue();
                String pct = off > 1 ? " (" + Py.f(Math.abs(1 - onB / off) * 100, 0, true) + (onB <= off ? "% less" : "% more") + ")" : "";
                changes.add((t == null ? "" : "test = " + t + ": ") + fb(off) + " B without, " + fb(onB) + " B with" + pct);
            }
            out.add("* Compact object headers, JDK version bytes per call: " + String.join("; ", changes) + ".");
        }
        out.add("");
        RUN_ROWS.put(cls, "|link:" + runTs + "-" + cls + "Analysis.adoc[" + cls + "] |`" + jdk + "` |"
                + Layout.range(jdkBytes, ShakersDocs::fb) + " |" + (jdkObjs.isEmpty() ? "n/a" : Layout.range(jdkObjs, x -> Py.f(x, 0, true)))
                + " |" + Layout.range(ecBytes, ShakersDocs::fb) + " |" + Layout.range(ratios, x -> Py.f(x, 1, true)));
        return out;
    }

    private static List<Double> values(Map<JmhParse.Key, Derived> table, String m, List<Integer> tests)
    {
        List<Double> out = new ArrayList<>();
        for (Integer t : tests)
        {
            Derived x = table.get(new JmhParse.Key(m, t));
            if (x != null)
            {
                out.add(x.bytes().doubleValue());
            }
        }
        return out;
    }

    private static List<Double> scaled(List<Double> values, double factor)
    {
        List<Double> out = new ArrayList<>();
        for (double v : values)
        {
            out.add(v * factor);
        }
        return out;
    }

    /** generated/runs/<RUN_TS>/shakers/reports/<RUN_TS>-Summary.adoc: one row per class, links to every report. */
    private static void writeRunSummary(List<String> classes) throws IOException
    {
        List<String> s = new ArrayList<>(List.of("= Shakers run " + runTs + ": summary", ":toc:", "",
                "Environment: " + env + " (" + java + "). Written from the run's numbers by `ShakersDocs.java`; "
                        + "each class has its own analysis with the tables and charts behind these numbers.", "",
                "== Garbage and speed per class (compact headers on)", "",
                "[cols=\"3,2,1,1,1,1\",options=\"header\"]", "|===",
                "|Class |JDK method |JDK bytes per call |JDK objects per call |EC bytes per call (every EC version) |JDK time ÷ EC time"));
        for (String c : classes)
        {
            s.add(RUN_ROWS.get(c));
        }
        s.addAll(List.of("|===", "", "== Reports", ""));
        for (String c : classes)
        {
            s.add("* link:" + runTs + "-" + c + "Analysis.adoc[" + c + " analysis] and link:" + runTs + "-" + c
                    + "Results.adoc[results]");
        }
        s.addAll(List.of("* link:" + runTs + "-JdkStreamOpsAnalysis.adoc[JDK Stream ops analysis] (written by `JdkOpsDocs.java`)", "",
                "== Caveats", "", caveats, ""));
        Files.writeString(docs.resolve(runTs + "-Summary.adoc"), String.join("\n", s), StandardCharsets.UTF_8);
    }

    private static List<Map.Entry<String, double[]>> byBytes(JmhParse.Histo h)
    {
        List<Map.Entry<String, double[]>> entries = new ArrayList<>(h.classes.entrySet());
        // stable sort, largest bytes first, like Python's sorted(key=-bytes)
        entries.sort(Comparator.comparingDouble(e -> -e.getValue()[1]));
        return entries;
    }

    private static List<String> embed(ShakersCharts.Chart c)
    {
        return Layout.embed(c.caption(), c.png(), c.mmd());
    }

    private static final String COMMANDS = """
            mvn -Pbench clean compile
            mvn -Pbench package
            # main runs, CONFIG/FLAGS = coh-on: -XX:+UseCompactObjectHeaders | coh-off: -XX:-UseCompactObjectHeaders
            #                          ea-off: -XX:+UseCompactObjectHeaders -XX:-DoEscapeAnalysis
            java -jar target-bench/microbenchmarks.jar "conf\\.session\\.shakers\\.${CLASS}Benchmark\\." \\
              -prof gc -jvmArgsAppend "-XX:+UseG1GC -Xms2g -Xmx2g ${FLAGS}" \\
              -rf text -rff generated/runs/${RUN_TS}/shakers/raw/${CLASS}-${CONFIG}.txt -o generated/runs/${RUN_TS}/shakers/raw/${CLASS}-${CONFIG}-console.txt
            # object counts, CONFIG/COH = histo-coh-on: -XX:+UseCompactObjectHeaders | histo-coh-off: -XX:-UseCompactObjectHeaders
            #                            histo-ea-off: -XX:+UseCompactObjectHeaders -XX:-DoEscapeAnalysis
            java -jar target-bench/microbenchmarks.jar "conf\\.session\\.shakers\\." -f 1 -wi 5 -w 1s -i 3 -r 100ms \\
              -jvmArgs "-XX:+UseParallelGC -Xms12g -Xmx12g -Xmn10g ${COH}" -prof prof.HistoProfiler -prof gc \\
              -rf text -rff generated/runs/${RUN_TS}/shakers/raw/all-${CONFIG}.txt -o generated/runs/${RUN_TS}/shakers/raw/all-${CONFIG}-console.txt
            # HISTO lines go to the console, not to the -o file; they were saved to generated/runs/${RUN_TS}/shakers/raw/all-${CONFIG}-histo.txt""";
}
