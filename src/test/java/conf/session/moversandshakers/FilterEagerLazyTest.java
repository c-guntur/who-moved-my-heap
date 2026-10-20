package conf.session.moversandshakers;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.factory.primitive.IntSets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.eclipse.collections.impl.list.Interval;
import org.eclipse.collections.impl.list.primitive.IntInterval;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FilterEagerLazyTest
{
    private static final int COUNT = 1_000;

    private final Integer test = 500;
    private final Set<Integer> EXPECTED = Interval.oneTo(test).toSet();
    private final IntSet EXPECTED_INT = IntInterval.oneTo(test).toSet();

    private final List<Integer> jdkList = List.copyOf(Interval.oneTo(COUNT));
    private final ImmutableList<Integer> ecList = Interval.oneTo(COUNT).toImmutableList();
    private final ImmutableIntList ecIntList = IntInterval.oneTo(COUNT).toList().toImmutable();

    @Test
    public void jdkFilter()
    {
        Set<Integer> lessThanOrEqualTest =
                jdkList.stream()
                        .filter(each -> each <= test)
                        .collect(Collectors.toSet());

        assertEquals(EXPECTED, lessThanOrEqualTest);
    }

    @Test
    public void ecSelect()
    {
        Set<Integer> lessThanOrEqualTest =
                ecList.select(each -> each <= test, Sets.mutable.empty());

        assertEquals(EXPECTED, lessThanOrEqualTest);
    }

    @Test
    public void ecSelectPrimitive()
    {
        IntSet lessThanOrEqualTest =
                ecIntList.select(each -> each <= test, IntSets.mutable.empty());

        assertEquals(EXPECTED_INT, lessThanOrEqualTest);
    }

    @Test
    public void ecSelectWith()
    {
        Set<Integer> lessThanOrEqualTest =
                ecList.selectWith((each, compare) -> each <= compare, this.test, Sets.mutable.empty());

        assertEquals(EXPECTED, lessThanOrEqualTest);
    }

    @Test
    public void ecLazySelect()
    {
        Set<Integer> lessThanOrEqualTest =
                ecList.asLazy().select(each -> each <= test).toSet();

        assertEquals(EXPECTED, lessThanOrEqualTest);
    }

    @Test
    public void ecLazySelectPrimitive()
    {
        IntSet lessThanOrEqualTest =
                ecIntList.asLazy().select(each -> each <= test).toSet();

        assertEquals(EXPECTED_INT, lessThanOrEqualTest);
    }
}
