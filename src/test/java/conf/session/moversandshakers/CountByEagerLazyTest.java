package conf.session.moversandshakers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import conf.session.JolMemoryTestUtil;
import org.eclipse.collections.api.bag.Bag;
import org.eclipse.collections.api.bag.ImmutableBag;
import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.factory.Bags;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.list.Interval;
import org.eclipse.collections.impl.list.primitive.IntInterval;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CountByEagerLazyTest
{
    private static final int COUNT = 1_000;

    Integer test = 100;

    private final List<Integer> jdkList = List.copyOf(Interval.oneTo(COUNT));
    private final ImmutableList<Integer> ecList = Interval.oneTo(COUNT).toImmutableList();

    @Test
    public void jdkCountByImmutable()
    {
        Map<Integer, Long> counts = Map.copyOf(jdkList.stream()
                .collect(Collectors.groupingBy(each -> each % test, Collectors.counting())));
        IntInterval.zeroTo(99).forEach(i -> assertEquals(Long.valueOf(10L), counts.get(0)));
        JolMemoryTestUtil.assertGraphMemoryEquals(3272L, 3256L, counts);
    }

    @Test
    public void ecCountByImmutable()
    {
        ImmutableBag<Integer> counts = ecList.countBy(each -> each % test);
        IntInterval.zeroTo(99).forEach(i -> assertEquals(10, counts.occurrencesOf(i)));
        JolMemoryTestUtil.assertGraphMemoryEquals(3752, 3736, counts);
    }

    @Test
    public void ecCountByWithImmutable()
    {
        ImmutableBag<Integer> counts = ecList.countByWith((each, compare) -> each % compare, test);
        IntInterval.zeroTo(99).forEach(i -> assertEquals(10, counts.occurrencesOf(i)));
        System.out.println(counts.getClass());
        JolMemoryTestUtil.assertGraphMemoryEquals(3752, 3736, counts);
    }

    @Test
    public void ecLazyCountByImmutable()
    {
        Bag<Integer> counts = ecList.asLazy().countBy(each -> each % test).toImmutable();
        IntInterval.zeroTo(99).forEach(i -> assertEquals(10, counts.occurrencesOf(i)));
        JolMemoryTestUtil.assertGraphMemoryEquals(3752, 3736, counts);
    }

    @Test
    public void jdkCountByMutable()
    {
        Map<Integer, Long> counts = jdkList.stream()
                .collect(Collectors.groupingBy(each -> each % test, Collectors.counting()));
        IntInterval.zeroTo(99).forEach(i -> assertEquals(Long.valueOf(10L), counts.get(0)));
        JolMemoryTestUtil.assertGraphMemoryEquals(5912, 5096L, counts);
    }

    @Test
    public void ecCountByMutable()
    {
        MutableBag<Integer> counts = ecList.countBy(each -> each % test, Bags.mutable.empty());
        IntInterval.zeroTo(99).forEach(i -> assertEquals(10, counts.occurrencesOf(i)));
        JolMemoryTestUtil.assertGraphMemoryEquals(3736, 3720, counts);
    }

    @Test
    public void ecCountByWithMutable()
    {
        MutableBag<Integer> counts = ecList.countByWith((each, compare) -> each % compare, test, Bags.mutable.empty());
        IntInterval.zeroTo(99).forEach(i -> assertEquals(10, counts.occurrencesOf(i)));
        System.out.println(counts.getClass());
        JolMemoryTestUtil.assertGraphMemoryEquals(3736, 3720, counts);
    }

    @Test
    public void ecLazyCountByMutable()
    {
        Bag<Integer> counts = ecList.asLazy().countBy(each -> each % test);
        IntInterval.zeroTo(99).forEach(i -> assertEquals(10, counts.occurrencesOf(i)));
        JolMemoryTestUtil.assertGraphMemoryEquals(3736, 3720, counts);
    }
}
