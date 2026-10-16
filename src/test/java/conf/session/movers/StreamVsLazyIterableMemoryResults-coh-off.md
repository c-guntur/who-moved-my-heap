<!-- Run 2026-10-15 in the Claude cloud sandbox, not the local Mac. JOL retained sizes do not depend on the machine.
openjdk version "25.0.4.1" 2026-08-18
OpenJDK Runtime Environment (build 25.0.4.1+1-1-24.04.4-Ubuntu)
OpenJDK 64-Bit Server VM (build 25.0.4.1+1-1-24.04.4-Ubuntu, mixed mode, sharing)
flags: -XX:-UseCompactObjectHeaders -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading
jol: 0.17, eclipse-collections: 13.0.0
# VM mode: 64 bits
# Compressed references (oops): 3-bit shift
# Compressed class pointers: 0-bit shift and 0x88000000 base
# Object alignment: 8 bytes
#                       ref, bool, byte, char, shrt,  int,  flt,  lng,  dbl
# Field sizes:            4,    1,    1,    2,    2,    4,    4,    8,    8
# Array element sizes:    4,    1,    1,    2,    2,    4,    4,    8,    8
# Array base offsets:    16,   16,   16,   16,   16,   16,   16,   16,   16

-->

JDK Type | # Bytes | Eclipse Collections Type | # Bytes
-------- | ------ | ------- | --------
`List.of().stream()` | 88 | `Lists.immutable.empty().asLazy()` | 16
`Stream.filter()` | 160 | `LazyIterable.select()` | 40
`Stream.map()` | 160 | `LazyIterable.collect()` | 40
`Stream.limit()` | 168 | `LazyIterable.take()` | 24
`Stream.flatMap()` | 160 | `LazyIterable.flatCollect()` | 40
`Stream.mapToLong()` | 160 | `LazyIterable.collectLong()` | 72
`Stream.mapToInt()` | 160 | `LazyIterable.collectInt()` | 72
`Stream.mapToDouble()` | 160 | `LazyIterable.collectDouble()` | 72
`Stream.distinct()` | 144 | `LazyIterable.distinct()` | 16
`Stream.dropWhile()` | 160 | `LazyIterable.dropWhile()` | 40
`Stream.takeWhile()` | 160 | `LazyIterable.takeWhile()` | 40
`Stream.peek()` | 160 | `LazyIterable.tap()` | 56
`Stream.filter().map().mapToLong()` | 304 | `LazyIterable.select().collect().collectLong()` | 136
