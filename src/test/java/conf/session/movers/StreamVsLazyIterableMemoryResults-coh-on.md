<!-- Run 2026-10-15 in the Claude cloud sandbox, not the local Mac. JOL retained sizes do not depend on the machine.
openjdk version "25.0.4.1" 2026-08-18
OpenJDK Runtime Environment (build 25.0.4.1+1-1-24.04.4-Ubuntu)
OpenJDK 64-Bit Server VM (build 25.0.4.1+1-1-24.04.4-Ubuntu, mixed mode, sharing)
flags: -XX:+UseCompactObjectHeaders -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading
jol: 0.17, eclipse-collections: 13.0.0
# VM mode: 64 bits
# Lilliput VM detected (experimental)
# Compressed references (oops): 3-bit shift
# Compressed class pointers: 10-bit shift and 0x18000000 base
# Object alignment: 8 bytes
#                       ref, bool, byte, char, shrt,  int,  flt,  lng,  dbl
# Field sizes:            4,    1,    1,    2,    2,    4,    4,    8,    8
# Array element sizes:    4,    1,    1,    2,    2,    4,    4,    8,    8
# Array base offsets:    12,   12,   12,   12,   12,   12,   12,   16,   16

-->

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
