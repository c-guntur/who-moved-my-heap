package conf.session;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
public class AnyMatchUtilityBenchmark
{
    private static final int COUNT = 1_000;

    @Param({"1", "500", "1000"})
    int test;

    private final List<Integer> jdkList = List.copyOf(Interval.oneTo(COUNT));

    @Benchmark
    public boolean jdkAnyMatch()
    {
        return jdkList.stream().anyMatch(Integer.valueOf(this.test)::equals);
    }

    @Benchmark
    public boolean ecAnySatisfy()
    {
        return Iterate.anySatisfy(jdkList, Integer.valueOf(this.test)::equals);
    }

    @Benchmark
    public boolean ecAnySatisfyWith()
    {
        return Iterate.anySatisfyWith(jdkList, Integer::equals, this.test);
    }
}
