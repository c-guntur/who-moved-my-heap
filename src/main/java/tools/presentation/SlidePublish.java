package tools.presentation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Publishes the plain HTML slides: empties the output folder (default assets/presentation), then copies into it
 * only the slide pages from the work folder (target/slides/plain). The pages show the shared images from
 * ../images (assets/images), so no images are copied. Deck files (<series>_<deck>.html) and slide-template.html are converted too, but are not slides and are left out.
 * The talk PDF is added to the same folder afterwards by SlidesToPdf.java.
 *
 * Run by the slides profile in pom.xml (mvn -Pslides process-resources), or by hand with Java 25:
 *   java src/main/java/tools/presentation/SlidePublish.java <work plain folder> <output folder> [<converted slide sources folder> [<original slide sources folder>]]
 */
public class SlidePublish
{
    // One page per slide (<series>_<deck>_<slide>.html) and the opening pages (index.html, 00_Index_01.html, ...)
    private static final Pattern SLIDE = Pattern.compile("\\d+_[A-Za-z]+_\\d\\d_\\d\\d\\.html|index\\.html|00_Index_\\d\\d\\.html");

    public static void main(String[] args) throws IOException
    {
        Path work = Path.of(args[0]).toAbsolutePath().normalize();
        Path out = Path.of(args[1]).toAbsolutePath().normalize();
        Path sources = args.length > 2 ? Path.of(args[2]).toAbsolutePath().normalize() : null;
        if (out.equals(work) || work.startsWith(out))
        {
            throw new IllegalArgumentException("The output folder " + out + " must not contain the work folder " + work);
        }

        // The output folder is emptied: it must never be, or contain, the slide sources (assets/presentation-40min)
        Path original = args.length > 3 ? Path.of(args[3]).toAbsolutePath().normalize() : sources;
        if (original != null && original.startsWith(out))
        {
            throw new IllegalArgumentException("The output folder " + out + " must not be or contain the slide sources " + original);
        }

        empty(out);

        List<Path> pages = new ArrayList<>();
        try (Stream<Path> files = Files.list(work))
        {
            files.filter(file -> SLIDE.matcher(file.getFileName().toString()).matches())
                    .filter(file -> !hidden(sources, file))
                    .sorted().forEach(pages::add);
        }
        if (pages.isEmpty())
        {
            throw new IllegalStateException("No slide pages in " + work + "; run the HTML conversion first");
        }

        for (Path page : pages)
        {
            Files.copy(page, out.resolve(page.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("Published: " + pages.size() + " slide pages in " + out);
    }

    // A page is hidden when its AsciiDoc source (same name, .adoc) has a :slide-hidden: line
    private static boolean hidden(Path sources, Path page)
    {
        if (sources == null)
        {
            return false;
        }
        String name = page.getFileName().toString();
        Path source = sources.resolve(name.substring(0, name.length() - ".html".length()) + ".adoc");
        try
        {
            return Files.exists(source) && Pattern.compile("^:slide-hidden:\\s*$", Pattern.MULTILINE)
                    .matcher(Files.readString(source)).find();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    // Removes everything inside the folder (creating it if needed), so pages of renamed or removed slides do not linger
    private static void empty(Path folder) throws IOException
    {
        Files.createDirectories(folder);
        List<Path> contents;
        try (Stream<Path> walk = Files.walk(folder))
        {
            contents = walk.filter(path -> !path.equals(folder)).sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : contents)
        {
            Files.delete(path);
        }
    }
}
