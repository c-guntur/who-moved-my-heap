package tools.presentation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draws the title-slide (banner-NN, banner-intro-NN, banner-problem-NN, banner-appendix-NN), diagram and placeholder SVGs for the slides (assets/presentation/images).
 *
 * No dependencies, so it runs straight from source.
 * Usage (Java 25, from the project root): java src/main/java/tools/presentation/SlideImages.java .
 * The slides profile in pom.xml runs it before converting the slides.
 *
 * Colours: a navy slide palette plus the Okabe-Ito colours the charts use
 * (orange = JDK, sky blue = EC capturing, dark blue = fat-free, reddish purple = lazy).
 */
public class SlideImages
{
    private static final String NAVY = "#1B2A41";
    private static final String NAVY2 = "#2E4A6B";
    private static final String INK = "#1F2933";
    private static final String MUTED = "#5B6770";
    private static final String PAPER = "#F5F7FA";
    private static final String LINE = "#C9D2DC";
    private static final String ORANGE = "#E69F00";
    private static final String SKY = "#56B4E9";
    private static final String BLUE = "#0072B2";
    private static final String PURPLE = "#CC79A7";
    private static final String FONT = "font-family='Helvetica Neue, Helvetica, Arial, sans-serif'";
    private static final String MONO = "font-family='JetBrains Mono, Menlo, Consolas, monospace'";

    // Deck number, title and subtitle of each title slide; add a row for a new deck
    private static final String[][] SECTIONS = {
            {"00", "Definitions", "The vocabulary behind who moved my heap"},
            {"01", "\"Dollar\" classes", "Anonymous inner classes and their $ names"},
            {"02", "Object pooling", "Reusing objects instead of allocating them"},
            {"03", "Compressed OOPs", "32-bit references on a 64-bit JVM"},
            {"04", "Compact object headers", "One 8-byte header word (JEP 519, JDK 25)"},
            {"05", "Stream API", "ReferencePipeline, eager and lazy operations"},
            {"06", "Lambdas", "Capturing vs fat-free (non-capturing)"},
            {"07", "Escape analysis", "When the JIT can make an allocation disappear"}};

    // Title slide of the 01_Intro series (banner-intro-NN.svg): deck number, title and subtitle
    private static final String[][] INTRO_SECTIONS = {
            {"00", "Intro", "Five JDK tools for looking at the heap"}};

    // Title slide of the 02_ProblemStatement series (banner-problem-NN.svg): deck number, title and subtitle
    private static final String[][] PROBLEM_SECTIONS = {
            {"00", "Problem statement", "What JDK collections and streams leave on the heap"}};

    // Title slide of the 07_Appendix series (banner-appendix-NN.svg): deck number, title and subtitle
    private static final String[][] APPENDIX_SECTIONS = {
            {"00", "Appendix", "References for this talk"}};

    public static void main(String[] args) throws IOException
    {
        Path out = Path.of(args[0], "assets", "presentation", "images");
        Files.createDirectories(out);
        Map<String, String> files = new LinkedHashMap<>();
        for (String[] section : SECTIONS)
        {
            files.put("banner-" + section[0] + ".svg",
                    banner("WHO MOVED MY HEAP  ·  03 DEFINITIONS", section[0], section[1], section[2]));
        }
        for (String[] section : INTRO_SECTIONS)
        {
            files.put("banner-intro-" + section[0] + ".svg",
                    banner("WHO MOVED MY HEAP  ·  01 INTRO", section[0], section[1], section[2]));
        }
        for (String[] section : PROBLEM_SECTIONS)
        {
            files.put("banner-problem-" + section[0] + ".svg",
                    banner("WHO MOVED MY HEAP  ·  02 PROBLEM STATEMENT", section[0], section[1], section[2]));
        }
        for (String[] section : APPENDIX_SECTIONS)
        {
            files.put("banner-appendix-" + section[0] + ".svg",
                    banner("WHO MOVED MY HEAP  ·  07 APPENDIX", section[0], section[1], section[2]));
        }
        files.put("diagram-01-anonymous-class.svg", anon());
        files.put("diagram-02-integer-cache.svg", pool());
        files.put("diagram-03-compressed-oops.svg", oops());
        files.put("diagram-04-object-headers.svg", headers());
        files.put("diagram-05-pipeline.svg", pipeline());
        files.put("diagram-05-eager-lazy.svg", eagerLazy());
        files.put("diagram-06-lambdas.svg", lambdas());
        files.put("diagram-07-escape-analysis.svg", escape());
        files.put("diagram-problem-01-baseline-volatile.svg", baselineVolatile());
        files.put("placeholder-problem-02-jmap-intellij.svg",
                placeholder("jmap -histo <pid of IntelliJ IDEA>", "screenshot-problem-02-jmap-intellij.png"));
        files.put("placeholder-problem-02-jmap-sonarqube.svg",
                placeholder("jmap -histo <pid of the SonarQube for IDE process>", "screenshot-problem-02-jmap-sonarqube.png"));
        for (Map.Entry<String, String> file : files.entrySet())
        {
            Files.writeString(out.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
        }
        System.out.println(files.size() + " SVGs in " + out);
    }

    // Numbers print the way the earlier Python version printed them: whole numbers as "40",
    // computed positions as doubles ("220.0", "99.69999999999999"), so the SVGs stay byte-identical.
    private static String num(Number value)
    {
        return value.toString();
    }

    private static String esc(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String svg(int width, int height, String body, String background)
    {
        String rect = background != null
                ? "<rect width='" + width + "' height='" + height + "' fill='" + background + "'/>"
                : "";
        return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 " + width + " " + height + "' "
                + "width='" + width + "' height='" + height + "' " + FONT + ">"
                + "<defs><marker id='arr' viewBox='0 0 10 10' refX='9' refY='5' markerWidth='7' markerHeight='7' "
                + "orient='auto-start-reverse'><path d='M0,0 L10,5 L0,10 z' fill='" + MUTED + "'/></marker>"
                + "<linearGradient id='band' x1='0' y1='0' x2='1' y2='1'><stop offset='0' stop-color='" + NAVY + "'/>"
                + "<stop offset='1' stop-color='" + NAVY2 + "'/></linearGradient></defs>"
                + rect + body + "</svg>\n";
    }

    private static String text(Number x, Number y, String value, int size, String fill, String weight, String anchor,
            boolean mono)
    {
        String font = mono ? MONO : "";
        return "<text x='" + num(x) + "' y='" + num(y) + "' font-size='" + size + "' fill='" + fill
                + "' font-weight='" + weight + "' text-anchor='" + anchor + "' " + font + ">" + esc(value) + "</text>";
    }

    private static String text(Number x, Number y, String value, int size, String fill, String weight)
    {
        return text(x, y, value, size, fill, weight, "start", false);
    }

    private static String text(Number x, Number y, String value, int size, String fill)
    {
        return text(x, y, value, size, fill, "normal", "start", false);
    }

    private static String box(int x, int y, int w, int h, String fill, String label, int size, String fg, String stroke,
            boolean dash, String weight, boolean mono)
    {
        String s = stroke != null ? " stroke='" + stroke + "' stroke-width='2'" : "";
        String d = dash ? " stroke-dasharray='8 6'" : "";
        StringBuilder out = new StringBuilder("<rect x='" + x + "' y='" + y + "' width='" + w + "' height='" + h
                + "' rx='8' fill='" + fill + "'" + s + d + "/>");
        if (!label.isEmpty())
        {
            String[] lines = label.split("\n", -1);
            double top = y + h / 2.0 - (lines.length - 1) * size * 0.6;
            for (int i = 0; i < lines.length; i++)
            {
                out.append(text(x + w / 2.0, top + i * size * 1.2 + size * 0.35, lines[i], size, fg, weight, "middle", mono));
            }
        }
        return out.toString();
    }

    private static String box(int x, int y, int w, int h, String fill, String label, int size, String fg, String stroke)
    {
        return box(x, y, w, h, fill, label, size, fg, stroke, false, "bold", false);
    }

    private static String box(int x, int y, int w, int h, String fill, String label, int size, String fg)
    {
        return box(x, y, w, h, fill, label, size, fg, null, false, "bold", false);
    }

    private static String box(int x, int y, int w, int h, String fill, String label, int size)
    {
        return box(x, y, w, h, fill, label, size, "white", null, false, "bold", false);
    }

    // A white box with a grey outline and a normal-weight label (code or plain text)
    private static String plain(int x, int y, int w, int h, String label, int size, String fg, boolean mono)
    {
        return box(x, y, w, h, "white", label, size, fg, LINE, false, "normal", mono);
    }

    private static String arrow(int x1, int y1, int x2, int y2, boolean dash)
    {
        String d = dash ? " stroke-dasharray='6 5'" : "";
        return "<line x1='" + x1 + "' y1='" + y1 + "' x2='" + x2 + "' y2='" + y2 + "' stroke='" + MUTED
                + "' stroke-width='2.5'" + d + " marker-end='url(#arr)'/>";
    }

    private static String arrow(int x1, int y1, int x2, int y2)
    {
        return arrow(x1, y1, x2, y2, false);
    }

    // 16:9 title slide for a deck (the first slide of every 03_Definitions_NN deck)
    private static String banner(String kicker, String number, String title, String subtitle)
    {
        String body = "<rect width='1600' height='900' fill='url(#band)'/>"
                + "<rect x='0' y='0' width='24' height='900' fill='" + ORANGE + "'/>"
                + "<circle cx='1380' cy='640' r='420' fill='" + SKY + "' opacity='0.08'/>"
                + "<circle cx='1500' cy='180' r='230' fill='" + ORANGE + "' opacity='0.10'/>"
                + text(120, 300, kicker, 30, SKY, "bold")
                + "<rect x='120' y='330' width='160' height='6' fill='" + ORANGE + "'/>"
                + text(120, 470, title, 104, "white", "bold")
                + text(120, 560, subtitle, 44, "#D8E1EA")
                + text(1460, 820, number, 150, "white", "bold", "end", false);
        return svg(1600, 900, body, null);
    }

    private static String anon()
    {
        String b = box(40, 40, 360, 70, NAVY, "Shaker  (enclosing instance)", 22)
                + box(40, 200, 360, 70, SKY, "Integer target  (captured local)", 22, INK)
                + "<rect x='560' y='60' width='520' height='230' rx='10' fill='white' stroke='" + ORANGE + "' stroke-width='3'/>"
                + text(820, 100, "Shaker$1   (anonymous class)", 26, INK, "bold", "middle", true)
                + box(590, 125, 460, 40, PAPER, "object header", 20, INK, LINE, false, "normal", false)
                + box(590, 175, 460, 40, PAPER, "Shaker this$0", 20, INK, LINE, false, "normal", true)
                + box(590, 225, 460, 40, PAPER, "Integer val$target", 20, INK, LINE, false, "normal", true)
                + arrow(590, 195, 405, 85) + arrow(590, 245, 405, 235)
                + text(1110, 120, "javac writes", 22, MUTED)
                + text(1110, 150, "Shaker$1.class", 22, INK, "bold", "start", true)
                + text(1110, 210, "new instance on", 22, MUTED)
                + text(1110, 240, "every evaluation", 22, ORANGE, "bold");
        return svg(1400, 330, b, PAPER);
    }

    private static String pool()
    {
        StringBuilder b = new StringBuilder(text(40, 50,
                "Integer.valueOf(int): the JDK keeps one shared Integer per value from -128 to 127", 24, INK, "bold"));
        String[] values = {"-128", "-127", "…", "0", "1", "…", "126", "127"};
        for (int i = 0; i < values.length; i++)
        {
            boolean gap = values[i].equals("…");
            b.append(box(40 + i * 120, 90, 105, 60, gap ? "white" : BLUE, values[i], 22,
                    gap ? MUTED : "white", gap ? LINE : null));
        }
        b.append(text(40, 180, "IntegerCache: allocated once, reused forever", 20, MUTED));
        b.append(plain(1080, 90, 280, 60, "Integer x = 100;", 22, INK, true));
        b.append(arrow(1080, 120, 1000, 120));
        b.append(plain(40, 230, 330, 60, "Integer y = 1000;", 22, INK, true));
        b.append(arrow(370, 260, 470, 260));
        b.append(box(480, 230, 300, 60, ORANGE, "new Integer  16 B", 22, INK));
        b.append(text(800, 268, "outside the pool: a new box on every boxing", 22, MUTED));
        return svg(1400, 320, b.toString(), PAPER);
    }

    private static String oops()
    {
        String b = text(40, 50, "A reference field: uncompressed vs compressed", 26, INK, "bold")
                + text(40, 110, "64-bit address", 22, MUTED)
                + box(260, 80, 800, 50, ORANGE, "8 bytes", 22, INK)
                + text(40, 190, "compressed OOP", 22, MUTED)
                + box(260, 160, 400, 50, BLUE, "4 bytes", 22)
                + text(690, 193, "half the size in every field and Object[] slot", 22, INK)
                + plain(260, 240, 800, 60, "address = heap base + (offset << 3)", 24, INK, true)
                + text(1100, 265, "2^32 × 8-byte alignment", 20, MUTED)
                + text(1100, 292, "= heaps up to 32 GB", 22, INK, "bold");
        return svg(1400, 330, b, PAPER);
    }

    // One row of the object-header diagram: cells are {width in 4-byte units, fill, name, text colour}
    private static String layout(int y, String label, String[][] cells, String total, String note)
    {
        StringBuilder out = new StringBuilder(text(40, y + 34, label, 22, MUTED));
        int x = 300;
        for (String[] cell : cells)
        {
            int width = Integer.parseInt(cell[0]);
            out.append(box(x, y, width * 22, 52, cell[1], cell[2], 18, cell[3], "white"));
            x += width * 22;
        }
        out.append(text(x + 20, y + 34, total, 24, INK, "bold"));
        out.append(text(x + 110, y + 34, note, 20, MUTED));
        return out.toString();
    }

    private static String headers()
    {
        String[][] legacy = {{"8", NAVY, "mark word 8", "white"}, {"4", NAVY2, "class 4", "white"}, {"4", SKY, "hash", INK},
                {"4", SKY, "key", INK}, {"4", SKY, "value", INK}, {"4", SKY, "next", INK}, {"4", LINE, "pad", INK}};
        String[][] compact = {{"8", ORANGE, "header 8", INK}, {"4", SKY, "hash", INK}, {"4", SKY, "key", INK},
                {"4", SKY, "value", INK}, {"4", SKY, "next", INK}};
        String b = text(40, 50, "HashMap$Node layout (box width = bytes)", 26, INK, "bold")
                + layout(90, "compact headers off", legacy, "32 B", "12-byte header, padded")
                + layout(180, "compact headers on", compact, "24 B", "mark + class in one word")
                + text(40, 300, "1M HashMap entries: 56.4 MB → 48.4 MB.  An Integer stays 16 B either way (12 B rounds up to 16).",
                        22, INK);
        return svg(1400, 330, b, PAPER);
    }

    private static String pipeline()
    {
        StringBuilder b = new StringBuilder(text(40, 50, "list.stream().filter(p).findFirst(): what one call allocates",
                26, INK, "bold"));
        String[][] stages = {{"list.stream()", "Head + Spliterator", "80 B"},
                {".filter(p)", "StatelessOp stage", "72 B"},
                {".findFirst()", "Sink + Optional", "32 B"}};
        for (int i = 0; i < stages.length; i++)
        {
            int x = 40 + i * 400;
            b.append(text(x + 160, 100, stages[i][0], 22, INK, "bold", "middle", true));
            b.append(box(x, 115, 320, 90, ORANGE, stages[i][1] + "\n" + stages[i][2], 22, INK));
            if (i > 0)
            {
                b.append(arrow(x - 75, 160, x - 5, 160));
            }
        }
        b.append("<rect x='40' y='235' width='720' height='6' fill='" + PURPLE + "'/>");
        b.append(text(40, 275, "intermediate operations (lazy): only link a new stage", 20, PURPLE, "bold"));
        b.append("<rect x='840' y='235' width='320' height='6' fill='" + BLUE + "'/>");
        b.append(text(840, 275, "terminal (eager): runs it now", 20, BLUE, "bold"));
        b.append(text(40, 312, "+ captured lambda 16 B = 200 B per call.  The EC loop allocates 0 B.", 20, MUTED));
        return svg(1400, 330, b.toString(), PAPER);
    }

    private static String eagerLazy()
    {
        StringBuilder b = new StringBuilder(text(40, 50, "Same question, two shapes of work", 26, INK, "bold"));
        b.append(text(40, 110, "eager steps", 22, BLUE, "bold"));
        String[] eager = {"step 1: every element", "new collection", "step 2: every element", "result"};
        for (int i = 0; i < eager.length; i++)
        {
            boolean step = i % 2 == 0;
            b.append(box(220 + i * 280, 80, 220, 50, step ? BLUE : "white", eager[i], 18,
                    step ? "white" : INK, step ? null : LINE));
            if (i > 0)
            {
                b.append(arrow(220 + i * 280 - 55, 105, 220 + i * 280 - 5, 105));
            }
        }
        b.append(text(40, 220, "lazy chain", 22, PURPLE, "bold"));
        String[] lazy = {"filter (recorded)", "map (recorded)"};
        for (int i = 0; i < lazy.length; i++)
        {
            b.append(box(220 + i * 280, 190, 220, 50, "white", lazy[i], 20, PURPLE, PURPLE, true, "bold", false));
            b.append(arrow(220 + i * 280 + 225, 215, 220 + i * 280 + 275, 215, true));
        }
        b.append(box(780, 190, 220, 50, BLUE, "terminal op", 20));
        b.append(arrow(1005, 215, 1055, 215));
        b.append(box(1060, 190, 220, 50, "white", "one element at a time", 18, INK, LINE));
        b.append(text(220, 290, "Lazy steps do nothing until an eager terminal operation pulls elements through them.", 20, MUTED));
        return svg(1400, 320, b.toString(), PAPER);
    }

    private static String lambdas()
    {
        StringBuilder b = new StringBuilder(text(40, 50, "Three calls to the same method", 26, INK, "bold"));
        b.append(text(40, 100, "capturing:  each -> each.equals(target)", 22, INK, "bold", "start", true));
        for (int i = 0; i < 3; i++)
        {
            b.append(box(40 + i * 200, 120, 170, 46, "white", "call " + (i + 1), 20, INK, LINE));
            b.append(arrow(125 + i * 200, 166, 125 + i * 200, 206));
            b.append(box(40 + i * 200, 210, 170, 50, SKY, "new lambda", 20, INK));
        }
        b.append(text(40, 300, "3 objects, each holding target", 20, MUTED));
        b.append(text(760, 100, "fat-free:  Object::equals, target", 22, INK, "bold", "start", true));
        for (int i = 0; i < 3; i++)
        {
            b.append(box(760 + i * 200, 120, 170, 46, "white", "call " + (i + 1), 20, INK, LINE));
            b.append(arrow(845 + i * 200, 166, 1045, 206));
        }
        b.append(box(930, 210, 230, 50, BLUE, "one shared instance", 20));
        b.append(text(760, 300, "target travels as an argument", 20, MUTED));
        return svg(1400, 320, b.toString(), PAPER);
    }

    private static String escape()
    {
        String b = text(40, 50, "After inlining, C2 asks: can anyone outside this method see the new object?", 24, INK, "bold")
                + plain(40, 120, 300, 70, "new Object()", 22, INK, true)
                + arrow(340, 155, 440, 115) + arrow(340, 155, 440, 245)
                + box(450, 80, 300, 70, BLUE, "does not escape", 22)
                + arrow(750, 115, 830, 115)
                + box(840, 80, 520, 70, "white", "scalar replaced: fields in registers, 0 B", 22, BLUE, BLUE)
                + box(450, 210, 300, 70, ORANGE, "escapes", 22, INK)
                + arrow(750, 245, 830, 245)
                + box(840, 210, 520, 70, "white", "allocated on the heap, collected by GC", 22, INK, ORANGE)
                + text(40, 330, "Escapes = stored in a field, returned, or passed to code that was not inlined.  "
                        + "Turn it off: -XX:-DoEscapeAnalysis", 20, MUTED);
        return svg(1400, 360, b, PAPER);
    }

    // Heap over time (an illustration, not data): a baseline the collections hold, a sawtooth of
    // short-lived garbage on top of it, and a spike where the same code runs more often
    private static String baselineVolatile()
    {
        int left = 80;
        int right = 1060;
        int floor = 290;
        int base = 225;
        StringBuilder teeth = new StringBuilder("M" + left + "," + base);
        int x = left;
        while (x < right)
        {
            boolean spike = x >= 640 && x < 880;
            int width = spike ? 30 : 70;
            int top = spike ? 105 : 175;
            int end = Math.min(x + width, right);
            teeth.append(" L").append(end).append(",").append(top).append(" L").append(end).append(",").append(base);
            x = end;
        }
        String b = text(40, 40, "Heap used over time (illustration, not measured)", 24, INK, "bold")
                + "<line x1='" + left + "' y1='80' x2='" + right + "' y2='80' stroke='" + INK
                + "' stroke-width='2' stroke-dasharray='8 6'/>"
                + text(left + 6, 72, "maximum heap (-Xmx)", 18, MUTED)
                + "<path d='" + teeth + " Z' fill='" + ORANGE + "' fill-opacity='0.35' stroke='" + ORANGE
                + "' stroke-width='2'/>"
                + "<rect x='" + left + "' y='" + base + "' width='" + (right - left) + "' height='" + (floor - base)
                + "' fill='" + BLUE + "'/>"
                + text(left + 20, 266, "baseline: what the collections hold", 20, "white", "bold")
                + "<rect x='640' y='96' width='240' height='194' fill='none' stroke='" + PURPLE
                + "' stroke-width='2' stroke-dasharray='6 5'/>"
                + text(760, 70, "spike: 10x the calls", 18, PURPLE, "bold", "middle", false)
                + arrow(left, floor, right + 20, floor)
                + text(right + 10, 316, "time", 18, MUTED, "normal", "end", false)
                + box(1110, 100, 260, 60, ORANGE, "volatile: garbage\nper call (shakers)", 18, INK)
                + box(1110, 225, 260, 60, BLUE, "baseline: footprint\n(movers)", 18)
                + text(1110, 200, "headroom = max - baseline", 18, MUTED);
        return svg(1400, 330, b, PAPER);
    }

    // An empty frame that marks where a screenshot goes; replace the image reference once it exists
    private static String placeholder(String command, String file)
    {
        String b = "<rect x='20' y='20' width='1360' height='540' rx='12' fill='white' stroke='" + LINE
                + "' stroke-width='3' stroke-dasharray='14 10'/>"
                + text(700, 250, "Screenshot to add", 36, MUTED, "bold", "middle", false)
                + text(700, 310, command, 26, INK, "normal", "middle", true)
                + text(700, 370, "images/" + file, 22, MUTED, "normal", "middle", true);
        return svg(1400, 580, b, PAPER);
    }
}
