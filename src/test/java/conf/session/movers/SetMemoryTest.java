package conf.session.movers;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.collections.impl.list.Interval;
import org.eclipse.collections.impl.list.primitive.IntInterval;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.GraphLayout;

public class SetMemoryTest
{
    @Test
    void hashSetMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> Interval.oneTo(each).into(new HashSet<>()))
                .forEach(each -> System.out.println("HashSet size: " + each.size() + " Memory (bytes): " + this.memory(each)));
    }

    @Test
    void unifiedSetMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> Interval.oneTo(each).into(new UnifiedSet<>()))
                .forEach(each -> System.out.println("UnifiedSet size: " + each.size() + " Memory (bytes): " + this.memory(each)));
    }

    @Test
    void intHashSetMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> IntInterval.oneTo(each).toSet())
                .forEach(each -> System.out.println("IntHashSet size: " + each.size() + " Memory (bytes): " + this.memory(each)));
    }

    @Test
    void hashSetOnlyMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> Interval.oneTo(each).into(new HashSet<>()))
                .forEach(each -> System.out.println("Only HashSet size: " + each.size() + " Memory (bytes): " + this.memoryMinusIntegers(each)));
    }

    @Test
    void unifiedSetOnlyMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> Interval.oneTo(each).into(new UnifiedSet<>()))
                .forEach(each -> System.out.println("Only UnifiedSet size: " + each.size() + " Memory (bytes): " + this.memoryMinusIntegers(each)));
    }

    private long memory(Object instance)
    {
        return GraphLayout.parseInstance(instance).totalSize();
    }

    private long memoryMinusIntegers(Set set)
    {
        long integerSize = GraphLayout.parseInstance(Integer.valueOf(0)).totalSize();
        return memory(set) - (set.size() * integerSize);
    }
}
