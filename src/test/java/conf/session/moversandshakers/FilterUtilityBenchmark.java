package conf.session.moversandshakers;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.impl.list.Interval;
import org.eclipse.collections.impl.utility.Iterate;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 10, time = 2)
@Measurement(iterations = 10, time = 2)
public class FilterUtilityBenchmark
{
    private static final int COUNT = 1_000;

    @Param({"1", "500", "1000"})
    Integer test;

    private final List<Integer> jdkList = List.copyOf(Interval.oneTo(COUNT));

    @Benchmark
    public Set<Integer> jdkFilter()
    {
        return jdkList.stream()
                .filter(each -> each <= test)
                .collect(Collectors.toSet());
    }

    @Benchmark
    public Set<Integer> ecSelect()
    {
        return Iterate.select(jdkList, each -> each <= test, Sets.mutable.empty());
    }

    @Benchmark
    public Set<Integer> ecSelectWith()
    {
        return Iterate.selectWith(jdkList, (each, compare) -> each <= compare, this.test, Sets.mutable.empty());
    }
}
