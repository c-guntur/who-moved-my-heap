package conf.session.movers;

import java.util.ArrayList;

import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.Interval;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.primitive.IntInterval;
import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.GraphLayout;

public class ListMemoryTest
{
    @Test
    void arrayListMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> Interval.oneTo(each).into(new ArrayList<>()))
                .forEach(each -> System.out.println("ArrayList size: " + each.size() + " Memory (bytes): " + this.memory(each)));
    }

    @Test
    void presizedArrayListMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> Interval.oneTo(each).into(new ArrayList<>(each)))
                .forEach(each -> System.out.println("Presized ArrayList size: " + each.size() + " Memory (bytes): " + this.memory(each)));
    }

    @Test
    void fastListMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> Interval.oneTo(each).into(new FastList<>()))
                .forEach(each -> System.out.println("FastList size: " + each.size() + " Memory (bytes): " + this.memory(each)));
    }

    @Test
    void presizedFastListMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> Interval.oneTo(each).into(new FastList<>(each)))
                .forEach(each -> System.out.println("Presized FastList size: " + each.size() + " Memory (bytes): " + this.memory(each)));
    }

    @Test
    void intArrayListMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> IntInterval.oneTo(each).injectInto(IntLists.mutable.empty(), MutableIntList::with))
                .forEach(each -> System.out.println("IntArrayList size: " + each.size() + " Memory (bytes): " + this.memory(each)));
    }

    @Test
    void presizedIntArrayListMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> IntInterval.oneTo(each).toList())
                .forEach(each -> System.out.println("PresizedIntArrayList size: " + each.size() + " Memory (bytes): " + this.memory(each)));
    }

    private long memory(Object instance)
    {
        return GraphLayout.parseInstance(instance).totalSize();
    }
}
