package tools.analysis;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Movers (JOL footprint) analysis: one AsciiDoc per test plus charts, from the compact-headers off/on baselines.
 *
 * Usage (Java 25, from the project root): java src/main/java/tools/analysis/MoversDocs.java . <RUN_TS>
 * Reads the Maven output of each test, generated/runs/<RUN_TS>/movers/raw/<Test>Test-coh-{off,on}.txt, and meta.txt there
 * (instructions.md M3); writes <RUN_TS>-<Test>Analysis.adoc and <RUN_TS>-Summary.adoc to
 * generated/runs/<RUN_TS>/movers/reports/, PNGs to reports/images/ and Mermaid sources to reports/mermaid/
 * (Layout.java, instructions.md L). MOVERS_RESULTS=<folder> reads the raw files from another folder.
 * Set MMDC=<path to mmdc> to parse-check every .mmd (optional).
 */
public class MoversDocs
{
    private static String root;
    private static String runTs;
    private static Layout layout;
    private static String srcRel;
    private static Path src;
    private static Path out;      // analysis docs
    private static Path mmdDir;   // Mermaid sources
    private static Path ch;       // PNGs
    private static final String IMG_LINK = Layout.IMG_LINK;   // relative to out
    private static final String MMD_LINK = Layout.MMD_LINK;
    // The Summary section of each analysis, for the run summary
    private static final Map<String, List<String>> SUMMARIES = new LinkedHashMap<>();
    private static String mmdc;
    private static final String[] CFGS = {"coh-off", "coh-on"};
    private static final int[] SIZES = new int[10];
    private static final int[] SHOW = {100_000, 500_000, 1_000_000};  // sizes shown as bar groups
    private static final int M = 1_000_000;

    static
    {
        for (int i = 0; i < SIZES.length; i++)
        {
            SIZES[i] = 100_000 * (i + 1);
        }
    }

    // Okabe-Ito, one colour per kind (instructions.md M4.5); a second member of a kind gets a hatch.
    private static final String JDK = "#E69F00";
    private static final String EC_BOXED = "#56B4E9";
    private static final String EC_PRIM = "#0072B2";
    private static final String EC_MIXED = "#CC79A7";
    private static final String EC_LAZY = "#CC79A7";
    private static final String OFF = "#999999";
    private static final String ON = "#0072B2";

    // label in the results file -> {legend text, colour, hatch}
    private static final Map<String, String[]> STYLE = new LinkedHashMap<>();

    static
    {
        style("ArrayList", "ArrayList (JDK), grown", JDK, "");
        style("Presized ArrayList", "ArrayList (JDK), presized", JDK, "//");
        style("FastList", "FastList (EC), grown", EC_BOXED, "");
        style("Presized FastList", "FastList (EC), presized", EC_BOXED, "//");
        style("IntArrayList", "IntArrayList (EC primitive), grown", EC_PRIM, "");
        style("PresizedIntArrayList", "IntArrayList (EC primitive), presized", EC_PRIM, "//");
        style("HashSet", "HashSet (JDK)", JDK, "");
        style("UnifiedSet", "UnifiedSet (EC)", EC_BOXED, "");
        style("IntHashSet", "IntHashSet (EC primitive)", EC_PRIM, "");
        style("Only HashSet", "HashSet (JDK), structure only", JDK, "..");
        style("Only UnifiedSet", "UnifiedSet (EC), structure only", EC_BOXED, "..");
        style("HashMap", "HashMap<Integer, Integer> (JDK)", JDK, "");
        style("UnifiedMap", "UnifiedMap<Integer, Integer> (EC)", EC_BOXED, "");
        style("IntObjectHashMap", "IntObjectHashMap<Integer> (EC, primitive key)", EC_MIXED, "");
        style("ObjectIntHashMap", "ObjectIntHashMap<Integer> (EC, primitive value)", EC_MIXED, "//");
        style("IntIntHashMap", "IntIntHashMap (EC primitive)", EC_PRIM, "");
        style("Only HashMap", "HashMap (JDK), structure only", JDK, "..");
        style("Only UnifiedMap", "UnifiedMap (EC), structure only", EC_BOXED, "..");
    }

    private static void style(String label, String legend, String colour, String hatch)
    {
        STYLE.put(label, new String[]{legend, colour, hatch});
    }

    private static String[] styleOf(String label)
    {
        return STYLE.getOrDefault(label, new String[]{label, JDK, ""});
    }

    /** One row of the pipeline test: JDK pipeline and bytes, EC pipeline and bytes. */
    private record Row(String jdk, long jdkBytes, String ec, long ecBytes)
    {
    }

    public static void main(String[] args) throws IOException, InterruptedException
    {
        root = args[0];
        runTs = args[1];
        layout = Layout.movers(root, runTs);
        srcRel = layout.rawRel;
        src = layout.raw;
        out = layout.reports;
        mmdDir = layout.mermaid;
        ch = layout.images;
        mmdc = System.getenv("MMDC");
        if (mmdc != null && mmdc.isEmpty())
        {
            mmdc = null;
        }

        lists();
        hashed("SetMemory", "Sets", List.of("HashSet", "UnifiedSet", "IntHashSet"),
                List.of("Only HashSet", "Only UnifiedSet", "IntHashSet"),
                "`SetMemoryTest` fills each set with the `Integer`s (or `int`s) 1 to n, for n = 100,000 to 1,000,000. The "
                        + "\"structure only\" rows subtract 16 B per `Integer`, leaving the set's own tables and entry objects.",
                "Sets: JDK HashSet vs EC UnifiedSet and IntHashSet", MoversDocs::setSummary);
        hashed("MapMemory", "Maps", List.of("HashMap", "UnifiedMap", "IntObjectHashMap", "ObjectIntHashMap", "IntIntHashMap"),
                List.of("Only HashMap", "Only UnifiedMap", "IntIntHashMap"),
                "`MapMemoryTest` fills each map with n entries mapping i to i, for n = 100,000 to 1,000,000. In the boxed maps the "
                        + "key and the value are the same `Integer` object, so each map holds one `Integer` per entry; the \"structure only\" "
                        + "rows subtract them.",
                "Maps: JDK HashMap vs EC UnifiedMap and primitive maps", MoversDocs::mapSummary);
        pipelines();
        runSummary();
    }

    // ---------------------------------------------------------------- reading the results files

    private static String read(String test, String cfg) throws IOException
    {
        return Files.readString(src.resolve(test + "Test-" + cfg + ".txt"), StandardCharsets.UTF_8);
    }

    /** {cfg: {label: {size: bytes}}} for the size tests. */
    private static Map<String, Map<String, Map<Integer, Long>>> load(String test) throws IOException
    {
        Pattern line = Pattern.compile("^(.+?) size: (\\d+) Memory \\(bytes\\): (\\d+)$", Pattern.MULTILINE);
        Map<String, Map<String, Map<Integer, Long>>> result = new LinkedHashMap<>();
        for (String cfg : CFGS)
        {
            Map<String, Map<Integer, Long>> d = new LinkedHashMap<>();
            Matcher m = line.matcher(read(test, cfg));
            while (m.find())
            {
                d.computeIfAbsent(m.group(1), k -> new LinkedHashMap<>())
                        .put(Integer.parseInt(m.group(2)), Long.parseLong(m.group(3)));
            }
            result.put(cfg, d);
        }
        return result;
    }

    /** {cfg: [row]} for the pipeline test. */
    private static Map<String, List<Row>> loadRows(String test) throws IOException
    {
        Map<String, List<Row>> result = new LinkedHashMap<>();
        for (String cfg : CFGS)
        {
            List<Row> rows = new ArrayList<>();
            for (String line : read(test, cfg).split("\n", -1))
            {
                String[] p = line.split("\\|", -1);
                if (line.startsWith("`") && p.length >= 4)
                {
                    rows.add(new Row(stripTicks(p[0].strip()), Long.parseLong(p[1].strip()),
                            stripTicks(p[2].strip()), Long.parseLong(p[3].strip())));
                }
            }
            result.put(cfg, rows);
        }
        return result;
    }

    private static String stripTicks(String s)
    {
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) == '`')
        {
            start++;
        }
        while (end > start && s.charAt(end - 1) == '`')
        {
            end--;
        }
        return s.substring(start, end);
    }

    private static String mb(double b)
    {
        return Py.f(b / 1e6, 1, true) + " MB";
    }

    // ---------------------------------------------------------------- charts

    private static void mmd(Path path, String title, String ylabel, List<String> cats, List<Double> vals)
            throws IOException, InterruptedException
    {
        List<String> padded = new ArrayList<>();
        for (String c : cats)
        {
            padded.add(" ".repeat(14) + c);  // NBSP pad: Mermaid under-measures label width
        }
        double max = vals.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        List<String> quoted = new ArrayList<>();
        for (String c : padded)
        {
            quoted.add("\"" + c + "\"");
        }
        List<String> bars = new ArrayList<>();
        for (double v : vals)
        {
            bars.add(Py.g(v, 4));
        }
        List<String> lines = List.of("---", "config:", "  xyChart:", "    width: 1400",
                "    height: " + Math.max(500, 30 * padded.size() + 140),
                "    chartOrientation: horizontal", "    xAxis:", "      labelFontSize: 13", "  themeVariables:", "    xyChart:",
                "      plotColorPalette: \"#0072B2\"", "---",
                "%% Generated by MoversDocs.java. The PNG next to this file is the same data with colours and a legend.",
                "xychart-beta", "  title \"" + title + "\"",
                "  x-axis [" + String.join(", ", quoted) + "]",
                "  y-axis \"" + ylabel + "\" 0 --> " + Py.g(max * 1.1, 4),
                "  bar [" + String.join(", ", bars) + "]");
        Files.writeString(path, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        if (mmdc != null)
        {
            List<String> command = new ArrayList<>(List.of(mmdc, "-i", path.toString(), "-o", "/tmp/_movers_check.svg", "-q"));
            String pp = System.getenv("MMDC_PP");
            if (pp != null && !pp.isEmpty())
            {
                command.addAll(List.of("-p", pp));
            }
            int exit = new ProcessBuilder(command).inheritIO().start().waitFor();
            if (exit != 0)
            {
                throw new IllegalStateException("mmdc failed on " + path);
            }
        }
    }

    // Title, subtitle, legend and footnote, then the PNG (matplotlib finish())
    private static void finish(Plot plot, String title, String subtitle, String name, List<Plot.Entry> legend, int ncol,
            String foot) throws IOException
    {
        plot.title(title);
        plot.subtitle(Py.wrap(subtitle, 130));
        plot.legend(legend, ncol, 9.5);
        if (foot != null)
        {
            plot.footnote(Py.wrap(foot, 150));
        }
        plot.save(ch.resolve(name + ".png"));
    }

    /** Vertical grouped bars: one group per size (or per groups), one bar per label. */
    private static String grouped(String test, String key, List<String> labels, Map<String, double[]> values,
            Map<String, String[]> text, String title, String subtitle, String ylabel, String foot, List<String> groups)
            throws IOException, InterruptedException
    {
        if (groups == null)
        {
            groups = new ArrayList<>();
            for (int s : SHOW)
            {
                groups.add(Py.grouped(s) + " elements");
            }
        }
        int n = labels.size();
        Plot plot = new Plot(12, 6.2, false);
        double w = 0.8 / n;
        double vmax = -Double.MAX_VALUE;
        for (String l : labels)
        {
            for (double v : values.get(l))
            {
                vmax = Math.max(vmax, v);
            }
        }
        List<Plot.Entry> legend = new ArrayList<>();
        for (int i = 0; i < n; i++)
        {
            String l = labels.get(i);
            String[] st = styleOf(l);
            Color colour = Plot.colour(st[1]);
            for (int g = 0; g < groups.size(); g++)
            {
                double x = g + (i - (n - 1) / 2.0) * w;
                double v = values.get(l)[g];
                plot.bar(x - w * 0.46, x + w * 0.46, 0, v, colour, st[2]);
                plot.text(x, v, text.get(l)[g], 8.5, Color.BLACK, false, "center", "bottom", n > 3, null, 3);
            }
            legend.add(new Plot.Entry(st[0], colour, st[2]));
        }
        double[] positions = new double[groups.size()];
        for (int g = 0; g < positions.length; g++)
        {
            positions[g] = g;
        }
        plot.categories(positions, groups.toArray(new String[0]), 11);
        plot.numberAxisLabel(ylabel);
        plot.limits(0, vmax * (n > 3 ? 1.45 : 1.25));
        String name = runTs + "-" + test + "-" + key;
        finish(plot, title, subtitle, name, legend, 1, foot);
        List<String> cats = new ArrayList<>();
        List<Double> vals = new ArrayList<>();
        for (int g = 0; g < groups.size(); g++)
        {
            for (String l : labels)
            {
                cats.add(groups.get(g) + ": " + styleOf(l)[0] + " = " + text.get(l)[g]);
                vals.add(values.get(l)[g]);
            }
        }
        mmd(mmdDir.resolve(name + ".mmd"), title, ylabel, cats, vals);
        return name;
    }

    /** Horizontal pairs: compact headers off (grey) vs on (dark blue) for each label, with the saving. */
    private static String cohBars(String test, String key, List<String> labels, Map<String, Long> off, Map<String, Long> on,
            String title, String subtitle, String unit) throws IOException, InterruptedException
    {
        Plot plot = new Plot(12, 1.4 + 0.7 * labels.size(), true);
        double h = 0.38;
        double scale = unit.equals("MB") ? 1e6 : 1;
        LongFunction<String> fmt = unit.equals("MB") ? v -> Py.f(v / 1e6, 1, true) + " MB" : v -> Py.f(v, 0, true) + " B";
        double top = -Double.MAX_VALUE;
        for (String l : labels)
        {
            top = Math.max(top, off.get(l) / scale);
        }
        double[] positions = new double[labels.size()];
        String[] names = new String[labels.size()];
        for (int i = 0; i < labels.size(); i++)
        {
            String l = labels.get(i);
            double y = labels.size() - 1 - i;
            positions[i] = y;
            names[i] = styleOf(l)[0];
            double vOff = off.get(l) / scale;
            double vOn = on.get(l) / scale;
            plot.bar(y, y + h, 0, vOff, Plot.colour(OFF), "");
            plot.bar(y - h, y, 0, vOn, Plot.colour(ON), "");
            plot.text(y + h / 2, vOff * 1.01, fmt.apply(off.get(l)), 9, Color.BLACK, false, "left", "center", false, null, 0);
            plot.text(y - h / 2, vOn * 1.01, fmt.apply(on.get(l)), 9, Color.BLACK, false, "left", "center", false, null, 0);
            long d = off.get(l) - on.get(l);
            String txt = (double) d / off.get(l) >= 0.005
                    ? "saves " + fmt.apply(d) + " (" + Py.f((double) d / off.get(l) * 100, 0) + "%)"
                    : "no change";
            plot.text(y, top * 1.17, txt, 9.5, Plot.colour(txt.equals("no change") ? "#555555" : ON), true, "left", "center",
                    false, null, 0);
        }
        plot.categories(positions, names, 10);
        plot.limits(0, top * 1.55);
        plot.numberAxisLabel("retained size (" + unit + ")");
        String name = runTs + "-" + test + "-" + key;
        finish(plot, title, subtitle, name, List.of(new Plot.Entry("compact headers off", Plot.colour(OFF), ""),
                new Plot.Entry("compact headers on (-XX:+UseCompactObjectHeaders)", Plot.colour(ON), "")), 2, null);
        List<String> cats = new ArrayList<>();
        List<Double> vals = new ArrayList<>();
        for (String l : labels)
        {
            cats.add(styleOf(l)[0] + ", headers off = " + fmt.apply(off.get(l)));
            vals.add(off.get(l) / scale);
            cats.add(styleOf(l)[0] + ", headers on = " + fmt.apply(on.get(l)));
            vals.add(on.get(l) / scale);
        }
        mmd(mmdDir.resolve(name + ".mmd"), title, "retained size (" + unit + ")", cats, vals);
        return name;
    }

    // ---------------------------------------------------------------- doc parts

    private static List<String> embed(String name, String caption)
    {
        return List.of("." + caption, "image::" + IMG_LINK + "/" + name + ".png[" + caption + "]",
                "Mermaid source: link:" + MMD_LINK + "/" + name + ".mmd[" + name + ".mmd]", "");
    }

    /** Every size, both configurations. */
    private static List<String> table(Map<String, Map<String, Map<Integer, Long>>> d, List<String> labels)
    {
        List<String> a = new ArrayList<>(List.of("[cols=\"3,1,1,1,1\",options=\"header\"]", "|===",
                "|Collection |Elements |Headers off (B) |Headers on (B) |B per element (on)"));
        for (String l : labels)
        {
            for (int s : SIZES)
            {
                long off = d.get("coh-off").get(l).get(s);
                long on = d.get("coh-on").get(l).get(s);
                a.add("|" + styleOf(l)[0] + " |" + Py.grouped(s) + " |" + Py.grouped(off) + " |" + Py.grouped(on) + " |"
                        + Py.f((double) on / s, 1));
            }
        }
        a.add("|===");
        a.add("");
        return a;
    }

    private static List<String> header(String test, String title, String intro) throws IOException
    {
        return new ArrayList<>(List.of("= " + title + " (run " + runTs + ")", ":toc:", ":toclevels: 2", "",
                intro + " Sizes are JOL retained sizes (`GraphLayout.parseInstance(x).totalSize()`): every object reachable "
                        + "from the collection, measured once it is filled. They show memory *footprint*, not what building the "
                        + "collection allocated along the way, and not speed. " + "Environment: " + env(test) + ". "
                        + "JOL sizes depend on the JVM layout (compressed references, object alignment, header size), not the "
                        + "machine. The raw files are `" + srcRel + "/" + test + "Test-coh-off.txt` and "
                        + "`" + test + "Test-coh-on.txt` (the Maven output of the test); "
                        + (Files.exists(src.resolve("meta.txt")) ? "`meta.txt` in the same folder has the JDK and the machine."
                                : "they start with the JDK, JOL and Eclipse Collections versions and the full JVM details."),
                ""));
    }

    /** Environment and JDK from meta.txt in the raw folder or, for older results files, from their HTML comment header. */
    private static String env(String test) throws IOException
    {
        Path meta = src.resolve("meta.txt");
        if (Files.exists(meta))
        {
            String text = Files.readString(meta, StandardCharsets.UTF_8);
            return Layout.environment(text) + " (" + Layout.javaVersion(text) + ")";
        }
        String head = read(test, "coh-on").split("-->", -1)[0];
        List<String> lines = new ArrayList<>();
        for (String l : head.replace("<!--", "").split("\\r?\\n|\\r", -1))
        {
            if (!l.strip().isEmpty())
            {
                lines.add(l.strip());
            }
        }
        String where = null;
        for (String l : lines)
        {
            if (l.startsWith("environment:"))
            {
                where = l.substring(l.indexOf(':') + 1).strip();
                break;
            }
        }
        if (where == null)
        {
            where = lines.isEmpty() ? "not recorded" : lines.get(0);
        }
        String java = "JDK version not recorded";
        for (String l : lines)
        {
            if (l.contains("version \""))
            {
                java = l;
                break;
            }
        }
        return where + " (" + java + ")";
    }

    private static List<String> footer(String test)
    {
        return List.of("== Caveats", "",
                "* JOL counts every reachable object once. Values 1 to 127 come from the `Integer` cache and are shared; with "
                        + "100,000 or more elements that is under 0.13% of the `Integer`s.",
                "* Retained size includes unused capacity (empty array slots), so a grown collection is larger than a presized one.",
                "* Layout explanations marked \"derived\" are arithmetic from the measured totals and the JVM details, not "
                        + "separate measurements.", "",
                "== Reproduce", "", "....",
                "mvn test -Dtest=" + test + "Test -Dsurefire.failIfNoSpecifiedTests=false \\",
                "  -DargLine=\"-XX:-UseCompactObjectHeaders -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading\"",
                "  > " + srcRel + "/" + test + "Test-coh-off.txt 2>&1",
                "# then again with -XX:+UseCompactObjectHeaders into " + test + "Test-coh-on.txt",
                "java src/main/java/tools/analysis/MoversDocs.java . " + runTs, "....", "");
    }

    private static void write(String test, List<String> lines) throws IOException
    {
        Path path = Path.of(out + "/" + runTs + "-" + test + "Analysis.adoc");
        Files.writeString(path, String.join("\n", lines), StandardCharsets.UTF_8);
        System.out.println(path);
        // keep the Summary section (up to the next heading) for the run summary
        int start = lines.indexOf("== Summary");
        List<String> summary = new ArrayList<>();
        for (int i = start + 1; start >= 0 && i < lines.size() && !lines.get(i).startsWith("=="); i++)
        {
            if (!summary.isEmpty() || !lines.get(i).isEmpty())
            {
                summary.add(lines.get(i));
            }
        }
        SUMMARIES.put(test, summary);
    }

    /** reports/<RUN_TS>-Summary.adoc: the Summary of every test, with links to the analyses. */
    private static void runSummary() throws IOException
    {
        List<String> s = new ArrayList<>(List.of("= Movers run " + runTs + ": summary", ":toc:", "",
                "JOL retained sizes (memory footprint) of JDK and Eclipse Collections collections, compact object headers off "
                        + "and on. Written from the run's numbers by `MoversDocs.java`; each test has its own analysis with the "
                        + "tables and charts.", ""));
        for (Map.Entry<String, List<String>> e : SUMMARIES.entrySet())
        {
            s.addAll(List.of("== " + e.getKey() + "Test", "", "link:" + runTs + "-" + e.getKey() + "Analysis.adoc[Full analysis]", ""));
            s.addAll(e.getValue());
        }
        Path path = Path.of(out + "/" + runTs + "-Summary.adoc");
        Files.writeString(path, String.join("\n", s), StandardCharsets.UTF_8);
        System.out.println(path);
    }

    // ---------------------------------------------------------------- lists

    private static void lists() throws IOException, InterruptedException
    {
        String t = "ListMemory";
        Map<String, Map<String, Map<Integer, Long>>> d = load(t);
        Map<String, Map<Integer, Long>> on = d.get("coh-on");
        Map<String, Map<Integer, Long>> off = d.get("coh-off");
        List<String> labels = List.of("ArrayList", "Presized ArrayList", "FastList", "Presized FastList", "IntArrayList",
                "PresizedIntArrayList");
        Map<String, double[]> v = new LinkedHashMap<>();
        Map<String, String[]> text = new LinkedHashMap<>();
        for (String l : labels)
        {
            double[] vs = new double[SHOW.length];
            String[] ts = new String[SHOW.length];
            for (int i = 0; i < SHOW.length; i++)
            {
                vs[i] = on.get(l).get(SHOW[i]) / 1e6;
                ts[i] = mb(on.get(l).get(SHOW[i]));
            }
            v.put(l, vs);
            text.put(l, ts);
        }
        String c1 = grouped(t, "footprint", labels, v, text,
                "Lists of Integers: memory held once filled, JDK vs Eclipse Collections",
                "Lower is better. At 1,000,000 elements a boxed list holds " + mb(on.get("Presized ArrayList").get(M)) + " "
                        + "(16 B Integer + 4 B reference per element); EC IntArrayList holds " + mb(on.get("PresizedIntArrayList").get(M)) + " "
                        + "(4 B int per element), 5x less. ArrayList and FastList are the same size.",
                "retained size (MB), compact headers on", null, null);
        Map<String, double[]> slack = new LinkedHashMap<>();
        String[][] pairs = {{"ArrayList", "Presized ArrayList"}, {"FastList", "Presized FastList"},
                {"IntArrayList", "PresizedIntArrayList"}};
        for (String[] pair : pairs)
        {
            double[] s = new double[SIZES.length];
            for (int i = 0; i < SIZES.length; i++)
            {
                s[i] = ((double) on.get(pair[0]).get(SIZES[i]) / on.get(pair[1]).get(SIZES[i]) - 1) * 100;
            }
            slack.put(pair[0], s);
        }
        List<String> g = List.of("ArrayList", "FastList", "IntArrayList");
        Map<String, String[]> slackText = new LinkedHashMap<>();
        for (String l : g)
        {
            String[] ts = new String[SIZES.length];
            for (int i = 0; i < SIZES.length; i++)
            {
                ts[i] = Py.f(slack.get(l)[i], 0) + "%";
            }
            slackText.put(l, ts);
        }
        List<String> kGroups = new ArrayList<>();
        for (int s : SIZES)
        {
            kGroups.add(s / 1000 + "k");
        }
        String c2 = grouped(t, "growth-slack", g, slack, slackText,
                "Lists grown one element at a time: extra memory compared with a presized list",
                "Lower is better. A list that grows keeps unused capacity after its last resize. ArrayList grows by "
                        + "1.5x; FastList and IntArrayList by their own steps, so the waste jumps at different sizes. "
                        + "Worst case here: IntArrayList at 700,000 elements, " + Py.f(slack.get("IntArrayList")[6], 0) + "% larger than presized.",
                "% larger than the presized list", null, kGroups);
        double maxSlack = -Double.MAX_VALUE;
        for (double[] x : slack.values())
        {
            for (double y : x)
            {
                maxSlack = Math.max(maxSlack, y);
            }
        }
        List<String> a = header(t, "Lists: JDK ArrayList vs EC FastList and IntArrayList",
                "`ListMemoryTest` fills each list with the `Integer`s (or `int`s) 1 to n, for n = 100,000 to 1,000,000, "
                        + "once growing it from empty and once presized to n.");
        a.addAll(List.of("== Summary", "",
                "* *Boxing is the cost.* At 1,000,000 elements a presized `ArrayList` or `FastList` holds " + mb(on.get("Presized ArrayList").get(M)) + "; "
                        + "`IntArrayList` holds " + mb(on.get("PresizedIntArrayList").get(M)) + ", 5x less. Per element that is 20 B (a 16 B `Integer` "
                        + "plus a 4 B reference in the array) against 4 B (derived).",
                "* *`ArrayList` and `FastList` are the same size* (within 8 B) when presized: both are a small object around one `Object[]`.",
                "* *Growing instead of presizing wastes up to " + Py.f(maxSlack, 0) + "%* (unused capacity after "
                        + "the last resize). The policies differ: `ArrayList` grows by 1.5x, so its waste is spread evenly; `FastList` and "
                        + "`IntArrayList` grow in bigger steps.",
                "* *Compact headers change almost nothing here:* a 16 B `Integer` stays 16 B (12 B header + 4 B `int` without compact "
                        + "headers, 8 B + 4 B padded to 16 B with them), so only the list object itself shrinks, by 0 to 8 B (derived).", ""));
        a.addAll(List.of("== Memory held once filled", ""));
        a.addAll(embed(c1, "Retained size of each list, compact headers on"));
        a.addAll(List.of("== Growing vs presizing", ""));
        a.addAll(embed(c2, "Extra memory of a grown list compared with a presized one"));
        a.addAll(List.of("[cols=\"1,1,1,1\",options=\"header\"]", "|===", "|Elements |ArrayList |FastList |IntArrayList"));
        for (int i = 0; i < SIZES.length; i++)
        {
            List<String> cells = new ArrayList<>();
            for (String l : g)
            {
                cells.add(Py.f(slack.get(l)[i], 1) + "%");
            }
            a.add("|" + Py.grouped(SIZES[i]) + " |" + String.join(" |", cells));
        }
        a.addAll(List.of("|===", "",
                "A grown list also threw away every smaller array it outgrew. JOL does not see that garbage; it shows only "
                        + "the final list.", "",
                "== Compact headers off vs on (1,000,000 elements)", "",
                "[cols=\"3,1,1,1\",options=\"header\"]", "|===", "|List |Headers off (B) |Headers on (B) |Saved (B)"));
        for (String l : labels)
        {
            long dOff = off.get(l).get(M);
            long dOn = on.get(l).get(M);
            a.add("|" + STYLE.get(l)[0] + " |" + Py.grouped(dOff) + " |" + Py.grouped(dOn) + " |" + (dOff - dOn));
        }
        a.addAll(List.of("|===", "", "== Every measurement", ""));
        a.addAll(table(d, labels));
        a.addAll(footer(t));
        write(t, a);
    }

    // ---------------------------------------------------------------- sets and maps

    private static void hashed(String t, String kind, List<String> full, List<String> only, String intro, String title,
            BiFunction<Map<String, Map<Integer, Long>>, Map<String, Map<Integer, Long>>, List<String>> summary)
            throws IOException, InterruptedException
    {
        String unit = kind.equals("Maps") ? "entries" : "elements";
        Map<String, Map<String, Map<Integer, Long>>> d = load(t);
        Map<String, Map<Integer, Long>> on = d.get("coh-on");
        Map<String, Map<Integer, Long>> off = d.get("coh-off");
        List<String> unitGroups = new ArrayList<>();
        for (int s : SHOW)
        {
            unitGroups.add(Py.grouped(s) + " " + unit);
        }
        List<String> atMillion = new ArrayList<>();
        for (String l : full)
        {
            atMillion.add(STYLE.get(l)[0].split(" \\(", -1)[0] + " " + mb(on.get(l).get(M)));
        }
        String c1 = grouped(t, "footprint", full, sizes(on, full), sizeTexts(on, full),
                kind + " of Integers: memory held once filled, JDK vs Eclipse Collections",
                "Lower is better. At 1,000,000 " + unit + ": " + String.join(", ", atMillion) + ". Compact headers on.",
                "retained size (MB), compact headers on", null, unitGroups);
        String singular = kind.toLowerCase().substring(0, kind.length() - 1);
        String c2 = grouped(t, "structure-only", only, sizes(on, only), sizeTexts(on, only),
                kind + ": memory for the " + singular + " itself, without the Integers it holds",
                "Lower is better. The Integers are subtracted (16 B each), leaving tables and entry objects. The JDK "
                        + (kind.equals("Sets") ? "HashSet wraps a HashMap, which" : "HashMap") + " makes one entry object per element; "
                        + "Eclipse Collections stores elements directly in its table.",
                "retained size (MB) without the Integers, compact headers on", null, unitGroups);
        List<String> labels = new ArrayList<>(full);
        for (String l : only)
        {
            if (!full.contains(l))
            {
                labels.add(l);
            }
        }
        Map<String, Long> offM = new LinkedHashMap<>();
        Map<String, Long> onM = new LinkedHashMap<>();
        for (String l : labels)
        {
            offM.put(l, off.get(l).get(M));
            onM.put(l, on.get(l).get(M));
        }
        String c3 = cohBars(t, "coh", labels, offM, onM,
                kind + " with 1,000,000 " + unit + ": compact object headers off vs on",
                "Lower is better. Compact headers shrink every object header from 12 to 8 bytes. Only the JDK "
                        + (kind.equals("Sets") ? "HashSet" : "HashMap") + " gets smaller, because only it has one object per entry; "
                        + "the Integers (16 B either way) and the arrays do not change.", "MB");
        List<String> a = header(t, title, intro);
        a.addAll(List.of("== Summary", ""));
        a.addAll(summary.apply(on, off));
        a.add("");
        a.addAll(List.of("== Memory held once filled", ""));
        a.addAll(embed(c1, "Retained size of each " + singular + ", compact headers on"));
        a.addAll(List.of("== The structure alone", ""));
        a.addAll(embed(c2, "Retained size without the Integers, compact headers on"));
        a.addAll(List.of("== Compact headers off vs on", ""));
        a.addAll(embed(c3, "Retained size at 1,000,000 " + unit + ", compact headers off vs on"));
        a.addAll(List.of("[cols=\"3,1,1,1\",options=\"header\"]", "|===",
                "|Collection (1,000,000 " + unit + ") |Headers off (B) |Headers on (B) |Saved"));
        for (String l : labels)
        {
            long o = off.get(l).get(M);
            long n = on.get(l).get(M);
            a.add("|" + STYLE.get(l)[0] + " |" + Py.grouped(o) + " |" + Py.grouped(n) + " |" + Py.grouped(o - n) + " B ("
                    + Py.f((double) (o - n) / o * 100, 0) + "%)");
        }
        a.addAll(List.of("|===", "", "== Every measurement", ""));
        a.addAll(table(d, labels));
        a.addAll(footer(t));
        write(t, a);
    }

    private static Map<String, double[]> sizes(Map<String, Map<Integer, Long>> on, List<String> labels)
    {
        Map<String, double[]> v = new LinkedHashMap<>();
        for (String l : labels)
        {
            double[] vs = new double[SHOW.length];
            for (int i = 0; i < SHOW.length; i++)
            {
                vs[i] = on.get(l).get(SHOW[i]) / 1e6;
            }
            v.put(l, vs);
        }
        return v;
    }

    private static Map<String, String[]> sizeTexts(Map<String, Map<Integer, Long>> on, List<String> labels)
    {
        Map<String, String[]> text = new LinkedHashMap<>();
        for (String l : labels)
        {
            String[] ts = new String[SHOW.length];
            for (int i = 0; i < SHOW.length; i++)
            {
                ts[i] = mb(on.get(l).get(SHOW[i]));
            }
            text.put(l, ts);
        }
        return text;
    }

    private static List<String> setSummary(Map<String, Map<Integer, Long>> on, Map<String, Map<Integer, Long>> off)
    {
        return List.of("* *The JDK `HashSet` holds twice as much as `UnifiedSet`:* " + mb(on.get("HashSet").get(M)) + " against " + mb(on.get("UnifiedSet").get(M)) + " "
                        + "at 1,000,000 `Integer`s. `IntHashSet` holds " + mb(on.get("IntHashSet").get(M)) + ", about 6x less than `HashSet`.",
                "* *Without the `Integer`s the gap is 4x:* `HashSet` itself takes " + mb(on.get("Only HashSet").get(M)) + ", `UnifiedSet` "
                        + mb(on.get("Only UnifiedSet").get(M)) + ". `HashSet` is a `HashMap` that makes a `HashMap$Node` per element (24 B with "
                        + "compact headers, 32 B without) next to its table; `UnifiedSet` puts the element straight into its table (derived: "
                        + "1,000,000 × 24 B + 2^21 slots × 4 B ≈ 32.4 MB).",
                "* *`IntHashSet` is the same size as `UnifiedSet` without its `Integer`s* (" + mb(on.get("IntHashSet").get(M)) + "): an `int[]` "
                        + "instead of an `Object[]` of the same length, and no boxes.",
                "* *Compact headers save " + mb(off.get("HashSet").get(M) - on.get("HashSet").get(M)) + " on `HashSet`* (one 8 B saving per node) "
                        + "and nothing on the EC sets, which have no per-element objects besides the `Integer`s.",
                "* *The tables double at different sizes:* `HashSet` and `UnifiedSet` at 200k, 400k and 800k elements, "
                        + "`IntHashSet` at 200k, 300k and 600k, so it keeps more empty slots (a lower load factor, inferred from the steps).");
    }

    private static List<String> mapSummary(Map<String, Map<Integer, Long>> on, Map<String, Map<Integer, Long>> off)
    {
        return List.of("* *`HashMap<Integer, Integer>` holds " + mb(on.get("HashMap").get(M)) + "; `UnifiedMap` holds " + mb(on.get("UnifiedMap").get(M)) + "* "
                        + "at 1,000,000 entries (the key and the value are the same `Integer`, so each map holds one `Integer` per entry). "
                        + "`IntIntHashMap` holds " + mb(on.get("IntIntHashMap").get(M)) + ", about 3x less than `HashMap`.",
                "* *Without the `Integer`s:* `HashMap` itself takes " + mb(on.get("Only HashMap").get(M)) + ", `UnifiedMap` "
                        + mb(on.get("Only UnifiedMap").get(M)) + ". `HashMap` makes a `HashMap$Node` per entry (24 B with compact headers, 32 B "
                        + "without) next to its table; `UnifiedMap` stores key and value side by side in one `Object[]` (derived: "
                        + "2^22 slots × 4 B ≈ 16.8 MB).",
                "* *Half-primitive maps do not help here:* `IntObjectHashMap` and `ObjectIntHashMap` hold "
                        + mb(on.get("IntObjectHashMap").get(M)) + ", the same as `UnifiedMap`, because one side is still a boxed `Integer` and the "
                        + "other side's array is as long as before. Only `IntIntHashMap`, with no boxes at all, is smaller.",
                "* *Compact headers save " + mb(off.get("HashMap").get(M) - on.get("HashMap").get(M)) + " on `HashMap`* (one 8 B saving per node) "
                        + "and nothing on the EC maps.");
    }

    // ---------------------------------------------------------------- pipelines

    private static void pipelines() throws IOException, InterruptedException
    {
        String t = "StreamVsLazyIterableMemory";
        Map<String, List<Row>> d = loadRows(t);
        List<Row> rowsOn = d.get("coh-on");
        List<Row> rowsOff = d.get("coh-off");
        String name = runTs + "-" + t + "-pipeline-size";
        Plot plot = new Plot(12, 1.6 + 0.62 * rowsOn.size(), true);
        double h = 0.38;
        double[] positions = new double[rowsOn.size()];
        String[] names = new String[rowsOn.size()];
        long maxJdk = 0;
        for (int i = 0; i < rowsOn.size(); i++)
        {
            Row r = rowsOn.get(i);
            double y = rowsOn.size() - 1 - i;
            positions[i] = y;
            names[i] = r.jdk().replace("Stream.", "") + "  vs  " + r.ec().replace("LazyIterable.", "");
            plot.bar(y, y + h, 0, r.jdkBytes(), Plot.colour(JDK), "");
            plot.bar(y - h, y, 0, r.ecBytes(), Plot.colour(EC_LAZY), "");
            plot.text(y + h / 2, r.jdkBytes() + 2, Py.f(r.jdkBytes(), 0) + " B", 9, Color.BLACK, false, "left", "center", false, null, 0);
            plot.text(y - h / 2, r.ecBytes() + 2, Py.f(r.ecBytes(), 0) + " B", 9, Color.BLACK, false, "left", "center", false, null, 0);
            maxJdk = Math.max(maxJdk, r.jdkBytes());
        }
        plot.categories(positions, names, 9.5);
        plot.limits(0, maxJdk * 1.2);
        plot.numberAxisLabel("bytes in the unevaluated pipeline (compact headers on)");
        Row first = rowsOn.get(0);
        Row second = rowsOn.get(1);
        Row last = rowsOn.get(rowsOn.size() - 1);
        finish(plot, "Size of a lazy pipeline before it runs: JDK Stream vs Eclipse Collections LazyIterable",
                "Lower is better. Starting costs " + first.jdkBytes() + " B for stream() against " + first.ecBytes() + " B for asLazy(); with one "
                        + "operation the pipeline is " + second.jdkBytes() + " B against " + second.ecBytes() + " B. A 3-step chain: " + last.jdkBytes() + " B vs "
                        + last.ecBytes() + " B. Counts include the lambdas, which are shared constants, so this is pipeline size, not bytes per call.",
                name, List.of(new Plot.Entry("JDK Stream", Plot.colour(JDK), ""),
                        new Plot.Entry("Eclipse Collections LazyIterable", Plot.colour(EC_LAZY), "")), 2, null);
        List<String> cats = new ArrayList<>();
        List<Double> vals = new ArrayList<>();
        for (Row r : rowsOn)
        {
            cats.add(r.jdk() + " = " + r.jdkBytes() + " B");
            cats.add(r.ec() + " = " + r.ecBytes() + " B");
            vals.add((double) r.jdkBytes());
            vals.add((double) r.ecBytes());
        }
        mmd(mmdDir.resolve(name + ".mmd"), "Size of a lazy pipeline before it runs: JDK Stream vs Eclipse Collections LazyIterable",
                "bytes (compact headers on)", cats, vals);
        for (int i = 0; i < rowsOn.size(); i++)
        {
            style("jdk" + i, rowsOn.get(i).jdk(), JDK, "");
            style("ec" + i, rowsOn.get(i).ec(), EC_LAZY, "");
        }
        Map<String, Long> offd = new LinkedHashMap<>();
        Map<String, Long> ond = new LinkedHashMap<>();
        long jdMin = Long.MAX_VALUE;
        long jdMax = Long.MIN_VALUE;
        long edMin = Long.MAX_VALUE;
        long edMax = Long.MIN_VALUE;
        for (int i = 0; i < rowsOn.size(); i++)
        {
            offd.put("jdk" + i, rowsOff.get(i).jdkBytes());
            offd.put("ec" + i, rowsOff.get(i).ecBytes());
            ond.put("jdk" + i, rowsOn.get(i).jdkBytes());
            ond.put("ec" + i, rowsOn.get(i).ecBytes());
        }
        for (int i = 0; i < Math.min(rowsOff.size(), rowsOn.size()); i++)
        {
            long jd = rowsOff.get(i).jdkBytes() - rowsOn.get(i).jdkBytes();
            long ed = rowsOff.get(i).ecBytes() - rowsOn.get(i).ecBytes();
            jdMin = Math.min(jdMin, jd);
            jdMax = Math.max(jdMax, jd);
            edMin = Math.min(edMin, ed);
            edMax = Math.max(edMax, ed);
        }
        int lastIndex = rowsOn.size() - 1;
        List<String> sel = List.of("jdk0", "ec0", "jdk1", "ec1", "jdk" + lastIndex, "ec" + lastIndex);
        String c2 = cohBars(t, "coh", sel, offd, ond, "Lazy pipelines: compact object headers off vs on",
                "Lower is better. Every pipeline object loses 4 bytes of header, which crosses an 8-byte boundary for "
                        + "some objects: across all 13 rows the JDK pipeline shrinks by " + jdMin + " to " + jdMax + " B, the EC one by "
                        + edMin + " to " + edMax + " B.", "B");
        List<String> a = header(t, "Lazy pipelines: JDK Stream vs EC LazyIterable",
                "`StreamVsLazyIterableMemoryTest` builds a pipeline on an empty list without running it, for 12 single "
                        + "operations and one 3-step chain, and measures the pipeline minus the list.");
        long j0 = first.jdkBytes();
        long e0 = first.ecBytes();
        long jMin = Long.MAX_VALUE;
        long jMax = Long.MIN_VALUE;
        long eMin = Long.MAX_VALUE;
        long eMax = Long.MIN_VALUE;
        for (Row r : rowsOn.subList(1, rowsOn.size() - 1))
        {
            jMin = Math.min(jMin, r.jdkBytes());
            jMax = Math.max(jMax, r.jdkBytes());
            eMin = Math.min(eMin, r.ecBytes());
            eMax = Math.max(eMax, r.ecBytes());
        }
        a.addAll(List.of("== Summary", "",
                "* *Starting is 5x cheaper in EC:* `list.stream()` is " + j0 + " B (pipeline head plus spliterator; the same 80 B "
                        + "the shakers object counts measured per call), `ecList.asLazy()` is " + e0 + " B (one `LazyIterableAdapter`).",
                "* *Each JDK operation adds a pipeline stage of " + (jMin - j0) + " to "
                        + (jMax - j0) + " B; each EC operation adds " + (eMin - e0) + " to "
                        + (eMax - e0) + " B.* "
                        + "The 3-step chain is " + last.jdkBytes() + " B against " + last.ecBytes() + " B.",
                "* *These are pipeline sizes, not allocations per call.* The lambdas in the test are non-capturing constants, "
                        + "which JOL counts but a real call does not allocate; and escape analysis can remove EC objects that are not "
                        + "used after the call. For bytes per call, see the shakers JDK Stream ops analysis.", ""));
        a.addAll(embed(name, "Size of each unevaluated pipeline, compact headers on"));
        a.addAll(embed(c2, "Compact headers off vs on: start, one operation, and the 3-step chain"));
        a.addAll(List.of("== Every measurement", "", "[cols=\"3,1,1,3,1,1\",options=\"header\"]", "|===",
                "|JDK |Off (B) |On (B) |Eclipse Collections |Off (B) |On (B)"));
        for (int i = 0; i < Math.min(rowsOff.size(), rowsOn.size()); i++)
        {
            Row ro = rowsOff.get(i);
            Row rn = rowsOn.get(i);
            a.add("|`" + rn.jdk() + "` |" + ro.jdkBytes() + " |" + rn.jdkBytes() + " |`" + rn.ec() + "` |" + ro.ecBytes() + " |" + rn.ecBytes());
        }
        a.addAll(List.of("|===", ""));
        a.addAll(footer(t));
        write(t, a);
    }
}
