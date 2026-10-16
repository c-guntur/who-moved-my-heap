package conf.session.movers;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.collections.api.factory.primitive.IntIntMaps;
import org.eclipse.collections.api.factory.primitive.IntObjectMaps;
import org.eclipse.collections.api.factory.primitive.ObjectIntMaps;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.eclipse.collections.impl.list.Interval;
import org.eclipse.collections.impl.list.primitive.IntInterval;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.GraphLayout;

/**
 * Retained size (JOL) of maps holding 100,000 to 1,000,000 entries, each entry mapping i to i.
 *
 * <p>In the boxed maps the key and the value are the same {@code Integer} object, so each map holds
 * exactly one {@code Integer} per entry. The "Only" tests subtract those {@code Integer}s to leave the
 * map's own structure (table, entry objects).
 */
public class MapMemoryTest
{
    @Test
    void hashMapMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> this.fill(each, new HashMap<>()))
                .forEach(each -> System.out.println("HashMap size: " + each.size() + " Memory (bytes): " + this.memory(each)));
    }

    @Test
    void unifiedMapMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> this.fill(each, new UnifiedMap<>()))
                .forEach(each -> System.out.println("UnifiedMap size: " + each.size() + " Memory (bytes): " + this.memory(each)));
    }

    @Test
    void hashMapOnlyMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> this.fill(each, new HashMap<>()))
                .forEach(each -> System.out.println("Only HashMap size: " + each.size() + " Memory (bytes): " + this.memoryMinusIntegers(each)));
    }

    @Test
    void unifiedMapOnlyMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> this.fill(each, new UnifiedMap<>()))
                .forEach(each -> System.out.println("Only UnifiedMap size: " + each.size() + " Memory (bytes): " + this.memoryMinusIntegers(each)));
    }

    // Primitive key and value: no Integer objects at all.
    @Test
    void intIntHashMapMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> IntInterval.oneTo(each).injectInto(IntIntMaps.mutable.empty(), (MutableIntIntMap map, int i) ->
                {
                    map.put(i, i);
                    return map;
                }))
                .forEach(each -> System.out.println("IntIntHashMap size: " + each.size() + " Memory (bytes): " + this.memory(each)));
    }

    // Primitive key, boxed value: one Integer per entry (the value).
    @Test
    void intObjectHashMapMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> Interval.oneTo(each).injectInto(IntObjectMaps.mutable.<Integer>empty(), (MutableIntObjectMap<Integer> map, Integer i) ->
                {
                    map.put(i, i);
                    return map;
                }))
                .forEach(each -> System.out.println("IntObjectHashMap size: " + each.size() + " Memory (bytes): " + this.memory(each)));
    }

    // Boxed key, primitive value: one Integer per entry (the key).
    @Test
    void objectIntHashMapMemory()
    {
        Interval.fromTo(100_000, 1_000_000)
                .by(100_000)
                .collect(each -> Interval.oneTo(each).injectInto(ObjectIntMaps.mutable.<Integer>empty(), (MutableObjectIntMap<Integer> map, Integer i) ->
                {
                    map.put(i, i);
                    return map;
                }))
                .forEach(each -> System.out.println("ObjectIntHashMap size: " + each.size() + " Memory (bytes): " + this.memory(each)));
    }

    private Map<Integer, Integer> fill(int size, Map<Integer, Integer> map)
    {
        Interval.oneTo(size).each(each -> map.put(each, each));
        return map;
    }

    private long memory(Object instance)
    {
        return GraphLayout.parseInstance(instance).totalSize();
    }

    private long memoryMinusIntegers(Map<Integer, Integer> map)
    {
        long integerSize = GraphLayout.parseInstance(Integer.valueOf(0)).totalSize();
        return memory(map) - (map.size() * integerSize);
    }
}
