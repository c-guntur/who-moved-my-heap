package conf.session.movers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.GraphLayout;

public class SweatingTheSmallStuffTest
{
    @Test
    public void fixedSizeListsToSizeSix()
    {
        ArrayList arrayList = new ArrayList();
        System.out.println("ArrayList Empty: " +
                           this.memory(arrayList));
        arrayList.add(null);
        System.out.println("ArrayList 1: " +
                           this.memory(arrayList));
        arrayList.add(null);
        System.out.println("ArrayList 2: " +
                           this.memory(arrayList));
        arrayList.add(null);
        System.out.println("ArrayList 3: " +
                           this.memory(arrayList));
        arrayList.add(null);
        System.out.println("ArrayList 4: " +
                           this.memory(arrayList));
        arrayList.add(null);
        System.out.println("ArrayList 5: " +
                           this.memory(arrayList));
        arrayList.add(null);
        System.out.println("ArrayList 6: " +
                           this.memory(arrayList));

        arrayList = new ArrayList(0);
        System.out.println("ArrayList 0: " +
                           this.memory(arrayList));
        arrayList.add(null);
        System.out.println("ArrayList0 1: " +
                           this.memory(arrayList));
        arrayList = new ArrayList(0);
        arrayList.add(null);
        arrayList.add(null);
        System.out.println("ArrayList0 2: " +
                           this.memory(arrayList));
        arrayList = new ArrayList(0);
        arrayList.add(null);
        arrayList.add(null);
        arrayList.add(null);
        System.out.println("ArrayList0 3: " +
                           this.memory(arrayList));
        arrayList = new ArrayList(0);
        arrayList.add(null);
        arrayList.add(null);
        arrayList.add(null);
        arrayList.add(null);
        System.out.println("ArrayList0 4: " +
                           this.memory(arrayList));
        arrayList = new ArrayList(0);
        arrayList.add(null);
        arrayList.add(null);
        arrayList.add(null);
        arrayList.add(null);
        arrayList.add(null);
        System.out.println("ArrayList0 5: " +
                           this.memory(arrayList));
        arrayList = new ArrayList(0);
        arrayList.add(null);
        arrayList.add(null);
        arrayList.add(null);
        arrayList.add(null);
        arrayList.add(null);
        arrayList.add(null);
        System.out.println("ArrayList0 6: " +
                           this.memory(arrayList));

        List list = Lists.fixedSize.empty();
        System.out.println("FixedSizeList Empty: " +
                           this.memory(list));
        list = Lists.fixedSize.of((Object)null);
        System.out.println("FixedSizeList 1: " +
                           this.memory(list));
        list = Lists.fixedSize.of(null, null);;
        System.out.println("FixedSizeList 2: " +
                           this.memory(list));
        list = Lists.fixedSize.of(null, null, null);
        System.out.println("FixedSizeList 3: " +
                           this.memory(list));
        list = Lists.fixedSize.of(null, null, null, null);
        System.out.println("FixedSizeList 4: " +
                           this.memory(list));
        list = Lists.fixedSize.of(null, null, null, null, null);
        System.out.println("FixedSizeList 5: " +
                           this.memory(list));
        list = Lists.fixedSize.of(null, null, null, null, null, null);
        System.out.println("FixedSizeList 6: " +
                           this.memory(list));
    }

    @Test
    public void immutableListsToSizeEleven()
    {
        Object o = new Object();
        System.out.println("List Empty: " +
                           this.memoryMinus(List.of(), o));
        System.out.println("List 1: " +
                           this.memoryMinus(List.of(o), o));
        System.out.println("List 2: " +
                           this.memoryMinus(List.of(o, o), o));
        System.out.println("List 3: " +
                           this.memoryMinus(List.of(o, o, o), o));
        System.out.println("List 4: " +
                           this.memoryMinus(List.of(o, o, o, o), o));
        System.out.println("List 5: " +
                           this.memoryMinus(List.of(o, o, o, o, o), o));
        System.out.println("List 6: " +
                           this.memoryMinus(List.of(o, o, o, o, o, o), o));
        System.out.println("List 7: " +
                           this.memoryMinus(List.of(o, o, o, o, o, o, o), o));
        System.out.println("List 8: " +
                           this.memoryMinus(List.of(o, o, o, o, o, o, o, o), o));
        System.out.println("List 9: " +
                           this.memoryMinus(List.of(o, o, o, o, o, o, o, o, o), o));
        System.out.println("List 10: " +
                           this.memoryMinus(List.of(o, o, o, o, o, o, o, o, o, o), o));
        System.out.println("List 11: " +
                           this.memoryMinus(List.of(o, o, o, o, o, o, o, o, o, o), o));

        ImmutableList list = Lists.immutable.empty();
        System.out.println("ImmutableList Empty: " + this.memory(list));
        list = Lists.immutable.of((Object)null);
        System.out.println("ImmutableList 1: " + this.memory(list));
        list = Lists.immutable.of(null, null);
        System.out.println("ImmutableList 2: " + this.memory(list));
        list = Lists.immutable.of(null, null, null);
        System.out.println("ImmutableList 3: " + this.memory(list));
        list = Lists.immutable.of(null, null, null, null);
        System.out.println("ImmutableList 4: " + this.memory(list));
        list = Lists.immutable.of(null, null, null, null, null);
        System.out.println("ImmutableList 5: " + this.memory(list));
        list = Lists.immutable.of(null, null, null, null, null, null);
        System.out.println("ImmutableList 6: " + this.memory(list));
        list = Lists.immutable.of(null, null, null, null, null, null, null);
        System.out.println("ImmutableList 7: " + this.memory(list));
        list = Lists.immutable.of(null, null, null, null, null, null, null, null);
        System.out.println("ImmutableList 8: " + this.memory(list));
        list = Lists.immutable.of(null, null, null, null, null, null, null, null, null);
        System.out.println("ImmutableList 9: " + this.memory(list));
        list = Lists.immutable.of(null, null, null, null, null, null, null, null, null, null);
        System.out.println("ImmutableList 10: " + this.memory(list));
        list = Lists.immutable.of(null, null, null, null, null, null, null, null, null, null, null);
        System.out.println("ImmutableList 11: " + this.memory(list));
    }

    private long memory(Object instance)
    {
        return GraphLayout.parseInstance(instance).totalSize();
    }

    private long memoryMinus(Object instance, Object subtract)
    {
        return memory(instance) - memory(subtract);
    }
}
