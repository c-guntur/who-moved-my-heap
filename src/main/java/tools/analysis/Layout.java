package tools.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Where the files of one run live (instructions.md, section L). Every generator uses it.
 * <p>
 * Paths are relative to the project root; part is "shakers" or "movers":
 *   generated/runs/<RUN_TS>/<part>/raw/           raw output of the run, every file .txt
 *   generated/runs/<RUN_TS>/<part>/               data the generators pass on (summary.json)
 *   generated/runs/<RUN_TS>/<part>/reports/       results and analysis docs, run summary
 *   generated/runs/<RUN_TS>/<part>/reports/images/   PNG charts
 *   generated/runs/<RUN_TS>/<part>/reports/mermaid/  Mermaid sources
 * An environment variable (SHAKERS_RUN_DIR, MOVERS_RESULTS) can point at a raw folder kept elsewhere.
 */
final class Layout
{
    static final String GENERATED = "generated";

    // Links written into the reports, relative to the reports folder
    static final String IMG_LINK = "images";
    static final String MMD_LINK = "mermaid";

    final String rawRel;      // raw folder, relative to the project root
    final Path raw;
    final String outRel;
    final Path out;           // generated/runs/<RUN_TS>/<part>
    final String reportsRel;
    final Path reports;
    final Path images;
    final Path mermaid;

    private Layout(String root, String runTs, String part, String overrideVariable) throws IOException
    {
        String override = System.getenv(overrideVariable);
        outRel = GENERATED + "/runs/" + runTs + "/" + part;
        out = Path.of(root + "/" + outRel);
        rawRel = override != null && !override.isEmpty() ? override : outRel + "/raw";
        raw = Path.of(root + "/" + rawRel);
        reportsRel = outRel + "/reports";
        reports = Path.of(root + "/" + reportsRel);
        images = reports.resolve(IMG_LINK);
        mermaid = reports.resolve(MMD_LINK);
        for (Path dir : List.of(out, reports, images, mermaid))
        {
            Files.createDirectories(dir);
        }
    }

    static Layout shakers(String root, String runTs) throws IOException
    {
        return new Layout(root, runTs, "shakers", "SHAKERS_RUN_DIR");
    }

    static Layout movers(String root, String runTs) throws IOException
    {
        return new Layout(root, runTs, "movers", "MOVERS_RESULTS");
    }

    // The "environment:" line of meta.txt (section S3.1.3), or a plain fallback
    static String environment(String meta)
    {
        for (String line : meta.split("\n", -1))
        {
            if (line.startsWith("environment:"))
            {
                return line.substring(line.indexOf(':') + 1).strip();
            }
        }
        return "not recorded in meta.txt";
    }

    static String javaVersion(String meta)
    {
        for (String line : meta.split("\n", -1))
        {
            if (line.contains("version \""))
            {
                return line.strip();
            }
        }
        return "JDK version not recorded";
    }

    // AsciiDoc lines for one chart: the PNG from images/, a link to the Mermaid source in mermaid/
    static List<String> embed(String caption, String png, String mmd)
    {
        return List.of("." + caption, "image::" + IMG_LINK + "/" + png + "[" + caption + "]",
                "Mermaid source: link:" + MMD_LINK + "/" + mmd + "[" + mmd + "]", "");
    }

    // Lowest to highest of some formatted values: "160", or "160 to 176"
    static String range(List<Double> values, DoubleFunction<String> format)
    {
        if (values.isEmpty())
        {
            return "n/a";
        }
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (double v : values)
        {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        String a = format.apply(lo);
        String b = format.apply(hi);
        return a.equals(b) ? a : a + " to " + b;
    }
}
