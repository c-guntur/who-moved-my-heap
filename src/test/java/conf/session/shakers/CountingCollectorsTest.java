package conf.session.shakers;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.eclipse.collections.api.block.function.primitive.IntToObjectFunction;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.primitive.ObjectIntMaps;
import org.eclipse.collections.impl.block.factory.HashingStrategies;
import org.eclipse.collections.impl.collector.Collectors2;
import org.eclipse.collections.impl.factory.HashingStrategySets;
import org.eclipse.collections.impl.list.primitive.IntInterval;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class CountingCollectorsTest
{
    private static final int COUNT = 1_000;
    private static final int JDK_EXPECTED_COUNT = 1_000;
    public static final int EC_EXPECTED_COUNT = 1;

    @Test
    public void countingUniqueCollectors()
    {
        var jdkCollectorsAndExpectedCounts = ObjectIntMaps.mutable
                .<IntToObjectFunction<Collector<?, ?, ?>>>empty()
                .withKeyValue(i -> Collectors.toList(), JDK_EXPECTED_COUNT)
                .withKeyValue(i -> Collectors.toSet(), JDK_EXPECTED_COUNT)
                // To simulate Bag type in JDK
                .withKeyValue(
                        i -> Collectors.groupingBy(
                                Function.identity(),
                                Collectors.counting()), JDK_EXPECTED_COUNT)
                .withKeyValue(i -> Collectors.toUnmodifiableList(), JDK_EXPECTED_COUNT)
                .withKeyValue(i -> Collectors.toUnmodifiableSet(), JDK_EXPECTED_COUNT)
                .withKeyValue(i -> Collectors.joining(), JDK_EXPECTED_COUNT)
                .toImmutable();

        var ecCollectorsAndExpectedCounts = ObjectIntMaps.mutable
                .<IntToObjectFunction<Collector<?, ?, ?>>>empty()
                .withKeyValue(i -> Collectors2.toList(), EC_EXPECTED_COUNT)
                .withKeyValue(i -> Collectors2.toSet(), EC_EXPECTED_COUNT)
                .withKeyValue(i -> Collectors2.toBag(), EC_EXPECTED_COUNT)
                .withKeyValue(i -> Collectors2.toImmutableList(), EC_EXPECTED_COUNT)
                .withKeyValue(i -> Collectors2.toImmutableSet(), EC_EXPECTED_COUNT)
                .withKeyValue(i -> Collectors2.toImmutableBag(), EC_EXPECTED_COUNT)
                .withKeyValue(i -> Collectors2.makeString(), EC_EXPECTED_COUNT)
                .toImmutable();

        var all = Lists.immutable.with(jdkCollectorsAndExpectedCounts, ecCollectorsAndExpectedCounts);

        all.forEach(map ->
                map.forEachKeyValue(
                        CountingCollectorsTest::countUniqueCollectors));
    }

    private static void countUniqueCollectors(
            IntToObjectFunction<Collector<?, ?, ?>> function,
            int expectedCount)
    {
        Set<Collector<?, ?, ?>> identitySet =
                HashingStrategySets.mutable.with(
                        HashingStrategies.identityStrategy());

        int uniqueCount = IntInterval.oneTo(COUNT)
                .collect(function, identitySet)
                .size();

        assertEquals(expectedCount, uniqueCount);
    }

    @Test
    public void equalsAndHashCodeForCollectorsToListInJava25()
    {
        var collector1 = Collectors.toList();
        var collector2 = Collectors.toList();

        assertNotSame(collector1, collector2);
        assertEquals(collector1, collector2);
        assertEquals(collector1.hashCode(), collector2.hashCode());
    }

    @Test
    public void equalsAndHashCodeForCollectors2ToListInEC()
    {
        var collector1 = Collectors2.toList();
        var collector2 = Collectors2.toList();

        assertSame(collector1, collector2);
        assertEquals(collector1, collector2);
        assertEquals(collector1.hashCode(), collector2.hashCode());
    }

    @Test
    public void collectorsToList()
    {
        List<Integer> integers = List.of(1, 2, 3, 4, 5);
        List<Integer> evens = integers.stream()
                .filter(e -> e % 2 == 0)
                .collect(Collectors.toList());

        assertEquals(List.of(2, 4), evens);
    }
}

