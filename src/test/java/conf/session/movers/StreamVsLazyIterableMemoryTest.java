package conf.session.movers;

import java.util.List;
import java.util.stream.Gatherers;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.GraphLayout;

public class StreamVsLazyIterableMemoryTest
{
    @Test
    public void streamVsLazyIterableMemory()
    {
        List<String> list = List.of();
        ImmutableList<String> ecList = Lists.immutable.empty();
        long listSize = GraphLayout.parseInstance(list).totalSize();
        long ecListSize = GraphLayout.parseInstance(ecList).totalSize();

        System.out.println("JDK Type | # Bytes | Eclipse Collections Type | # Bytes");
        System.out.println("-------- | ------ | ------- | --------");

        var stream = list.stream();
        var asLazy = ecList.asLazy();
        System.out.printf(
                "`List.of().stream()` | %s | `Lists.immutable.empty().asLazy()` | %s %n",
                GraphLayout.parseInstance(stream).totalSize() - listSize,
                GraphLayout.parseInstance(asLazy).totalSize() - ecListSize);
        var streamFilter = list.stream().filter(i -> true);
        var lazySelect = asLazy.select(i -> true);
        System.out.printf(
                "`Stream.filter()` | %s | `LazyIterable.select()` | %s %n",
                GraphLayout.parseInstance(streamFilter).totalSize() - listSize,
                GraphLayout.parseInstance(lazySelect).totalSize() - ecListSize);
        var streamMap = list.stream().map(i -> null);
        var lazyCollect = asLazy.collect(i -> null);
        System.out.printf(
                "`Stream.map()` | %s | `LazyIterable.collect()` | %s %n",
                GraphLayout.parseInstance(streamMap).totalSize() - listSize,
                GraphLayout.parseInstance(lazyCollect).totalSize() - ecListSize);
        var streamLimit = list.stream().limit(1L);
        var lazyTake = asLazy.take(1);
        System.out.printf(
                "`Stream.limit()` | %s | `LazyIterable.take()` | %s %n",
                GraphLayout.parseInstance(streamLimit).totalSize() - listSize,
                GraphLayout.parseInstance(lazyTake).totalSize() - ecListSize);
        var streamFlatMap = list.stream().flatMap(i -> null);
        var lazyFlatCollect = asLazy.flatCollect(i -> null);
        System.out.printf(
                "`Stream.flatMap()` | %s | `LazyIterable.flatCollect()` | %s %n",
                GraphLayout.parseInstance(streamFlatMap).totalSize() - listSize,
                GraphLayout.parseInstance(lazyFlatCollect).totalSize() - ecListSize);
        var streamMapToLong = list.stream().mapToLong(i -> 1L);
        var lazyCollectLong = asLazy.collectLong(i -> 1L);
        System.out.printf(
                "`Stream.mapToLong()` | %s | `LazyIterable.collectLong()` | %s %n",
                GraphLayout.parseInstance(streamMapToLong).totalSize() - listSize,
                GraphLayout.parseInstance(lazyCollectLong).totalSize() - ecListSize);
        var streamMapToInt = list.stream().mapToInt(i -> 1);
        var lazyCollectInt = asLazy.collectInt(i -> 1);
        System.out.printf(
                "`Stream.mapToInt()` | %s | `LazyIterable.collectInt()` | %s %n",
                GraphLayout.parseInstance(streamMapToInt).totalSize() - listSize,
                GraphLayout.parseInstance(lazyCollectInt).totalSize() - ecListSize);
        var streamMapToDouble = list.stream().mapToDouble(i -> 1.0);
        var lazyCollectDouble = asLazy.collectDouble(i -> 1.0);
        System.out.printf(
                "`Stream.mapToDouble()` | %s | `LazyIterable.collectDouble()` | %s %n",
                GraphLayout.parseInstance(streamMapToDouble).totalSize() - listSize,
                GraphLayout.parseInstance(lazyCollectDouble).totalSize() - ecListSize);
        var streamDistinct = list.stream().distinct();
        var lazyDistinct = asLazy.distinct();
        System.out.printf(
                "`Stream.distinct()` | %s | `LazyIterable.distinct()` | %s %n",
                GraphLayout.parseInstance(streamDistinct).totalSize() - listSize,
                GraphLayout.parseInstance(lazyDistinct).totalSize() - ecListSize);
        var streamDropWhile = list.stream().dropWhile(i -> true);
        var lazyDropWhile = asLazy.dropWhile(i -> true);
        System.out.printf(
                "`Stream.dropWhile()` | %s | `LazyIterable.dropWhile()` | %s %n",
                GraphLayout.parseInstance(streamDropWhile).totalSize() - listSize,
                GraphLayout.parseInstance(lazyDropWhile).totalSize() - ecListSize);
        var streamTakeWhile = list.stream().takeWhile(i -> true);
        var lazyTakeWhile = asLazy.takeWhile(i -> true);
        System.out.printf(
                "`Stream.takeWhile()` | %s | `LazyIterable.takeWhile()` | %s %n",
                GraphLayout.parseInstance(streamTakeWhile).totalSize() - listSize,
                GraphLayout.parseInstance(lazyTakeWhile).totalSize() - ecListSize);
        var streamPeek = list.stream().peek(i -> {});
        var lazyTap = asLazy.tap(i -> {});
        System.out.printf(
                "`Stream.peek()` | %s | `LazyIterable.tap()` | %s %n",
                GraphLayout.parseInstance(streamPeek).totalSize() - listSize,
                GraphLayout.parseInstance(lazyTap).totalSize() - ecListSize);
        var streamFilterMapMapToLong = list.stream().filter(i -> true).map(s -> "").mapToLong(value -> 1L);
        var lazySelectCollectCollectLong = asLazy.select(i -> true).collect(each -> "").collectLong(anObject -> 1L);
        System.out.printf(
                "`Stream.filter().map().mapToLong()` | %s | `LazyIterable.select().collect().collectLong()` | %s %n",
                GraphLayout.parseInstance(streamFilterMapMapToLong).totalSize() - listSize,
                GraphLayout.parseInstance(lazySelectCollectCollectLong).totalSize() - ecListSize);
    }
}
