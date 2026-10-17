package tools.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads JMH text results (-rf text) and the HISTO lines HistoProfiler prints.
 * Benchmarks without a @Param have no "test" column and print test=null; their test is null here.
 */
final class JmhParse
{
    private static final String NUM = "(?:≈\\s*0|≈\\s*10[⁻\\-]?[⁰¹²³⁴⁵⁶⁷⁸⁹0-9]+|-?[\\d.]+(?:E-?\\d+)?|NaN)";
    private static final Pattern ROW = Pattern.compile("^(\\w+)\\.(\\w+)(?::([\\w.]+))?(?:\\s+(\\d+))?\\s+avgt\\s+(\\d+)\\s+("
            + NUM + ")\\s*(?:±\\s*(" + NUM + "))?\\s+(\\S+)\\s*$");
    private static final Pattern HISTO_HEAD = Pattern.compile("HISTO (\\S+) test=(\\S+) clean=(\\w+)");
    private static final Pattern HISTO_LINE = Pattern.compile("HISTO\\s+([\\d.]+) obj\\s+([\\d.]+) B\\s+(.+)$");

    private JmhParse()
    {
    }

    /** One benchmark method at one test value. */
    record Key(String method, Integer test)
    {
    }

    /** One benchmark class, method and test value in a histogram file. */
    record HistoKey(String benchClass, String method, Integer test)
    {
    }

    /** A JMH score: the number, its error and the score text as printed. */
    record Score(Double score, Double error, String text)
    {
    }

    /** Per-class objects and bytes per call, averaged over the clean iterations. */
    static final class Histo
    {
        int iters;
        int cleanIters;
        final Map<String, double[]> classes = new LinkedHashMap<>();
        double objs;
        double bytes;
    }

    static Double num(String s)
    {
        if (s == null)
        {
            return null;
        }
        s = s.strip();
        if (s.startsWith("≈"))
        {
            return 0.0;
        }
        return Double.parseDouble(s);
    }

    /** {(method, test): {metric: score}}; the primary ns/op score is under "ns". */
    static Map<Key, Map<String, Score>> parseTable(Path path) throws IOException
    {
        Map<Key, Map<String, Score>> out = new LinkedHashMap<>();
        for (String line : Files.readString(path, StandardCharsets.UTF_8).split("\n", -1))
        {
            Matcher m = ROW.matcher(line.strip());
            if (!m.matches())
            {
                continue;
            }
            String test = m.group(4);
            Key key = new Key(m.group(2), test != null ? Integer.valueOf(test) : null);
            String metric = m.group(3) != null && !m.group(3).isEmpty() ? m.group(3) : "ns";
            out.computeIfAbsent(key, k -> new LinkedHashMap<>())
                    .put(metric, new Score(num(m.group(6)), num(m.group(7)), m.group(6).strip()));
        }
        return out;
    }

    private static final class Block
    {
        String benchClass;
        String method;
        Integer test;
        boolean clean;
        final Map<String, double[]> classes = new LinkedHashMap<>();
    }

    /** {(class, method, test): histogram} averaged over clean iterations. */
    static Map<HistoKey, Histo> parseHisto(Path path) throws IOException
    {
        List<Block> blocks = new ArrayList<>();
        Block current = null;
        for (String line : Files.readString(path, StandardCharsets.UTF_8).split("\n", -1))
        {
            if (!line.startsWith("HISTO"))
            {
                continue;
            }
            Matcher head = HISTO_HEAD.matcher(line);
            if (head.lookingAt())
            {
                String[] name = head.group(1).split("\\.");
                current = new Block();
                current.benchClass = name[name.length - 2];
                current.method = name[name.length - 1];
                current.test = head.group(2).matches("\\d+") ? Integer.valueOf(head.group(2)) : null;
                current.clean = head.group(3).equals("true");
                blocks.add(current);
                continue;
            }
            Matcher m = HISTO_LINE.matcher(line.stripTrailing());
            if (m.lookingAt() && current != null)
            {
                current.classes.put(m.group(3), new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))});
            }
        }
        Map<HistoKey, Histo> agg = new LinkedHashMap<>();
        Map<HistoKey, Map<String, double[]>> sums = new LinkedHashMap<>();
        for (Block b : blocks)
        {
            HistoKey key = new HistoKey(b.benchClass, b.method, b.test);
            Histo h = agg.computeIfAbsent(key, k -> new Histo());
            Map<String, double[]> sum = sums.computeIfAbsent(key, k -> new LinkedHashMap<>());
            h.iters++;
            if (!b.clean)
            {
                continue;
            }
            h.cleanIters++;
            for (Map.Entry<String, double[]> c : b.classes.entrySet())
            {
                double[] s = sum.computeIfAbsent(c.getKey(), k -> new double[2]);
                s[0] += c.getValue()[0];
                s[1] += c.getValue()[1];
            }
        }
        for (Map.Entry<HistoKey, Histo> e : agg.entrySet())
        {
            Histo h = e.getValue();
            int n = h.cleanIters != 0 ? h.cleanIters : 1;
            // normalise lambda class names (the hidden-class suffix differs per run)
            for (Map.Entry<String, double[]> c : sums.get(e.getKey()).entrySet())
            {
                String name = c.getKey().replaceAll("/0x[0-9a-f]+", "");
                double[] prev = h.classes.getOrDefault(name, new double[]{0, 0});
                h.classes.put(name, new double[]{prev[0] + c.getValue()[0] / n, prev[1] + c.getValue()[1] / n});
            }
            double[] objs = new double[h.classes.size()];
            double[] bytes = new double[h.classes.size()];
            int i = 0;
            for (double[] v : h.classes.values())
            {
                objs[i] = v[0];
                bytes[i++] = v[1];
            }
            h.objs = Py.sum(objs);
            h.bytes = Py.sum(bytes);
        }
        return agg;
    }

    static boolean same(Integer a, Integer b)
    {
        return Objects.equals(a, b);
    }
}
