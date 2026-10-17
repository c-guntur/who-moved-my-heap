package tools.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full summary of one run: the shakers and movers summaries in one AsciiDoc.
 * <p>
 * Usage (Java 25, from the project root): java src/main/java/tools/analysis/RunSummary.java . <RUN_TS>
 * Reads generated/runs/<RUN_TS>/{shakers,movers}/reports/<RUN_TS>-Summary.adoc (written by ShakersDocs and
 * MoversDocs) and writes generated/runs/<RUN_TS>/<RUN_TS>-Summary.adoc (instructions.md L). A part without
 * a summary is named as missing. Links and images are rewritten so they resolve from the run folder.
 */
public class RunSummary
{
    private static final String[][] PARTS = {
            {"shakers", "Shakers: garbage and speed per call (JMH)"},
            {"movers", "Movers: memory footprint (JOL)"}};

    // link:X[...] and image::X[...] with a relative target
    private static final Pattern LINK = Pattern.compile("(link:|image::)(?!https?:|/)([^\\[\\s]+)\\[");

    public static void main(String[] args) throws IOException
    {
        String root = args[0];
        String runTs = args[1];
        Path run = Path.of(root, Layout.GENERATED, "runs", runTs);
        List<String> body = new ArrayList<>();
        List<String> present = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        String environment = null;
        for (String[] part : PARTS)
        {
            Path summary = run.resolve(part[0]).resolve("reports").resolve(runTs + "-Summary.adoc");
            if (!Files.exists(summary))
            {
                missing.add(part[0]);
                continue;
            }
            present.add(part[0]);
            if (environment == null)
            {
                Path meta = run.resolve(part[0]).resolve("raw").resolve("meta.txt");
                if (Files.exists(meta))
                {
                    environment = Layout.environment(Files.readString(meta, StandardCharsets.UTF_8));
                }
            }
            body.add("== " + part[1]);
            body.add("");
            body.add("From link:" + part[0] + "/reports/" + runTs + "-Summary.adoc[the " + part[0] + " summary].");
            body.add("");
            body.addAll(section(Files.readAllLines(summary, StandardCharsets.UTF_8), part[0] + "/reports/"));
        }
        if (present.isEmpty())
        {
            System.err.println("No part summaries in " + run + "; run ShakersDocs or MoversDocs first.");
            System.exit(1);
        }

        List<String> doc = new ArrayList<>();
        doc.add("= Run " + runTs + ": full summary");
        doc.add(":toc:");
        doc.add(":toclevels: 2");
        doc.add("");
        doc.add("Environment: " + (environment != null ? environment : "not recorded in meta.txt") + ". "
                + "Written by `RunSummary.java` from the summaries of each part"
                + (missing.isEmpty() ? "." : "; this run has no " + String.join(" or ", missing) + " results."));
        doc.add("");
        doc.addAll(body);
        while (doc.get(doc.size() - 1).isEmpty())
        {
            doc.remove(doc.size() - 1);
        }
        Path out = run.resolve(runTs + "-Summary.adoc");
        Files.writeString(out, String.join("\n", doc) + "\n", StandardCharsets.UTF_8);
        System.out.println(out);
    }

    // A part summary without its title and attributes, one heading level down, links relative to the run folder
    private static List<String> section(List<String> lines, String prefix)
    {
        List<String> out = new ArrayList<>();
        boolean header = true;
        for (String line : lines)
        {
            if (header)
            {
                if (line.startsWith("= ") || line.startsWith(":") || line.isEmpty())
                {
                    continue;
                }
                header = false;
            }
            if (line.startsWith("=="))
            {
                line = "=" + line;
            }
            Matcher m = LINK.matcher(line);
            StringBuilder sb = new StringBuilder();
            while (m.find())
            {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + prefix + m.group(2) + "["));
            }
            m.appendTail(sb);
            out.add(sb.toString());
        }
        out.add("");
        return out;
    }
}
