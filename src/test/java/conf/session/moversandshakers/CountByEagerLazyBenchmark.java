package conf.session.moversandshakers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.collections.api.bag.Bag;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.list.Interval;
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
public class CountByEagerLazyBenchmark
{
    private static final int COUNT = 1_000;

    @Param({"1", "100", "1000"})
    Integer test;

    private final List<Integer> jdkList = List.copyOf(Interval.oneTo(COUNT));
    private final ImmutableList<Integer> ecList = Interval.oneTo(COUNT).toImmutableList();

    @Benchmark
    public Map<Integer, Long> jdkCountBy()
    {
        return jdkList.stream()
                .collect(Collectors.groupingBy(each -> each % test, Collectors.counting()));
    }

    @Benchmark
    public Bag<Integer> ecCountBy()
    {
        return ecList.countBy(each -> each % test);
    }

    @Benchmark
    public Bag<Integer> ecCountByWith()
    {
        return ecList.countByWith((each, compare) -> each % compare, test);
    }

    @Benchmark
    public Bag<Integer> ecLazyCountBy()
    {
        return ecList.asLazy().countBy(each -> each % test);
    }
}
