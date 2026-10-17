package tools.analysis;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JDK Stream / FindOps / MatchOps / ReduceOps allocation breakdown for one run, compared with the
 * Eclipse Collections (EC) versions of the same three operations.
 *
 * Usage (Java 25, from the project root): java src/main/java/tools/analysis/JdkOpsDocs.java . <RUN_TS>
 * Reads generated/runs/<RUN_TS>/shakers/raw/all-histo-{coh-on,coh-off,ea-off}-histo.txt and the summary.json that
 * ShakersDocs writes to generated/runs/<RUN_TS>/shakers/; writes <RUN_TS>-JdkStreamOpsAnalysis.adoc to its
 * reports/ folder, PNGs to reports/images/ and Mermaid sources to reports/mermaid/ (Layout.java, instructions.md L).
 */
public class JdkOpsDocs
{
    private static String runTs;
    private static Layout layout;
    private static String env;
    private static String java;
    private static final Map<String, Map<JmhParse.HistoKey, JmhParse.Histo>> H = new LinkedHashMap<>();
    private static Map<String, Object> summary;
    private static final int[] TESTS = {1, 500, 1000};

    // Benchmark (Utility = JDK List.copyOf list; EagerLazy has the same JDK benchmark) -> JDK method
    private static final String[][] OPS = {
            {"anyMatch", "AnyMatchUtility", "jdkAnyMatch", "list.stream().anyMatch(Integer.valueOf(test)::equals)"},
            {"findFirst", "FindFirstUtility", "jdkFindFirst", "list.stream().filter(Integer.valueOf(test)::equals).findFirst().orElse(0)"},
            {"count", "CountUtility", "jdkCount", "list.stream().filter(Integer.valueOf(test)::equals).count()"}};

    // Origin of every class the JDK benchmarks allocate, and why (verified against JDK 25 bytecode with javap).
    private static final String[][] ORIGIN = {
            {"java.util.stream.ReferencePipeline$Head", "Stream source", "`list.stream()`: the pipeline head (AbstractPipeline fields: links, flags, depth, spliterator)"},
            {"java.util.AbstractList$RandomAccessSpliterator", "Stream source", "`list.stream()`: the spliterator that walks the list"},
            {"java.util.stream.ReferencePipeline$2", "filter stage", "`filter(p)`: the StatelessOp pipeline stage"},
            {"java.util.stream.ReferencePipeline$2$1", "filter stage", "`filter(p)`: its Sink.ChainedReference, made when the terminal op runs"},
            {"java.util.stream.MatchOps$MatchOp", "terminal op", "`anyMatch`: `MatchOps.makeRef` does `new MatchOp(...)` on every call"},
            {"java.util.stream.MatchOps$$Lambda", "terminal op", "`anyMatch`: the `() -> new MatchSink()` supplier, capturing the predicate and MatchKind, made on every call"},
            {"java.util.stream.MatchOps$1MatchSink", "terminal op", "`anyMatch`: the sink that tests each element"},
            {"java.util.stream.FindOps$FindSink$OfRef", "terminal op", "`findFirst`: the sink that keeps the first value (the FindOp itself is a cached static, `OP_FIND_FIRST`)"},
            {"java.util.Optional", "terminal op", "`findFirst`: the `Optional` result, made by `FindSink.OfRef.get()`"},
            {"java.util.stream.ReduceOps$5", "terminal op", "`count`: `ReduceOps.makeRefCounting()` does `new ReduceOps$5(...)` on every call"},
            {"java.util.stream.ReduceOps$CountingSink$OfRef", "terminal op", "`count`: the sink that counts (the `long` result is a cached `Long`)"},
            {"$$Lambda", "caller lambda", "the benchmark's bound method reference `Integer.valueOf(test)::equals` (captures the Integer)"},
            {"java.lang.Integer", "Integer box", "`Integer.valueOf(test)`: outside the -128..127 cache, so only at test = 500 and 1000"}};
    // Objects only the EC methods allocate, and only when escape analysis is off (verified against EC 13.0.0 bytecode).
    private static final String[][] EC_ORIGIN = {
            {"org.eclipse.collections.impl.block.factory.Predicates$BindPredicate2", "EC object",
                    "EC 13.0.0 `ImmutableArrayList` inherits `anySatisfyWith`, `countWith` and `detectWithIfNone` from "
                            + "`AbstractRichIterable`, which wraps the call in `Predicates.bind(predicate, parameter)`: a new capturing wrapper per call"},
            {"org.eclipse.collections.impl.lazy.LazyIterableAdapter", "EC object",
                    "`asLazy()`: `LazyIterate.adapt(this)` makes a new lazy view of the list on every call"},
            {"org.eclipse.collections.impl.block.procedure.CountProcedure", "EC object",
                    "`asLazy().count`: `LazyIterableAdapter` inherits `count` from `AbstractRichIterable`, which does "
                            + "`new CountProcedure(predicate)` and runs it through `forEach`"},
            {"java.util.Optional", "EC object", "`detectOptional` / `detectWithOptional`: the `Optional` result"},
            {"java.lang.Integer", "Integer box", "`*With` methods: `this.test` is an `int` field, autoboxed to pass it as the "
                    + "parameter; capturing methods: `Integer.valueOf(test)`. Only at test = 500 and 1000 (outside the cache)"},
            {"$$Lambda", "caller lambda", "the benchmark's bound method reference `Integer.valueOf(test)::equals` "
                    + "(captures the Integer). The fat-free `Integer::equals` is a constant and never allocates"}};
    private static final List<String> CATS = List.of("Stream source", "filter stage", "terminal op", "caller lambda", "Integer box");
    private static final List<String> ALL_CATS = List.of("Stream source", "filter stage", "terminal op", "caller lambda", "Integer box", "EC object");
    private static final Map<String, String> COL = Map.of("Stream source", "#E69F00", "filter stage", "#56B4E9", "terminal op", "#0072B2",
            "caller lambda", "#CC79A7", "Integer box", "#000000", "EC object", "#D55E00");
    private static final Map<String, String> HATCH = Map.of("Integer box", "//", "EC object", "xx");
    private static final String NBSP14 = " ".repeat(14);

    // ---------- Eclipse Collections side ----------
    // Every implementation of each operation: (benchmark, method, label, code). The JDK stream runs the same in
    // *Utility and *EagerLazy, so it is listed once (from *Utility). Utility = EC's Iterate on the same JDK list;
    // EagerLazy = an EC ImmutableList (eager methods and asLazy()).
    private static final Map<String, String[][]> IMPLS = new LinkedHashMap<>();

    static
    {
        IMPLS.put("anyMatch", new String[][]{
                {"AnyMatchUtility", "jdkAnyMatch", "JDK list.stream().anyMatch", "list.stream().anyMatch(Integer.valueOf(test)::equals)"},
                {"AnyMatchUtility", "ecAnySatisfy", "EC Iterate.anySatisfy (JDK list)", "Iterate.anySatisfy(list, Integer.valueOf(test)::equals)"},
                {"AnyMatchUtility", "ecAnySatisfyWith", "EC Iterate.anySatisfyWith (JDK list)", "Iterate.anySatisfyWith(list, Integer::equals, test)"},
                {"AnyMatchEagerLazy", "ecAnySatisfy", "EC ImmutableList.anySatisfy", "ecList.anySatisfy(Integer.valueOf(test)::equals)"},
                {"AnyMatchEagerLazy", "ecAnySatisfyWith", "EC ImmutableList.anySatisfyWith", "ecList.anySatisfyWith(Integer::equals, test)"},
                {"AnyMatchEagerLazy", "ecLazyAnySatisfy", "EC ImmutableList.asLazy().anySatisfy", "ecList.asLazy().anySatisfy(Integer.valueOf(test)::equals)"}});
        IMPLS.put("findFirst", new String[][]{
                {"FindFirstUtility", "jdkFindFirst", "JDK list.stream().filter().findFirst", "list.stream().filter(Integer.valueOf(test)::equals).findFirst().orElse(0)"},
                {"FindFirstUtility", "ecDetectOptional", "EC Iterate.detectOptional (JDK list)", "Iterate.detectOptional(list, Integer.valueOf(test)::equals).orElse(0)"},
                {"FindFirstUtility", "ecDetectWithOptional", "EC Iterate.detectWithOptional (JDK list)", "Iterate.detectWithOptional(list, Integer::equals, test).orElse(0)"},
                {"FindFirstUtility", "ecDetectIfNone", "EC Iterate.detectIfNone (JDK list)", "Iterate.detectIfNone(list, Integer.valueOf(test)::equals, 0)"},
                {"FindFirstUtility", "ecDetectWithIfNone", "EC Iterate.detectWithIfNone (JDK list)", "Iterate.detectWithIfNone(list, Integer::equals, test, 0)"},
                {"FindFirstEagerLazy", "ecDetectOptional", "EC ImmutableList.detectOptional", "ecList.detectOptional(Integer.valueOf(test)::equals).orElse(0)"},
                {"FindFirstEagerLazy", "ecDetectWithOptional", "EC ImmutableList.detectWithOptional", "ecList.detectWithOptional(Integer::equals, test).orElse(0)"},
                {"FindFirstEagerLazy", "ecDetectIfNone", "EC ImmutableList.detectIfNone", "ecList.detectIfNone(Integer.valueOf(test)::equals, () -> 0)"},
                {"FindFirstEagerLazy", "ecDetectWithIfNone", "EC ImmutableList.detectWithIfNone", "ecList.detectWithIfNone(Integer::equals, test, () -> 0)"},
                {"FindFirstEagerLazy", "ecLazyDetectOptional", "EC ImmutableList.asLazy().detectOptional", "ecList.asLazy().detectOptional(Integer.valueOf(test)::equals).orElse(0)"},
                {"FindFirstEagerLazy", "ecLazyDetectIfNone", "EC ImmutableList.asLazy().detectIfNone", "ecList.asLazy().detectIfNone(Integer.valueOf(test)::equals, () -> 0)"}});
        IMPLS.put("count", new String[][]{
                {"CountUtility", "jdkCount", "JDK list.stream().filter().count", "list.stream().filter(Integer.valueOf(test)::equals).count()"},
                {"CountUtility", "ecCount", "EC Iterate.count (JDK list)", "Iterate.count(list, Integer.valueOf(test)::equals)"},
                {"CountUtility", "ecCountWith", "EC Iterate.countWith (JDK list)", "Iterate.countWith(list, Integer::equals, test)"},
                {"CountEagerLazy", "ecCount", "EC ImmutableList.count", "ecList.count(Integer.valueOf(test)::equals)"},
                {"CountEagerLazy", "ecCountWith", "EC ImmutableList.countWith", "ecList.countWith(Integer::equals, test)"},
                {"CountEagerLazy", "ecLazyCount", "EC ImmutableList.asLazy().count", "ecList.asLazy().count(Integer.valueOf(test)::equals)"}});
    }

    private static final Map<String, String> KIND = Map.of("jdk", "JDK stream", "cap", "capturing lambda",
            "lazy", "lazy, capturing lambda", "free", "fat-free *With", "bound", "*With, but Predicates.bind (not fat-free)");

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws IOException
    {
        String root = args[0];
        runTs = args[1];
        layout = Layout.shakers(root, runTs);
        String meta = Files.readString(layout.raw.resolve("meta.txt"), StandardCharsets.UTF_8);
        env = Layout.environment(meta);
        java = Layout.javaVersion(meta);
        for (String c : List.of("coh-on", "coh-off", "ea-off"))
        {
            H.put(c, JmhParse.parseHisto(layout.raw.resolve("all-histo-" + c + "-histo.txt")));
        }
        summary = (Map<String, Object>) Py.parseJson(Files.readString(layout.out.resolve("summary.json"), StandardCharsets.UTF_8));
        doc();
    }

    // ---------------------------------------------------------------- lookups

    // (category, why) for a class; method decides between the JDK and the EC table
    private static String[] origin(String cls, String method)
    {
        String[][] table = method.startsWith("ec") ? EC_ORIGIN : ORIGIN;
        for (String[] row : table)
        {
            if (cls.equals(row[0]) || (row[0].equals("$$Lambda") && cls.startsWith("conf.")))
            {
                return new String[]{row[1], row[2]};
            }
        }
        return new String[]{"other", ""};
    }

    private static String[] origin(String cls)
    {
        return origin(cls, "jdk");
    }

    private static String shortName(String cls)
    {
        return cls.replace("java.util.stream.", "").replace("java.util.", "").replace("java.lang.", "")
                .replace("conf.session.shakers.", "");
    }

    private static String lastPart(String s)
    {
        String[] parts = s.split("\\.", -1);
        return parts[parts.length - 1];
    }

    private static JmhParse.Histo block(String cfg, String bench, String method, int t)
    {
        return H.get(cfg).get(new JmhParse.HistoKey(bench + "Benchmark", method, t));
    }

    private static Map<String, Double> byCat(String cfg, String bench, String method, int t, List<String> cats)
    {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String c : cats)
        {
            out.put(c, 0.0);
        }
        for (Map.Entry<String, double[]> e : block(cfg, bench, method, t).classes.entrySet())
        {
            String cat = origin(e.getKey(), method)[0];
            if (!out.containsKey(cat))
            {
                // A class the ORIGIN table does not know: check it with javap and add it (instructions.md S6.5.1).
                System.err.println("WARNING: " + e.getKey() + " (" + bench + " " + method + " test=" + t + ") has no origin; add it to ORIGIN");
                continue;
            }
            out.put(cat, out.get(cat) + Math.rint(e.getValue()[1]));
        }
        return out;
    }

    private static Map<String, Double> byCat(String cfg, String bench, String method, int t)
    {
        return byCat(cfg, bench, method, t, CATS);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> norm(String cfg, String bench, String method, int t)
    {
        return (Map<String, Object>) ((Map<String, Object>) summary.get(bench)).get(cfg + "|" + method + "|" + t);
    }

    private static double d(Object number)
    {
        return ((Number) number).doubleValue();
    }

    private static String f0(double x)
    {
        return Py.f(x, 0);
    }

    private static String kind(String bench, String method)
    {
        return ShakersCharts.classify(bench, method)[0];
    }

    // (objects, bytes) per call from the escape-analysis-off object-count run
    private static double[] eaOff(String bench, String method, int t)
    {
        JmhParse.Histo bl = block("ea-off", bench, method, t);
        return new double[]{bl.objs, bl.bytes};
    }

    private static String mermaidHeader(int height)
    {
        return String.join("\n", "---", "config:", "  xyChart:", "    width: 1400", "    height: " + height,
                "    chartOrientation: horizontal", "  themeVariables:", "    xyChart:", "      plotColorPalette: \"#0072B2\"", "---");
    }

    private static String quoted(List<String> cats)
    {
        List<String> q = new ArrayList<>();
        for (String c : cats)
        {
            q.add("\"" + c + "\"");
        }
        return String.join(", ", q);
    }

    private static void writeMermaid(String name, List<String> lines) throws IOException
    {
        Files.writeString(layout.mermaid.resolve(name + ".mmd"), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    private static Plot.Entry entry(String cat)
    {
        return new Plot.Entry(cat, Plot.colour(COL.get(cat)), HATCH.getOrDefault(cat, ""));
    }

    // ---------- chart 1: bytes per call by origin (stacked), coh-on, test=500 ----------
    private static String chartOrigin(int t) throws IOException
    {
        String name = runTs + "-JdkStreamOps-bytes-by-origin";
        Plot plot = new Plot(12, 5.2, true);
        double[] left = new double[OPS.length];
        double[] ys = new double[OPS.length];
        for (int i = 0; i < OPS.length; i++)
        {
            ys[i] = OPS.length - 1 - i;
        }
        List<Plot.Entry> legend = new ArrayList<>();
        for (String cat : CATS)
        {
            for (int i = 0; i < OPS.length; i++)
            {
                double v = byCat("coh-on", OPS[i][1], OPS[i][2], t).get(cat);
                plot.bar(ys[i] - 0.3, ys[i] + 0.3, left[i], left[i] + v, Plot.colour(COL.get(cat)), HATCH.getOrDefault(cat, ""));
                if (v != 0)
                {
                    plot.text(ys[i], left[i] + v / 2, f0(v) + " B", 10, cat.equals("Stream source") ? Color.BLACK : Color.WHITE,
                            true, "center", "center", false, null, 0);
                }
                left[i] += v;
            }
            legend.add(entry(cat));
        }
        double maxLeft = 0;
        for (int i = 0; i < OPS.length; i++)
        {
            double n = block("coh-on", OPS[i][1], OPS[i][2], t).objs;
            plot.text(ys[i], left[i] + 3, f0(left[i]) + " B, " + f0(n) + " objects", 10.5, Color.BLACK, false,
                    "left", "center", false, null, 0);
            maxLeft = Math.max(maxLeft, left[i]);
        }
        String[] names = new String[OPS.length];
        for (int i = 0; i < OPS.length; i++)
        {
            String op = OPS[i][0];
            names[i] = "stream()." + (!op.equals("anyMatch") ? "filter(p)." : "") + op + (op.equals("anyMatch") ? "(p)" : "()");
        }
        plot.categories(ys, names, 11);
        plot.limits(0, maxLeft * 1.3);
        plot.numberAxisLabel("bytes allocated per call (match at element 500, compact headers on)");
        plot.title("What one JDK stream call leaves behind, by where each object comes from");
        plot.subtitle(Py.wrap("Lower is better. The Stream source and the terminal op (MatchOps, FindOps, ReduceOps) "
                + "are paid on every call, whatever the lambda; filter adds its own stage. "
                + "Every Eclipse Collections version allocates 0 B here.", 130));
        plot.legend(legend, 5, 10);
        plot.save(layout.images.resolve(name + ".png"));

        List<String> cats = new ArrayList<>();
        List<Double> vals = new ArrayList<>();
        for (String[] op : OPS)
        {
            for (Map.Entry<String, Double> e : byCat("coh-on", op[1], op[2], t).entrySet())
            {
                if (e.getValue() != 0)
                {
                    cats.add(NBSP14 + op[0] + ": " + e.getKey() + " = " + f0(e.getValue()) + " B");
                    vals.add(e.getValue());
                }
            }
        }
        List<String> vs = new ArrayList<>();
        for (double v : vals)
        {
            vs.add(f0(v));
        }
        writeMermaid(name, List.of(mermaidHeader(34 * cats.size() + 140),
                "%% Generated by JdkOpsDocs.java. Bars: <terminal op>: <origin> = bytes per call (test=500, coh-on).",
                "xychart-beta", "  title \"What one JDK stream call leaves behind, by where each object comes from\"",
                "  x-axis [" + quoted(cats) + "]",
                "  y-axis \"bytes allocated per call\" 0 --> " + f0(max(vals) * 1.15),
                "  bar [" + String.join(", ", vs) + "]"));
        return name;
    }

    private static double max(List<Double> values)
    {
        double m = -Double.MAX_VALUE;
        for (double v : values)
        {
            m = Math.max(m, v);
        }
        return m;
    }

    /** One row of the compact-headers chart: class key, origin, bytes with headers on and off. */
    private record CohRow(String key, String cat, long on, long off)
    {
    }

    // ---------- chart 2: per-class bytes, compact headers on vs off ----------
    private static List<CohRow> chartCoh(int t, String[] nameOut) throws IOException
    {
        String name = runTs + "-JdkStreamOps-class-sizes-coh";
        List<CohRow> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String[] op : OPS)
        {
            List<Map.Entry<String, double[]>> items = new ArrayList<>(block("coh-on", op[1], op[2], t).classes.entrySet());
            items.sort(Comparator.comparingDouble(e -> -e.getValue()[1]));
            for (Map.Entry<String, double[]> e : items)
            {
                String cls = e.getKey();
                String key = cls.startsWith("conf.") ? "caller $$Lambda" : shortName(cls);
                if (seen.contains(key))
                {
                    continue;
                }
                seen.add(key);
                double off = block("coh-off", op[1], op[2], t).classes.get(cls)[1];
                rows.add(new CohRow(key, origin(cls)[0], (long) Math.rint(e.getValue()[1]), (long) Math.rint(off)));
            }
        }
        rows.sort(Comparator.comparingInt(r -> CATS.indexOf(r.cat())));
        Plot plot = new Plot(12, 7, true);
        double h = 0.38;
        double[] ys = new double[rows.size()];
        String[] names = new String[rows.size()];
        double maxOff = 0;
        Color grey = Plot.colour("#999999");
        Color blue = Plot.colour("#0072B2");
        for (int i = 0; i < rows.size(); i++)
        {
            CohRow r = rows.get(i);
            double y = rows.size() - 1 - i;
            ys[i] = y;
            names[i] = r.key() + "  (" + r.cat() + ")";
            plot.bar(y, y + h, 0, r.off(), grey, "");
            plot.bar(y - h, y, 0, r.on(), blue, "");
            plot.text(y + h / 2, r.off() + 0.6, r.off() + " B", 9, Color.BLACK, false, "left", "center", false, null, 0);
            plot.text(y - h / 2, r.on() + 0.6, r.on() + " B", 9, Color.BLACK, false, "left", "center", false, null, 0);
            if (r.off() != r.on())
            {
                plot.text(y, Math.max(r.on(), r.off()) + 4.5, "saves " + (r.off() - r.on()) + " B", 9.5, blue, true,
                        "left", "center", false, null, 0);
            }
            maxOff = Math.max(maxOff, r.off());
        }
        plot.categories(ys, names, 10);
        plot.limits(0, maxOff * 1.35);
        plot.numberAxisLabel("bytes per object");
        plot.title("Size of each object a JDK stream call allocates, compact object headers off vs on");
        plot.subtitle(Py.wrap("Lower is better. Compact headers shrink the header from 12 to 8 bytes, but objects are "
                + "padded to 8 bytes, so only some classes get smaller. The object count does not change.", 130));
        plot.legend(List.of(new Plot.Entry("compact headers off", grey, ""),
                new Plot.Entry("compact headers on (JDK 25 -XX:+UseCompactObjectHeaders)", blue, "")), 2, 10);
        plot.save(layout.images.resolve(name + ".png"));

        List<String> cats = new ArrayList<>();
        List<String> vals = new ArrayList<>();
        long maxVal = 0;
        for (CohRow r : rows)
        {
            for (Object[] lv : new Object[][]{{"off", r.off()}, {"on", r.on()}})
            {
                long v = (Long) lv[1];
                cats.add(NBSP14 + r.key() + " headers " + lv[0] + " = " + v + " B");
                vals.add(Long.toString(v));
                maxVal = Math.max(maxVal, v);
            }
        }
        writeMermaid(name, List.of(mermaidHeader(30 * cats.size() + 140),
                "%% Generated by JdkOpsDocs.java. Bars: <class> headers <off|on> = bytes per object.",
                "xychart-beta", "  title \"Size of each object a JDK stream call allocates, compact headers off vs on\"",
                "  x-axis [" + quoted(cats) + "]",
                "  y-axis \"bytes per object\" 0 --> " + f0(maxVal * 1.15),
                "  bar [" + String.join(", ", vals) + "]"));
        nameOut[0] = name;
        return rows;
    }

    // Stacked bars: what each JDK and EC version of op allocates per call with escape analysis off
    private static String chartEc(String op, int t) throws IOException
    {
        String name = runTs + "-JdkStreamOps-vs-ec-" + op;
        String[][] rows = IMPLS.get(op);
        Plot plot = new Plot(12, 1.6 + 0.55 * rows.length, true);
        double[] ys = new double[rows.length];
        for (int i = 0; i < rows.length; i++)
        {
            ys[i] = rows.length - 1 - i;
        }
        double[] left = new double[rows.length];
        List<Plot.Entry> legend = new ArrayList<>();
        for (String cat : ALL_CATS)
        {
            double[] vals = new double[rows.length];
            boolean any = false;
            for (int i = 0; i < rows.length; i++)
            {
                vals[i] = byCat("ea-off", rows[i][0], rows[i][1], t, ALL_CATS).get(cat);
                any |= vals[i] != 0;
            }
            if (!any)
            {
                continue;
            }
            Color colour = Plot.colour(COL.get(cat));
            for (int i = 0; i < rows.length; i++)
            {
                double v = vals[i];
                plot.bar(ys[i] - 0.31, ys[i] + 0.31, left[i], left[i] + v, colour, HATCH.getOrDefault(cat, ""));
                if (v >= 16)
                {
                    // A solid box behind the number keeps it readable on hatched segments.
                    plot.text(ys[i], left[i] + v / 2, f0(v), 9, cat.equals("Stream source") ? Color.BLACK : Color.WHITE, true,
                            "center", "center", false, HATCH.containsKey(cat) ? colour : null, 0);
                }
                left[i] += v;
            }
            legend.add(entry(cat));
        }
        double maxLeft = 0;
        for (int i = 0; i < rows.length; i++)
        {
            double n = block("ea-off", rows[i][0], rows[i][1], t).objs;
            double on = d(norm("coh-on", rows[i][0], rows[i][1], t).get("bytes"));
            plot.text(ys[i], left[i] + 3, f0(left[i]) + " B, " + f0(n) + " obj   (normal JVM: " + f0(on) + " B)", 10,
                    Color.BLACK, false, "left", "center", false, null, 0);
            maxLeft = Math.max(maxLeft, left[i]);
        }
        String[] names = new String[rows.length];
        for (int i = 0; i < rows.length; i++)
        {
            names[i] = rows[i][2] + "  [" + KIND.get(kind(rows[i][0], rows[i][1])) + "]";
        }
        plot.categories(ys, names, 10);
        plot.limits(0, maxLeft * 1.55);
        plot.numberAxisLabel("bytes allocated per call, escape analysis off (-XX:-DoEscapeAnalysis), match at element " + t);
        double jdk = left[0];
        double ecMin = Double.MAX_VALUE;
        double ecMax = -Double.MAX_VALUE;
        for (int i = 1; i < left.length; i++)
        {
            ecMin = Math.min(ecMin, left[i]);
            ecMax = Math.max(ecMax, left[i]);
        }
        plot.title(op + ": what one call leaves behind, JDK stream vs Eclipse Collections");
        plot.subtitle(Py.wrap("Lower is better. Escape analysis off, so every object the code creates is counted. The JDK stream "
                + "leaves " + f0(jdk) + " B; EC versions leave " + f0(ecMin) + " to " + f0(ecMax) + " B, with no pipeline at all. "
                + "On a normal JVM (in brackets) the JIT removes every EC object; the JDK objects stay.", 130));
        plot.legend(legend, 6, 10);
        plot.save(layout.images.resolve(name + ".png"));

        List<String> cats = new ArrayList<>();
        List<Double> vals = new ArrayList<>();
        for (int i = 0; i < rows.length; i++)
        {
            cats.add(" ".repeat(14) + rows[i][2] + " = " + f0(left[i]) + " B (normal JVM "
                    + f0(d(norm("coh-on", rows[i][0], rows[i][1], t).get("bytes"))) + " B)");
            vals.add(left[i]);
        }
        List<String> vs = new ArrayList<>();
        for (double v : vals)
        {
            vs.add(f0(v));
        }
        writeMermaid(name, List.of(mermaidHeader(34 * cats.size() + 140),
                "%% Generated by JdkOpsDocs.java. Bars: <implementation> = bytes per call, escape analysis off, test=" + t + ".",
                "xychart-beta", "  title \"" + op + ": what one call leaves behind, JDK stream vs Eclipse Collections\"",
                "  x-axis [" + quoted(cats) + "]",
                "  y-axis \"bytes per call (-XX:-DoEscapeAnalysis)\" 0 --> " + f0(max(vals) * 1.15),
                "  bar [" + String.join(", ", vs) + "]"));
        return name;
    }

    // JDK bytes per call at element 500, escape analysis off, for the three operations.
    private static double[] jdkEaOff()
    {
        double[] out = new double[OPS.length];
        for (int i = 0; i < OPS.length; i++)
        {
            out[i] = block("ea-off", OPS[i][1], OPS[i][2], 500).bytes;
        }
        return out;
    }

    private static double min(double[] values)
    {
        return Arrays.stream(values).min().orElseThrow();
    }

    private static double max(double[] values)
    {
        return Arrays.stream(values).max().orElseThrow();
    }

    private static int catIndex(String cls, String method)
    {
        int i = ALL_CATS.indexOf(origin(cls, method)[0]);
        return i >= 0 ? i : 99;
    }

    // "`Class` (16 B), ..." for one implementation, escape analysis off
    private static String ecObjects(String b, String m, int t)
    {
        Map<String, double[]> cl = block("ea-off", b, m, t).classes;
        if (cl.isEmpty())
        {
            return "nothing";
        }
        List<Map.Entry<String, double[]>> items = new ArrayList<>(cl.entrySet());
        items.sort(Comparator.<Map.Entry<String, double[]>>comparingInt(e -> catIndex(e.getKey(), m))
                .thenComparing(Map.Entry::getKey));
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, double[]> e : items)
        {
            String c = e.getKey();
            parts.add((c.startsWith("conf.") ? "benchmark `$$Lambda`" : "`" + lastPart(shortName(c)) + "`")
                    + " (" + f0(e.getValue()[1]) + " B)");
        }
        return String.join(", ", parts);
    }

    private static List<String> embedTail(String caption, String png, String mmd)
    {
        // [1:] drops embed()'s own caption line: the block title above is the caption here.
        List<String> lines = Layout.embed(caption, png, mmd);
        return lines.subList(1, lines.size());
    }

    private static List<String> ecDoc() throws IOException
    {
        Map<String, String> charts = new LinkedHashMap<>();
        for (String op : IMPLS.keySet())
        {
            charts.put(op, chartEc(op, 500));
        }
        List<String> a = new ArrayList<>(List.of("== The same three operations in Eclipse Collections", "",
                "Every benchmark class also has Eclipse Collections (EC 13.0.0) versions of the same operation. In the "
                        + "`*Utility` classes EC's `Iterate` utility runs on the same JDK list as the stream; in the `*EagerLazy` "
                        + "classes the methods of an EC `ImmutableList` run, eagerly and through `asLazy()`. Two lambda shapes are "
                        + "compared: *capturing* (`Integer.valueOf(test)::equals`, a new object per call that holds the value) and "
                        + "*fat-free* (`Integer::equals` with the value passed as a parameter to a `*With` method, a constant lambda "
                        + "that is never allocated).", "",
                "=== Where each JDK object goes in EC", "",
                "An EC method is a plain loop over the list that calls the predicate, so most of what a stream builds has "
                        + "no EC counterpart at all (checked in the EC 13.0.0 bytecode with `javap`):", "",
                "[cols=\"2,3,4\",options=\"header\"]", "|===", "|JDK stream object (B per call) |EC equivalent |Why",
                "|Stream source: `ReferencePipeline$Head`, `RandomAccessSpliterator` (80 B) |none "
                        + "|`Iterate` checks the list type once and runs `for (i = 0; i < size; i++) list.get(i)` "
                        + "(`RandomAccessListIterate`); `ImmutableList` loops over its own array (`InternalArrayIterate`).",
                "|filter stage: `ReferencePipeline$2`, `$2$1` (72 B) |none |The predicate is called directly in the loop.",
                "|`anyMatch` terminal op: `MatchOp`, sink supplier, `MatchSink` (64 B) |none |The loop returns `true` at the first match.",
                "|`count` terminal op: `ReduceOps$5`, `CountingSink` (40 B) |none |The loop counts in a local `int`.",
                "|`findFirst` terminal op: `FindSink` (16 B) |none |The loop returns the element.",
                "|`findFirst` result: `Optional` (16 B) |`Optional` only for `detectOptional` / `detectWithOptional`; "
                        + "none for `detectIfNone` |`detectIfNone` returns the element or the default.",
                "|caller lambda `Integer.valueOf(test)::equals` (16 B) |same object for capturing EC calls; none for `*With` "
                        + "|`*With` takes a constant `Integer::equals` plus the value as a parameter.",
                "|`Integer` box (16 B at test 500, 1000) |same |Belongs to the benchmark's value, not to either library.",
                "|===", "",
                "EC adds a few objects of its own in some methods: `asLazy()` makes a `LazyIterableAdapter` per call (and "
                        + "`asLazy().count` a `CountProcedure`), `detectOptional` returns an `Optional`, and on an `ImmutableList` three "
                        + "of the `*With` methods are not fat-free in EC 13.0.0 because they go through `Predicates.bind` (see below).", "",
                "=== On a normal JVM", "",
                "Main runs, compact headers on, escape analysis on (the default). Times were measured on: " + env + "; "
                        + "bytes and GC counts do not depend on the machine.", ""));
        for (Map.Entry<String, String[][]> e : IMPLS.entrySet())
        {
            a.addAll(List.of("." + e.getKey(), "[cols=\"4,2,1,1,1,1\",options=\"header\"]", "|===",
                    "|Implementation |Lambda |ns per call (element 1) |ns per call (element 500) |B per call (element 500) "
                            + "|GC runs per 1 billion calls (element 500)"));
            for (String[] r : e.getValue())
            {
                Map<String, Object> n1 = norm("coh-on", r[0], r[1], 1);
                Map<String, Object> n5 = norm("coh-on", r[0], r[1], 500);
                a.add("|" + r[2] + " +\n`" + r[3] + "` |" + KIND.get(kind(r[0], r[1])) + " |" + Py.f(d(n1.get("ns")), 1)
                        + " |" + Py.f(d(n5.get("ns")), 1) + " |" + f0(d(n5.get("bytes"))) + " |" + Py.f(d(n5.get("gc_per_b")), 0, true));
            }
            a.addAll(List.of("|===", ""));
        }
        a.addAll(List.of("Every EC version leaves 0 B per call and needed no garbage collection, capturing or fat-free, eager or lazy, "
                        + "on either list. The JIT inlines the EC loop and the lambda into the caller and then removes every object "
                        + "they create (escape analysis). It cannot do that for the stream pipeline (see above), so the JDK side keeps "
                        + "its 160 to 224 B.", "",
                "=== With escape analysis off: what each call creates", "",
                "Escape analysis is a JIT optimisation, not a promise: it gives up when code is not inlined (cold code, "
                        + "large methods, deep call chains, megamorphic call sites). The object-count run with "
                        + "`-XX:-DoEscapeAnalysis` (`all-histo-ea-off-histo.txt`, every iteration GC-free) shows what the code itself "
                        + "creates, which is what an application pays whenever the JIT does not remove it. Match at element 500, "
                        + "compact headers on.", ""));
        for (Map.Entry<String, String[][]> e : IMPLS.entrySet())
        {
            String op = e.getKey();
            a.add("." + op + ": bytes per call by origin, escape analysis off (normal JVM in brackets)");
            a.addAll(embedTail(op + ": JDK stream vs Eclipse Collections, escape analysis off", charts.get(op) + ".png", charts.get(op) + ".mmd"));
            a.addAll(List.of("[cols=\"3,2,5,1,1\",options=\"header\"]", "|===",
                    "|Implementation |Lambda |Objects per call |Objects |B per call"));
            for (String[] r : e.getValue())
            {
                double[] ob = eaOff(r[0], r[1], 500);
                String objs = r[1].startsWith("ec") ? ecObjects(r[0], r[1], 500) : "the JDK pipeline above, plus the caller lambda and `Integer`";
                a.add("|" + r[2] + " |" + KIND.get(kind(r[0], r[1])) + " |" + objs + " |" + f0(ob[0]) + " |" + f0(ob[1]));
            }
            a.addAll(List.of("|===", ""));
        }
        a.addAll(List.of("=== Every object the EC versions allocate, and why", "",
                "[cols=\"3,1,3,5\",options=\"header\"]", "|===", "|Class |Origin |Allocated by |Why"));
        Map<String, List<String>> seen = new LinkedHashMap<>();
        for (String[][] rows : IMPLS.values())
        {
            for (String[] r : rows)
            {
                if (!r[1].startsWith("ec"))
                {
                    continue;
                }
                for (int t : TESTS)
                {
                    for (String c : block("ea-off", r[0], r[1], t).classes.keySet())
                    {
                        String key = c.startsWith("conf.") ? "conf.$$Lambda" : c;
                        List<String> labs = seen.computeIfAbsent(key, k -> new ArrayList<>());
                        if (!labs.contains(r[2]))
                        {
                            labs.add(r[2]);
                        }
                    }
                }
            }
        }
        List<String> keys = new ArrayList<>(seen.keySet());
        keys.sort(Comparator.<String>comparingInt(c -> catIndex(c, "ec")).thenComparing(c -> c));
        for (String c : keys)
        {
            String[] cw = origin(c, "ec");
            String name = c.startsWith("conf.") ? "benchmark `$$Lambda`" : "`" + lastPart(shortName(c)) + "`";
            String who;
            if (c.startsWith("conf."))
            {
                who = "every capturing call";
            }
            else if (c.equals("java.lang.Integer"))
            {
                who = "every call at test 500 and 1000";
            }
            else
            {
                List<String> labs = new ArrayList<>();
                for (String x : seen.get(c))
                {
                    labs.add(x.replace("EC ", ""));
                }
                who = String.join(", ", labs);
            }
            a.add("|" + name + " |" + cw[0] + " |" + who + " |" + cw[1]);
        }
        a.addAll(List.of("|===", "",
                "=== Fat-free vs capturing", "",
                "With escape analysis off the difference between the lambda shapes is plain:", ""));
        double[] jdkEa = jdkEaOff();
        a.addAll(List.of("* *Fat-free (`Iterate.anySatisfyWith`, `countWith`, `detectWithIfNone`):* only the `Integer` box, "
                        + f0(eo("AnyMatchUtility", "ecAnySatisfyWith")) + " B, and nothing at element 1 where the box is cached. "
                        + "The lambda is a constant.",
                "* *Capturing (`Iterate.anySatisfy`, `count`, `detectIfNone`):* the lambda plus the box, "
                        + f0(eo("AnyMatchUtility", "ecAnySatisfy")) + " B. A new lambda is made on every call because it holds the value.",
                "* *Lazy (`asLazy()`):* the capturing lambda plus a `LazyIterableAdapter`, "
                        + f0(eo("AnyMatchEagerLazy", "ecLazyAnySatisfy")) + " B; `count` also makes a `CountProcedure` "
                        + "(" + f0(eo("CountEagerLazy", "ecLazyCount")) + " B).",
                "* *`*With` on an EC 13.0.0 `ImmutableList`:* the `Predicates$BindPredicate2` wrapper plus the box, "
                        + f0(eo("AnyMatchEagerLazy", "ecAnySatisfyWith")) + " B, as much as the capturing version. `ImmutableArrayList` does not "
                        + "override `anySatisfyWith`, `countWith` or `detectWithIfNone`; it inherits them from `AbstractRichIterable`, "
                        + "which binds the parameter into a new predicate. `detectWithOptional` is overridden and stays fat-free. "
                        + "`Iterate` on a JDK list has its own `*With` loops, so all of them are fat-free there.",
                "* *JDK stream:* " + f0(min(jdkEa)) + " to " + f0(max(jdkEa)) + " B, "
                        + "with escape analysis on or off. Its pipeline objects are the bulk of it, and the stream has no `*With` "
                        + "form, so even a non-capturing predicate would still leave the pipeline behind.", "",
                "So the fat-free claim holds: on the paths that are fat-free, the only allocation left is the boxed value "
                        + "the caller passes in, and with escape analysis on there is nothing at all. The JDK stream allocates its "
                        + "pipeline whatever lambda it is given.", ""));
        return a;
    }

    private static double eo(String b, String m)
    {
        return eaOff(b, m, 500)[1];
    }

    @SuppressWarnings("unchecked")
    private static void doc() throws IOException
    {
        String c1 = chartOrigin(500);
        String[] c2Name = new String[1];
        List<CohRow> rows = chartCoh(500, c2Name);
        String c2 = c2Name[0];
        double[] jdkEa = jdkEaOff();
        String runRel = layout.rawRel;
        List<String> a = new ArrayList<>(List.of(
                "= JDK Stream, FindOps and MatchOps vs Eclipse Collections: where the garbage comes from (run " + runTs + ")", ":toc:", ":toclevels: 2", "",
                "Fresh look at the raw results of run `" + runTs + "` (`" + runRel + "/`): which objects one JDK `stream()` "
                        + "call allocates, which part of the Stream API makes each one, and what that costs; then the same numbers for "
                        + "the Eclipse Collections (EC) versions of each operation, with escape analysis on and off. "
                        + "Object counts come from the class-histogram runs (`all-histo-coh-on-histo.txt`, `all-histo-coh-off-histo.txt`, "
                        + "`all-histo-ea-off-histo.txt`, every iteration GC-free); bytes per call from `gc.alloc.rate.norm` in the main runs. "
                        + "Where each class comes from was checked with `javap` in the bytecode of the JDK that ran (" + java + ") and of EC 13.0.0. Environment: " + env + ". "
                        + "Bytes and object counts do not depend on the machine, timings do.", "",
                "== Summary", "",
                "* *Every JDK stream call allocates a fixed set of 6 to 8 objects (160 to 224 B), wherever "
                        + "the match is.* None of it is per element: it is the pipeline being built and thrown away on each call.",
                "* *More than a third of it is the Stream source.* `list.stream()` alone costs 80 B (pipeline head 48 B + spliterator 32 B) "
                        + "before any operation is chosen.",
                "* *The terminal op is the part that differs most.* `anyMatch` (MatchOps) allocates 3 objects, 64 B, because "
                        + "`MatchOps.makeRef` builds a new `MatchOp` and a new sink-supplier lambda on every call. `findFirst` (FindOps) "
                        + "allocates 2 objects, 32 B: its `FindOp` is a cached static, but the sink and the `Optional` result are new. "
                        + "`count` (ReduceOps) allocates 2 objects, 40 B: a new `ReduceOps$5` op on every call plus the counting sink.",
                "* *`filter` adds a whole pipeline stage* (2 objects, 72 B). That is why `stream().anyMatch(p)` (160 B) is "
                        + "cheaper than `stream().filter(p).findFirst()` (200 B) for the same question: anyMatch puts the predicate "
                        + "in the terminal op and needs no filter stage.",
                "* *Escape analysis removes none of it.* Bytes per call are identical with `-XX:-DoEscapeAnalysis` for every "
                        + "JDK benchmark. The same JIT removes every object in the Eclipse Collections loops, including the `Optional` "
                        + "that `detectOptional` returns.",
                "* *Compact object headers shave 16 to 24 B (7 to 11%)* off a call by shrinking 2 or 3 of its objects by 8 B "
                        + "each. The number of objects stays the same.",
                "* *Speed:* at match position 1, where almost no elements are scanned, a JDK call takes 29 to 38 ns against "
                        + "2 to 3 ns for Eclipse Collections. Building and dropping these objects is most of that difference (inferred: "
                        + "allocation plus the virtual calls through the sink chain).",
                "* *Eclipse Collections has no pipeline to build.* Its methods are a loop over the list that calls the "
                        + "predicate, so the Stream source, filter stage and terminal op have no EC counterpart. On a normal JVM every "
                        + "EC version, capturing or fat-free, eager or lazy, allocates 0 B and needed 0 garbage collections.",
                "* *With escape analysis off, the lambda shape shows.* A fat-free `Iterate.*With` call leaves only the "
                        + "`Integer` box the caller passes in (" + f0(eaOff("AnyMatchUtility", "ecAnySatisfyWith", 500)[1]) + " B, 0 B at "
                        + "element 1); a capturing call adds its lambda (" + f0(eaOff("AnyMatchUtility", "ecAnySatisfy", 500)[1]) + " B); "
                        + "`asLazy()` adds a `LazyIterableAdapter`; the JDK stream leaves "
                        + f0(min(jdkEa)) + " to " + f0(max(jdkEa)) + " B. "
                        + "On an EC 13.0.0 `ImmutableList`, `anySatisfyWith`, `countWith` and `detectWithIfNone` are not fat-free "
                        + "(`Predicates.bind` makes a wrapper per call).", "",
                "== The three pipelines", "",
                "[cols=\"1,4\",options=\"header\"]", "|===", "|Operation |Benchmark code"));
        for (String[] op : OPS)
        {
            a.add("|`" + op[0] + "` |`" + op[3] + "`");
        }
        a.addAll(List.of("|===", "",
                "The same JDK code runs in the `*Utility` and `*EagerLazy` benchmarks (both use a `List.copyOf` list), and the "
                        + "histograms are identical for both. The tables use the `*Utility` runs.", "",
                "== Every object, and which part of the Stream API makes it", "",
                "Compact headers on, match at element 500 (one call):", "",
                "[cols=\"3,2,1,1,1,5\",options=\"header\"]", "|===",
                "|Class |Origin |anyMatch (B) |findFirst (B) |count (B) |Why it is allocated"));
        List<String> classes = new ArrayList<>();
        for (String[] op : OPS)
        {
            for (String cls : block("coh-on", op[1], op[2], 500).classes.keySet())
            {
                if (!classes.contains(cls))
                {
                    classes.add(cls);
                }
            }
        }
        classes.sort(Comparator.<String>comparingInt(c -> CATS.indexOf(origin(c)[0])).thenComparing(c -> c));
        Set<String> done = new LinkedHashSet<>();
        for (String cls : classes)
        {
            String[] cw = origin(cls);
            boolean lam = cls.startsWith("conf.");
            if (lam && done.contains("lambda"))
            {
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (String[] op : OPS)
            {
                Map<String, double[]> cl = block("coh-on", op[1], op[2], 500).classes;
                double[] v = null;
                if (lam)
                {
                    for (Map.Entry<String, double[]> e : cl.entrySet())
                    {
                        if (e.getKey().startsWith("conf."))
                        {
                            v = e.getValue();
                            break;
                        }
                    }
                }
                else
                {
                    v = cl.get(cls);
                }
                cells.add(v != null ? f0(v[1]) : "-");
            }
            if (lam)
            {
                done.add("lambda");
            }
            String name = lam ? "benchmark `$$Lambda`" : "`" + shortName(cls) + "`";
            a.add("|" + name + " |" + cw[0] + " |" + String.join(" |", cells) + " |" + cw[1]);
        }
        List<String> tot = new ArrayList<>();
        for (String[] op : OPS)
        {
            JmhParse.Histo bl = block("coh-on", op[1], op[2], 500);
            tot.add("*" + f0(bl.bytes) + "* (" + f0(bl.objs) + " obj)");
        }
        a.addAll(List.of("2+h|Total per call |" + String.join(" |", tot) + " |", "|===", "",
                ".Bytes per call by origin. Every Eclipse Collections version allocates 0 B."));
        a.addAll(embedTail("Bytes per JDK stream call by origin", c1 + ".png", c1 + ".mmd"));
        a.addAll(List.of("=== By origin, every match position (compact headers on)", "",
                "[cols=\"2,1,1,1,1,1,1,1\",options=\"header\"]", "|===",
                "|Operation |test |Stream source |filter stage |terminal op |caller lambda |Integer box |Total (B / objects)"));
        for (String[] op : OPS)
        {
            for (int t : TESTS)
            {
                Map<String, Double> dc = byCat("coh-on", op[1], op[2], t);
                JmhParse.Histo bl = block("coh-on", op[1], op[2], t);
                List<String> cells = new ArrayList<>();
                for (String c : CATS)
                {
                    cells.add(f0(dc.get(c)));
                }
                a.add("|`" + op[0] + "` |" + t + " |" + String.join(" |", cells) + " |" + f0(bl.bytes) + " / " + f0(bl.objs));
            }
        }
        a.addAll(List.of("|===", "",
                "Only the `Integer` box changes with the match position (`Integer.valueOf(test)` is cached for 1, not for "
                        + "500 or 1000), and that box belongs to the benchmark's lambda, not to the Stream API. The Stream API's own "
                        + "cost is the same for a match at element 1 as at element 1000.", "",
                "== The terminal ops compared", "",
                "[cols=\"1,2,1,1,3\",options=\"header\"]", "|===",
                "|Operation |Terminal op class |New objects per call |Bytes |What could be avoided"));
        Map<String, String> notes = Map.of(
                "anyMatch", "The op and the supplier lambda depend on the predicate, so they cannot be cached as written.",
                "findFirst", "The op is already cached (`FindSink.OfRef.OP_FIND_FIRST`). The `Optional` is the result.",
                "count", "`ReduceOps$5` holds no per-call state, yet a new one is made each call (unlike FindOps). "
                        + "Caching it would save 16 B a call (inferred from the bytecode; not tested).");
        Map<String, String> clsOf = Map.of("anyMatch", "MatchOps", "findFirst", "FindOps", "count", "ReduceOps");
        for (String[] op : OPS)
        {
            List<String> items = new ArrayList<>();
            long sum = 0;
            for (Map.Entry<String, double[]> e : block("coh-on", op[1], op[2], 500).classes.entrySet())
            {
                if (origin(e.getKey())[0].equals("terminal op"))
                {
                    items.add("`" + shortName(e.getKey()) + "`");
                    sum += (long) Math.rint(e.getValue()[1]);
                }
            }
            a.add("|`" + op[0] + "` |" + clsOf.get(op[0]) + " |" + String.join(", ", items) + " |" + sum + " |" + notes.get(op[0]));
        }
        a.addAll(List.of("|===", "",
                "== Escape analysis does not help the JDK stream", "",
                "`gc.alloc.rate.norm` from the main runs, escape analysis on (normal) and off (`-XX:-DoEscapeAnalysis`), "
                        + "compact headers on:", "",
                "[cols=\"2,1,1,1\",options=\"header\"]", "|===", "|Operation |test |EA on (B per call) |EA off (B per call)"));
        for (String[] op : OPS)
        {
            for (int t : TESTS)
            {
                a.add("|`" + op[0] + "` |" + t + " |" + f0(d(norm("coh-on", op[1], op[2], t).get("bytes"))) + " |"
                        + f0(d(norm("ea-off", op[1], op[2], t).get("bytes"))));
            }
        }
        a.addAll(List.of("|===", "",
                "The numbers are the same, so the JIT scalar-replaces none of the pipeline objects. They are linked to each "
                        + "other (head to spliterator, stage to stage, sink to sink) and passed through interface calls in "
                        + "`AbstractPipeline.evaluate`, so they escape (inferred: the usual reasons escape analysis gives up, not "
                        + "confirmed with JIT logs). Compare Eclipse Collections, where the same JIT removes every object, including the "
                        + "`Optional` from `detectOptional`: there the loop and the lambda are inlined into the caller.", "",
                "== Compact object headers", ""));
        a.addAll(embedTail("Object sizes, compact headers off vs on", c2 + ".png", c2 + ".mmd"));
        a.addAll(List.of("[cols=\"3,2,1,1,1\",options=\"header\"]", "|===", "|Class |Origin |Headers off (B) |Headers on (B) |Saved"));
        for (CohRow r : rows)
        {
            a.add("|`" + r.key() + "` |" + r.cat() + " |" + r.off() + " |" + r.on() + " |"
                    + (r.off() != r.on() ? Long.toString(r.off() - r.on()) : "-"));
        }
        a.addAll(List.of("|===", "",
                "[cols=\"2,1,1,1,1\",options=\"header\"]", "|===", "|Operation |test |Headers off (B per call) |Headers on (B per call) |Saved"));
        for (String[] op : OPS)
        {
            for (int t : TESTS)
            {
                JmhParse.Histo on = block("coh-on", op[1], op[2], t);
                JmhParse.Histo off = block("coh-off", op[1], op[2], t);
                a.add("|`" + op[0] + "` |" + t + " |" + f0(off.bytes) + " |" + f0(on.bytes) + " |" + f0(off.bytes - on.bytes) + " B "
                        + "(" + f0((1 - on.bytes / off.bytes) * 100) + "%)");
            }
        }
        a.addAll(List.of("|===", "",
                "Object-count run (exact per-class sizes). Compact headers turn a 12-byte header into 8 bytes, and objects "
                        + "are padded to a multiple of 8 bytes, so a class only gets smaller when the 4 bytes saved cross that "
                        + "boundary: the pipeline head, the MatchOps lambda, the filter sink and the find sink do; the spliterator, "
                        + "the ops, `Optional` and `Integer` do not. Not allocating at all saves the whole 160 to 224 B.", "",
                "== What it costs an application", "",
                "Derived from bytes per call (compact headers on): allocation rate = bytes per call × calls per second.", "",
                "[cols=\"2,1,1,1,1\",options=\"header\"]", "|===",
                "|Operation |Bytes per call (test=500) |MB/s at 1 M calls/s |MB/s at 10 M calls/s |GC runs per 1 billion calls (measured, G1 2 GB heap)"));
        for (String[] op : OPS)
        {
            double by = block("coh-on", op[1], op[2], 500).bytes;
            a.add("|`" + op[0] + "` |" + f0(by) + " |" + f0(by) + " |" + Py.f(by * 10, 0, true) + " |"
                    + f0(d(norm("coh-on", op[1], op[2], 500).get("gc_per_b"))));
        }
        a.addAll(List.of("|===", "",
                "The Eclipse Collections versions of the same three operations allocate 0 B per call and caused 0 GC runs.", "",
                "=== Time spent on the pipeline itself", "",
                "At match position 1 the loop does almost no work, so the time is mostly setting up and tearing down the call.", "",
                "[cols=\"2,1,1,1\",options=\"header\"]", "|===", "|Operation |JDK stream (ns per call) |Fastest EC version (ns per call) |Difference (ns)"));
        for (String[] op : OPS)
        {
            Map<String, Object> sb = (Map<String, Object>) summary.get(op[1]);
            double best = Double.MAX_VALUE;
            for (String k : sb.keySet())
            {
                if (k.startsWith("coh-on|ec") && k.endsWith("|1"))
                {
                    String e = k.split("\\|", -1)[1];
                    best = Math.min(best, d(((Map<String, Object>) sb.get("coh-on|" + e + "|1")).get("ns")));
                }
            }
            double j = d(norm("coh-on", op[1], op[2], 1).get("ns"));
            a.add("|`" + op[0] + "` |" + Py.f(j, 1) + " |" + Py.f(best, 1) + " |" + Py.f(j - best, 1));
        }
        a.addAll(List.of("|===", "",
                "`count` always scans all 1000 elements, so at position 1 it is not a setup measurement; the difference "
                        + "there includes the slower per-element path through the filter and counting sinks.", ""));
        a.addAll(ecDoc());
        String override = System.getenv("SHAKERS_RUN_DIR");
        a.addAll(List.of("== Caveats", "",
                "* One machine (" + env + "), one JDK build (" + java + "), microbenchmarks.",
                "* Origins of each class were read from the JDK 25 bytecode (`javap -c`); explanations of why escape "
                        + "analysis fails, and the saving from caching `ReduceOps$5`, are inferred.",
                "* `gc.alloc.rate.norm` can sit a few bytes below the object-count totals in some forks, where the JIT "
                        + "removed one object some of the time; the object-count run is the reference.",
                "* EC findings are for EC 13.0.0: the `Predicates.bind` path on `ImmutableList` may change in other versions.",
                "* Escape analysis off is a bound, not a typical JVM: it shows what the code creates, while a normal JVM "
                        + "removes what it can inline. Which real call sites get inlined depends on the application.", "",
                "== Reproduce", "", "....",
                (override != null && !override.isEmpty() ? "SHAKERS_RUN_DIR=" + runRel + " " : "")
                        + "java src/main/java/tools/analysis/JdkOpsDocs.java . " + runTs, "....", "",
                "Inputs: `" + runRel + "/all-histo-coh-on-histo.txt`, `all-histo-coh-off-histo.txt`, `all-histo-ea-off-histo.txt`, "
                        + "`" + layout.outRel + "/summary.json`. `JdkOpsDocs.java`, `ShakersCharts.java`, `JmhParse.java` and `Layout.java` are in `src/main/java/tools/analysis/`.", ""));
        Path path = layout.reports.resolve(runTs + "-JdkStreamOpsAnalysis.adoc");
        Files.writeString(path, String.join("\n", a), StandardCharsets.UTF_8);
        System.out.println(layout.reports + "/" + runTs + "-JdkStreamOpsAnalysis.adoc");
    }
}
