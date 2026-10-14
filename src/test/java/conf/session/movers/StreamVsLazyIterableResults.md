JDK Type | # Bytes | Eclipse Collections Type | # Bytes
-------- | ------ | ------- | --------
`List.of().stream()` | 80 | `Lists.immutable.empty().asLazy()` | 16
`Stream.filter()` | 144 | `LazyIterable.select()` | 24
`Stream.map()` | 144 | `LazyIterable.collect()` | 24
`Stream.limit()` | 152 | `LazyIterable.take()` | 16
`Stream.flatMap()` | 144 | `LazyIterable.flatCollect()` | 24
`Stream.mapToLong()` | 144 | `LazyIterable.collectLong()` | 64
`Stream.mapToInt()` | 144 | `LazyIterable.collectInt()` | 64
`Stream.mapToDouble()` | 144 | `LazyIterable.collectDouble()` | 64
`Stream.distinct()` | 128 | `LazyIterable.distinct()` | 16
`Stream.dropWhile()` | 144 | `LazyIterable.dropWhile()` | 24
`Stream.takeWhile()` | 144 | `LazyIterable.takeWhile()` | 24
`Stream.peek()` | 144 | `LazyIterable.tap()` | 40
`Stream.filter().map().mapToLong()` | 272 | `LazyIterable.select().collect().collectLong()` | 96
