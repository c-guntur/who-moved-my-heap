package conf.session;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
public class FindFirstEagerLazyBenchmark
{
    private static final int COUNT = 1_000;

    @Param({"1", "500", "1000"})
    int test;

    private final List<Integer> jdkList = List.copyOf(Interval.oneTo(COUNT));
    private final ImmutableList<Integer> ecList = Interval.oneTo(COUNT).toImmutableList();

    @Benchmark
    public Integer jdkFindFirst()
    {
        return jdkList.stream()
                .filter(Integer.valueOf(this.test)::equals)
                .findFirst()
                .orElse(0);
    }

    @Benchmark
    public Integer ecDetectOptional()
    {
        return ecList.detectOptional(Integer.valueOf(this.test)::equals).orElse(0);
    }

    @Benchmark
    public Integer ecLazyDetectOptional()
    {
        return ecList.asLazy().detectOptional(Integer.valueOf(this.test)::equals).orElse(0);
    }

    @Benchmark
    public Integer ecDetectWithOptional()
    {
        return ecList.detectWithOptional(Integer::equals, this.test).orElse(0);
    }

    @Benchmark
    public Integer ecDetectIfNone()
    {
        return ecList.detectIfNone(Integer.valueOf(this.test)::equals, () -> 0);
    }

    @Benchmark
    public Integer ecLazyDetectIfNone()
    {
        return ecList.asLazy().detectIfNone(Integer.valueOf(this.test)::equals, () -> 0);
    }

    @Benchmark
    public Integer ecDetectWithIfNone()
    {
        return ecList.detectWithIfNone(Integer::equals, this.test, () -> 0);
    }
}
