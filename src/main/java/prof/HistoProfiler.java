package prof;

import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.Collection;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import org.eclipse.collections.impl.tuple.Tuples;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;
import org.openjdk.jmh.runner.IterationType;

/**
 * Counts the objects a benchmark allocates per operation, broken down by class.
 *
 * <p>How it works: before each measurement iteration it forces a GC and takes a class histogram of the
 * whole heap; after the iteration it takes a second histogram. The difference, divided by the number of
 * operations, is what each operation allocated. {@code GC.class_histogram -all} is used so that the
 * histogram also counts <em>unreachable</em> objects (it skips the full GC that a plain histogram does).
 * That is the trick: short-lived garbage is still sitting in the heap at the end of the iteration, so it
 * can be counted.
 *
 * <p>This only works if no GC ran during the iteration (a GC would delete the garbage before the second
 * histogram). Run it with a young generation big enough for one iteration (for example
 * {@code -XX:+UseParallelGC -Xmn10g}) and short iterations, and trust only iterations reported as
 * {@code clean=true}.
 *
 * <p>Output: one block of {@code HISTO} lines per measurement iteration on stdout (the forked VM's
 * console, not JMH's {@code -o} file), plus three JMH secondary results. The per-class lines are the
 * numbers to use. The {@code total} line and {@code histo.*.norm} results also include objects this
 * profiler allocates itself after the first histogram (parsing it, the {@code jcmd} process), so they
 * are slightly too high.
 */
public class HistoProfiler implements InternalProfiler
{

    /** Instances and bytes of one class in a histogram. */
    record Counts(long instances, long bytes)
    {
        static final Counts ZERO = new Counts(0, 0);
    }

    /** Histogram taken in {@link #beforeIteration}, keyed by class name. */
    private MutableMap<String, Counts> before;

    /** Total GC count when the "before" histogram was taken; compared afterwards to detect a GC. */
    private long gcCountBefore;

    @Override
    public String getDescription()
    {
        return "objects allocated per op, by class";
    }

    /** Sum of collections run so far by every collector (young and old). */
    static long gcCount()
    {
        return ListAdapter.adapt(ManagementFactory.getGarbageCollectorMXBeans())
                .sumOfLong(GarbageCollectorMXBean::getCollectionCount);
    }

    /**
     * Takes a class histogram of this VM, including unreachable objects, by running
     * {@code jcmd <own pid> GC.class_histogram -all}.
     */
    static MutableMap<String, Counts> histo()
    {
        try
        {
            String pid = String.valueOf(ProcessHandle.current().pid());
            // Send jcmd's output to a temp file, not a pipe. The VM writes the histogram while it is at a
            // safepoint, so every Java thread, including this one, is stopped. A histogram of all classes
            // is larger than the 64 KB pipe buffer; once the pipe is full, jcmd waits for this thread to
            // read it and this thread waits for jcmd to finish: a deadlock. A file never fills up.

            File out = File.createTempFile("histo", ".txt");

            Process p = new ProcessBuilder("jcmd", pid, "GC.class_histogram", "-all")
                    .redirectErrorStream(true)
                    .redirectOutput(out)
                    .start();
            p.waitFor();

            String text = Files.readString(out.toPath());
            out.delete();

            // Data lines look like "   1:   12345   678900  java.lang.String (java.base@25)":
            // rank followed by ':', instance count, bytes, class name, optional module. The header,
            // separator and "Total" lines have no "<rank>:" first field and are skipped.
            MutableMap<String, Counts> histogram = Maps.mutable.empty();
            ArrayAdapter.adapt(text.split("\n"))
                    .collect(line -> line.trim().split("\\s+"))
                    .select(fields -> fields.length >= 4 && fields[0].endsWith(":"))
                    .forEach(fields -> histogram.put(fields[3],
                            new Counts(Long.parseLong(fields[1]), Long.parseLong(fields[2]))));
            return histogram;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams)
    {
        // Warmup iterations are not counted; skipping them also keeps warmup fast.
        if (iterationParams.getType() != IterationType.MEASUREMENT)
        {
            return;
        }

        // Empty the young generation first so the iteration starts with as much free space as possible
        // and is less likely to trigger a GC (which would make it unclean).
        System.gc();
        before = histo();
        gcCountBefore = gcCount();
    }

    @Override
    public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams,
                                                       IterationParams iterationParams,
                                                       IterationResult iterationResult)
    {
        if (iterationParams.getType() != IterationType.MEASUREMENT)
        {
            return Lists.fixedSize.empty();
        }

        MutableMap<String, Counts> after = histo();

        // If any collection ran during the iteration, garbage was deleted before it could be counted.
        boolean clean = gcCount() == gcCountBefore;

        // Operations JMH ran in this iteration (all threads). Dividing by it turns heap growth into
        // objects and bytes per operation.
        double ops = iterationResult.getMetadata().getAllOps();

        // Per class: (after - before) / ops, as {objects per op, bytes per op}.
        // A class missing from "before" had no instances then.
        MutableList<Pair<String, double[]>> perClass = after.keyValuesView()
                .collect(kv ->
                {
                    Counts b = before.getIfAbsentValue(kv.getOne(), Counts.ZERO);
                    Counts a = kv.getTwo();
                    double[] perOp = {(a.instances() - b.instances()) / ops, (a.bytes() - b.bytes()) / ops};
                    return Tuples.pair(kv.getOne(), perOp);
                })
                .toList();
        double objs = perClass.sumOfDouble(p -> p.getTwo()[0]);
        double bytes = perClass.sumOfDouble(p -> p.getTwo()[1]);

        // Printed as one string so blocks from different iterations never interleave. The format is
        // parsed by the analysis tools (parse.py): keep it unchanged.
        StringBuilder sb = new StringBuilder("HISTO " + benchmarkParams.getBenchmark()
                + " test=" + benchmarkParams.getParam("test") + " clean=" + clean + "\n");

        // Only classes with at least half an object per op; smaller values are noise from the JIT,
        // the profiler itself and other background activity, not from the benchmark method.
        perClass.select(p -> p.getTwo()[0] >= 0.5)
                .forEach(p -> sb.append(String.format("HISTO   %.3f obj  %.1f B  %s%n",
                        p.getTwo()[0], p.getTwo()[1], p.getOne())));

        sb.append(String.format("HISTO   total %.3f obj/op %.1f B/op ops=%.0f%n", objs, bytes, ops));
        System.out.print(sb);

        // AVG across iterations for the counts; MIN for "clean" so a single GC-hit iteration shows 0.
        return Lists.mutable.with(
                new ScalarResult("histo.objs.norm", objs, "obj/op", AggregationPolicy.AVG),
                new ScalarResult("histo.bytes.norm", bytes, "B/op", AggregationPolicy.AVG),
                new ScalarResult("histo.clean", clean ? 1 : 0, "bool", AggregationPolicy.MIN));
    }
}
