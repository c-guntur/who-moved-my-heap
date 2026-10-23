import org.openjdk.jol.info.ClassLayout;

// Demo: the layout of one HashMap entry. Run it twice, compact object headers off and on.
public class NodeLayout
{
    public static void main(String[] args) throws Exception
    {
        Class<?> node = Class.forName("java.util.HashMap$Node");
        System.out.println(ClassLayout.parseClass(node).instanceSize() + " bytes");
    }
}
