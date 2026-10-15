package conf.session.shakers;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.collections.impl.collector.Collectors2;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 10, time = 2)
@Measurement(iterations = 10, time = 2)
public class CollectorsAndCollectors2Benchmark
{
    @Benchmark
    public void collectors(Blackhole bh)
    {
        bh.consume(Collectors.toList());
        bh.consume(Collectors.toUnmodifiableList());
        bh.consume(Collectors.toSet());
        bh.consume(Collectors.toUnmodifiableSet());
        // Simulate a Bag
        bh.consume(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        // Simulate an ImmutableBag
        bh.consume(Collectors.collectingAndThen(Collectors.groupingBy(Function.identity(), Collectors.counting()), Map::copyOf));
        bh.consume(Collectors.joining());
    }

    @Benchmark
    public void collectors2(Blackhole bh)
    {
        bh.consume(Collectors2.toList());
        bh.consume(Collectors2.toImmutableList());
        bh.consume(Collectors2.toSet());
        bh.consume(Collectors2.toImmutableSet());
        bh.consume(Collectors2.toBag());
        bh.consume(Collectors2.toImmutableBag());
        bh.consume(Collectors2.makeString());
    }
}
