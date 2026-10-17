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

/**
 * Self-explanatory charts for one benchmark class.
 *
 * Each chart is drawn twice from the same numbers:
 *   <name>.png  grouped bars: one group per match position, one coloured bar per implementation,
 *               a legend that says what each colour is, and the value printed on every bar.
 *               This is the image the analysis docs embed.
 *   <name>.mmd  Mermaid xychart-beta source with the same title; every bar's label names the
 *               implementation, the match position and the value (Mermaid has no legend).
 */
final class ShakersCharts
{
    private static final Map<String, String> OP = Map.of("AnyMatch", "anyMatch", "Count", "count", "FindFirst", "findFirst");
    private static final Map<String, String> SOURCE = Map.of(
            "Utility", "on a JDK ArrayList (EC via Iterate)",
            "EagerLazy", "on an EC ImmutableList (eager and lazy)");
    // Classes that are not one operation on a list: the chart title prefix, used as is
    private static final Map<String, String> TITLE = Map.of(
            "CollectorsAndCollectors2", "Creating 7 collectors, JDK Collectors vs EC Collectors2");
    // Colour-blind-safe Okabe-Ito colours, one per kind, the same in every chart. No red/green pairs.
    // A second method of the same kind keeps the colour and gets a hatch pattern, so colour is never
    // the only cue (legend, hatch and the value on every bar also identify each bar).
    private static final Map<String, String> COLOURS = Map.of(
            "jdk", "#E69F00",    // orange: JDK stream
            "cap", "#56B4E9",    // sky blue: EC eager, capturing
            "lazy", "#CC79A7",   // reddish purple: EC lazy, capturing
            "free", "#0072B2",   // dark blue: EC *With, fat-free
            "bound", "#000000",  // black: EC *With that Predicates.bind makes capturing
            "ec", "#009E73");    // bluish green: EC, no lambda involved (Collectors2)
    private static final String[] HATCHES = {"", "//", "..", "xx"};

    /** One chart as embedded in a doc: key, caption, PNG file name, Mermaid file name. */
    record Chart(String key, String caption, String png, String mmd)
    {
    }

    /** Colour, hatch and legend text of one benchmark method. */
    record Style(String colour, String hatch, String legend)
    {
    }

    private ShakersCharts()
    {
    }

    private static String testLabel(Integer t)
    {
        return t == null ? "one call (no parameter)" : "match at element " + t;
    }

    private static String at(Integer t)
    {
        return t == null ? "Per call" : "At element " + t;
    }

    // {operation, source} for the title, e.g. {"anyMatch", "on a JDK ArrayList (EC via Iterate)"}
    static String[] opOf(String cls)
    {
        if (TITLE.containsKey(cls))
        {
            return new String[]{TITLE.get(cls), ""};
        }
        for (Map.Entry<String, String> op : OP.entrySet())
        {
            if (cls.startsWith(op.getKey()) && SOURCE.containsKey(cls.substring(op.getKey().length())))
            {
                return new String[]{op.getValue(), SOURCE.get(cls.substring(op.getKey().length()))};
            }
        }
        return null;
    }

    /** {family, plain description} for a benchmark method. */
    static String[] classify(String cls, String m)
    {
        if (cls.equals("CollectorsAndCollectors2"))
        {
            if (m.equals("collectors"))
            {
                return new String[]{"jdk", "JDK Collectors (java.util.stream.Collectors)"};
            }
            return new String[]{"ec", "EC Collectors2 (org.eclipse.collections.impl.collector.Collectors2)"};
        }
        if (m.startsWith("jdk"))
        {
            return new String[]{"jdk", "JDK stream (builds a pipeline each call)"};
        }
        boolean lazy = m.startsWith("ecLazy");
        if (m.contains("With"))
        {
            if (cls.endsWith("EagerLazy") && !m.equals("ecDetectWithOptional"))
            {
                return new String[]{"bound", "EC *With, but ImmutableList wraps it in Predicates.bind (not fat-free)"};
            }
            return new String[]{"free", "EC *With: fat-free (non-capturing) lambda"};
        }
        if (lazy)
        {
            return new String[]{"lazy", "EC lazy (asLazy), capturing lambda"};
        }
        return new String[]{"cap", "EC eager, capturing lambda"};
    }

    static Map<String, Style> styles(String cls, List<String> ms)
    {
        Map<String, Integer> used = new LinkedHashMap<>();
        Map<String, Style> out = new LinkedHashMap<>();
        for (String m : ms)
        {
            String[] kind = classify(cls, m);
            int i = used.getOrDefault(kind[0], 0);
            used.put(kind[0], i + 1);
            out.put(m, new Style(COLOURS.get(kind[0]), HATCHES[i % HATCHES.length], m + ": " + kind[1]));
        }
        return out;
    }

    /** Grouped vertical bars. groups: x groups (match positions); values.get(m)[g]; labels.get(m)[g] text on the bar. */
    static void png(Path path, String title, String subtitle, String ylabel, List<String> groups, List<String> ms,
            Map<String, Style> style, Map<String, double[]> values, Map<String, String[]> labels, String footnote)
            throws IOException
    {
        int n = ms.size();
        Plot plot = new Plot(12, 6.2, false);
        double width = 0.8 / n;
        double vmax = 1e-9;
        for (String m : ms)
        {
            for (double v : values.get(m))
            {
                vmax = Math.max(vmax, v);
            }
        }
        List<Plot.Entry> legend = new ArrayList<>();
        for (int i = 0; i < n; i++)
        {
            String m = ms.get(i);
            Style s = style.get(m);
            Color colour = Plot.colour(s.colour());
            for (int g = 0; g < groups.size(); g++)
            {
                double x = g + (i - (n - 1) / 2.0) * width;
                double v = values.get(m)[g];
                plot.bar(x - width * 0.46, x + width * 0.46, 0, v, colour, s.hatch());
                plot.text(x, v, labels.get(m)[g], n > 4 ? 8.5 : 9.5, Color.BLACK, false, "center", "bottom", n > 4, null, 3);
            }
            legend.add(new Plot.Entry(s.legend(), colour, s.hatch()));
        }
        double[] positions = new double[groups.size()];
        for (int g = 0; g < positions.length; g++)
        {
            positions[g] = g;
        }
        plot.categories(positions, groups.toArray(new String[0]), 11);
        plot.numberAxisLabel(ylabel);
        plot.limits(0, vmax * (n > 4 ? 1.45 : 1.25));
        plot.title(title);
        plot.subtitle(Py.wrap(subtitle, 130));
        plot.legend(legend, 1, 9.5);
        if (footnote != null)
        {
            plot.footnote(Py.wrap(footnote, 150));
        }
        plot.save(path);
    }

    static void mmd(Path path, String title, String ylabel, List<String> groupsShort, List<String> ms,
            Map<String, double[]> values, Map<String, String[]> labels, String mmdc, String pp) throws IOException
    {
        List<String> cats = new ArrayList<>();
        List<Double> vals = new ArrayList<>();
        for (int g = 0; g < groupsShort.size(); g++)
        {
            for (String m : ms)
            {
                // NBSP pad: Mermaid under-measures label width
                cats.add(" ".repeat(14) + m + " " + groupsShort.get(g) + " = " + labels.get(m)[g].replace("\n", " "));
                vals.add(values.get(m)[g]);
            }
        }
        double vmax = 1e-9;
        for (double v : vals)
        {
            vmax = Math.max(vmax, v);
        }
        List<String> quoted = new ArrayList<>();
        for (String c : cats)
        {
            quoted.add("\"" + c + "\"");
        }
        List<String> bars = new ArrayList<>();
        for (double v : vals)
        {
            bars.add(Py.g(v, 4));
        }
        List<String> lines = List.of("---", "config:", "  xyChart:", "    width: 1400",
                "    height: " + Math.max(500, 34 * cats.size() + 120),
                "    chartOrientation: horizontal", "    xAxis:", "      labelFontSize: 13", "  themeVariables:", "    xyChart:",
                "      plotColorPalette: \"#0072B2\"", "---",
                "%% Generated by ShakersDocs.java. Each bar label reads: match position, benchmark method = value.",
                "%% The PNG next to this file is the same data as grouped, coloured bars with a legend.",
                "xychart-beta", "  title \"" + title + "\"",
                "  x-axis [" + String.join(", ", quoted) + "]",
                "  y-axis \"" + ylabel + "\" 0 --> " + Py.g(vmax * 1.1, 4),
                "  bar [" + String.join(", ", bars) + "]");
        Files.writeString(path, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        if (mmdc != null && !mmdc.isEmpty())
        {
            // parse check only; the embedded PNG is drawn by Plot
            List<String> command = new ArrayList<>(List.of(mmdc));
            if (pp != null && !pp.isEmpty())
            {
                command.addAll(List.of("-p", pp));
            }
            command.addAll(List.of("-i", path.toString(), "-o", System.getProperty("java.io.tmpdir") + "/_mmd_check.svg", "-q"));
            run(command);
        }
    }

    static void run(List<String> command) throws IOException
    {
        try
        {
            int exit = new ProcessBuilder(command).inheritIO().start().waitFor();
            if (exit != 0)
            {
                throw new IOException("failed (" + exit + "): " + String.join(" ", command));
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static String rng(double[] xs)
    {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double x : xs)
        {
            min = Math.min(min, x);
            max = Math.max(max, x);
        }
        String lo = b(min);
        String hi = b(max);
        return lo.equals(hi) ? lo : lo + " to " + hi;
    }

    static String b(double x)
    {
        return x < 0.5 ? "0 B" : Py.f(x, 0, true) + " B";
    }

    private static double max(double[] xs)
    {
        double out = -Double.MAX_VALUE;
        for (double x : xs)
        {
            out = Math.max(out, x);
        }
        return out;
    }

    private static double maxOf(List<String> ms, Map<String, double[]> v)
    {
        double out = -Double.MAX_VALUE;
        for (String m : ms)
        {
            out = Math.max(out, max(v.get(m)));
        }
        return out;
    }

    /**
     * Writes all charts for one class; returns them in doc order.
     * PNGs go to chartsDir; Mermaid sources go to mmdDir.
     */
    static List<Chart> build(String cls, List<String> ms, Map<String, Map<JmhParse.Key, ShakersDocs.Derived>> d,
            List<Integer> tests, Path chartsDir, String runTs, String mmdc, String pp, Path mmdDir) throws IOException
    {
        String[] op = opOf(cls);
        String name = (op[0] + " " + op[1]).strip();
        String jn = TITLE.containsKey(cls) ? "JDK Collectors version" : "JDK stream";  // what the JDK bar is called in titles
        Map<String, Style> style = styles(cls, ms);
        String jdk = null;
        List<String> ec = new ArrayList<>();
        for (String m : ms)
        {
            if (jdk == null && classify(cls, m)[0].equals("jdk"))
            {
                jdk = m;
            }
        }
        for (String m : ms)
        {
            if (!m.equals(jdk))
            {
                ec.add(m);
            }
        }
        List<String> groups = new ArrayList<>();
        List<String> shortNames = new ArrayList<>();
        for (Integer t : tests)
        {
            groups.add(testLabel(t));
            shortNames.add(t != null ? "@" + t : "");
        }
        Map<JmhParse.Key, ShakersDocs.Derived> on = d.get("coh-on");
        Map<JmhParse.Key, ShakersDocs.Derived> off = d.get("coh-off");
        Map<JmhParse.Key, ShakersDocs.Derived> ea = d.get("ea-off");
        List<Chart> out = new ArrayList<>();
        String jdkMethod = jdk;

        Emitter emit = (key, title, subtitle, ylabel, values, labels, caption, footnote) ->
        {
            String file = runTs + "-" + cls + "-" + key;
            png(chartsDir.resolve(file + ".png"), title, subtitle, ylabel, groups, ms, style, values, labels, footnote);
            mmd(mmdDir.resolve(file + ".mmd"), title, ylabel, shortNames, ms, values, labels, mmdc, pp);
            out.add(new Chart(key, caption, file + ".png", file + ".mmd"));
        };

        // 1. speed
        Map<String, double[]> rel = series(ms, tests, (m, t) -> on.get(key(jdkMethod, t)).ns() / on.get(key(m, t)).ns());
        Map<String, String[]> lab = new LinkedHashMap<>();
        for (String m : ms)
        {
            String[] texts = new String[tests.size()];
            for (int i = 0; i < tests.size(); i++)
            {
                double r = rel.get(m)[i];
                texts[i] = ms.size() <= 4
                        ? Py.f(r, 1) + "x\n" + Py.f(on.get(key(m, tests.get(i))).mcalls(), 1, true) + " M/s"
                        : Py.f(r, 1) + "x";
            }
            lab.put(m, texts);
        }
        String best = ec.getFirst();
        for (String m : ec)
        {
            if (rel.get(m)[0] > rel.get(best)[0])
            {
                best = m;
            }
        }
        emit.emit("throughput", name + ": how many times faster than the " + jn,
                "Higher is better. " + jn + " = 1x. " + at(tests.getFirst()) + ", " + best + " is "
                        + Py.f(rel.get(best)[0], 0) + "x faster; labels also show million calls per second.",
                "times as fast as the " + jn + " (JDK = 1)", rel, lab,
                "Speed relative to the " + jn + ", compact headers on.", null);

        // 2. garbage per call
        Map<String, double[]> v = series(ms, tests, (m, t) -> on.get(key(m, t)).bytes().doubleValue());
        emit.emit("bytes-per-call", name + ": garbage left behind per call",
                "Lower is better. The " + jn + " leaves " + rng(v.get(jdk)) + " per call; every EC version leaves "
                        + b(maxOf(ec, v)) + ". Escape analysis on (normal JVM).",
                "bytes allocated per call", v, texts(v, ShakersCharts::b),
                "Bytes allocated per call (gc.alloc.rate.norm), compact headers on.", null);

        // 3. GC per billion calls
        v = series(ms, tests, (m, t) -> on.get(key(m, t)).gcPerB());
        emit.emit("gc-per-billion", name + ": garbage collections per billion calls",
                "Lower is better. G1 with a 2 GB heap. The " + jn + " needs about " + Py.f(max(v.get(jdk)), 0, true)
                        + " young collections per billion calls; EC needs " + Py.f(maxOf(ec, v), 0, true) + ".",
                "GC runs per 1 billion calls", v, texts(v, x -> Py.f(x, 0, true)),
                "Garbage collections per billion calls, compact headers on.", null);

        // 4. escape analysis off
        v = series(ms, tests, (m, t) -> ea.get(key(m, t)).bytes().doubleValue());
        List<String> free = new ArrayList<>();
        List<String> cap = new ArrayList<>();
        boolean anyBound = false;
        for (String m : ec)
        {
            String family = classify(cls, m)[0];
            if (family.equals("free"))
            {
                free.add(m);
            }
            if (family.equals("cap") || family.equals("lazy"))
            {
                cap.add(m);
            }
        }
        for (String m : ms)
        {
            anyBound |= classify(cls, m)[0].equals("bound");
        }
        String sub = "Lower is better. With escape analysis off the JIT cannot delete short-lived objects, so this shows what each lambda shape really costs.";
        if (free.isEmpty() && cap.isEmpty())
        {
            sub = "Lower is better. With escape analysis off the JIT cannot delete short-lived objects, so this shows what each call really allocates.";
        }
        if (!free.isEmpty() && !cap.isEmpty())
        {
            boolean optional = false;
            for (String m : free)
            {
                optional |= m.contains("Optional");
            }
            sub += " Capturing EC: up to " + b(maxOf(cap, v)) + "; fat-free EC: up to " + b(maxOf(free, v))
                    + " (no lambda object, only the Integer box" + (optional ? " and the returned Optional" : "") + ").";
        }
        String foot = anyBound
                ? "Black bars: EC 13.0.0 ImmutableList inherits these *With methods from AbstractRichIterable, which wraps the "
                + "call in Predicates.bind, so they allocate like the capturing versions."
                : null;
        emit.emit("ea-off-bytes", name + ": garbage per call when escape analysis is off",
                sub, "bytes allocated per call (-XX:-DoEscapeAnalysis)", v, texts(v, ShakersCharts::b),
                "Bytes per call with -XX:-DoEscapeAnalysis: the upper bound for lambda garbage.", foot);

        // 5. compact headers
        v = series(ms, tests, (m, t) -> off.get(key(m, t)).bytes().doubleValue() - on.get(key(m, t)).bytes().doubleValue());
        Map<String, String[]> cohLabels = new LinkedHashMap<>();
        Map<String, double[]> saved = new LinkedHashMap<>();
        for (String m : ms)
        {
            String[] texts = new String[tests.size()];
            double[] clipped = new double[tests.size()];
            for (int i = 0; i < tests.size(); i++)
            {
                double x = v.get(m)[i];
                double offBytes = off.get(key(m, tests.get(i))).bytes().doubleValue();
                texts[i] = b(Math.max(x, 0)) + (offBytes > 1 ? "\n(" + Py.f(x / offBytes * 100, 0) + "%)" : "");
                clipped[i] = Math.max(x, 0);
            }
            cohLabels.put(m, texts);
            saved.put(m, clipped);
        }
        double jdkMax = -Double.MAX_VALUE;
        for (Integer t : tests)
        {
            jdkMax = Math.max(jdkMax, on.get(key(jdk, t)).bytes().doubleValue());
        }
        emit.emit("coh-bytes", name + ": bytes per call saved by compact object headers",
                "Higher means compact headers helped more. They trim the " + jn + " by " + rng(v.get(jdk))
                        + " per call; EC had nothing to trim. Not allocating at all saves the whole " + b(jdkMax) + ".",
                "bytes saved per call (headers off minus on)", saved, cohLabels,
                "Bytes per call saved by -XX:+UseCompactObjectHeaders (off minus on).", null);

        // 6. spike headroom
        v = series(ms, tests, (m, t) -> on.get(key(m, t)).bytes().doubleValue() * 10);
        emit.emit("spike-headroom", name + ": heap needed to absorb a 10-second spike",
                "Lower is better. Derived: bytes per call x 1 million calls/s x 10 s. The " + jn + " needs up to "
                        + Py.f(max(v.get(jdk)), 0, true) + " MB of young generation (or that many MB of extra collections); EC needs "
                        + Py.f(maxOf(ec, v), 0, true) + " MB.",
                "MB allocated in 10 s at 1 million calls/s", v, texts(v, x -> Py.f(x, 0, true) + " MB"),
                "Young-generation memory a 10 s spike at 1 million calls/s allocates (derived).", null);
        return out;
    }

    @FunctionalInterface
    private interface Emitter
    {
        void emit(String key, String title, String subtitle, String ylabel, Map<String, double[]> values,
                Map<String, String[]> labels, String caption, String footnote) throws IOException;
    }

    private static JmhParse.Key key(String m, Integer t)
    {
        return new JmhParse.Key(m, t);
    }

    private static Map<String, double[]> series(List<String> ms, List<Integer> tests, BiFunction<String, Integer, Double> value)
    {
        Map<String, double[]> out = new LinkedHashMap<>();
        for (String m : ms)
        {
            double[] row = new double[tests.size()];
            for (int i = 0; i < tests.size(); i++)
            {
                row[i] = value.apply(m, tests.get(i));
            }
            out.put(m, row);
        }
        return out;
    }

    private static Map<String, String[]> texts(Map<String, double[]> values, java.util.function.DoubleFunction<String> format)
    {
        Map<String, String[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : values.entrySet())
        {
            String[] row = new String[e.getValue().length];
            for (int i = 0; i < row.length; i++)
            {
                row[i] = format.apply(e.getValue()[i]);
            }
            out.put(e.getKey(), row);
        }
        return out;
    }
}
