package conf.session.moversandshakers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.multimap.set.MutableSetMultimap;
import org.eclipse.collections.impl.factory.Multimaps;
import org.eclipse.collections.impl.list.Interval;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GroupByEagerLazyTest
{
    private static final int COUNT = 1_000;

    private final Integer test = 100;

    private final List<Integer> jdkList = List.copyOf(Interval.oneTo(COUNT));
    private final ImmutableList<Integer> ecList = Interval.oneTo(COUNT).toImmutableList();

    @Test
    public void jdkGroupBy()
    {
        Map<Integer, Set<Integer>> grouped = jdkList.stream()
                .collect(Collectors.groupingBy(each -> each % test, Collectors.toSet()));

        assertEquals(100, grouped.size());
    }

    @Test
    public void ecGroupBy()
    {
        MutableSetMultimap<Integer, Integer> grouped =
                ecList.groupBy(each -> each % test, Multimaps.mutable.set.empty());

        assertEquals(100, grouped.sizeDistinct());
    }

    @Test
    public void ecLazyGroupBy()
    {
        MutableSetMultimap<Integer, Integer> grouped =
                ecList.asLazy().groupBy(each -> each % test, Multimaps.mutable.set.empty());

        assertEquals(100, grouped.sizeDistinct());
    }
}
