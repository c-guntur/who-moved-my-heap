package tools.dark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Makes the dark-mode slide pages. It reads the HTML pages of the slide build work folder (target/slides/plain),
 * injects dark-slides.css as the last style block of each page and writes the result to the output folder
 * (default assets/pres-dark), emptied first. Hidden slides (a :slide-hidden: line in the source) are left out.
 * The pages show the shared images from ../images, like the light pages. No PDF is made, and the slide sources
 * and the images are never changed.
 *
 * Run by Pipeline.java (stages slides and slides-dark), or by hand with Java 25 from the project root after
 * mvn -Pslides generate-resources:
 *   java src/main/java/tools/dark/DarkSlides.java [<work plain folder> [<output folder> [<converted slide sources> [<original slide sources>]]]]
 */
public class DarkSlides
{
    // One page per slide (<series>_<deck>_<slide>.html) and the opening pages (index.html, 00_Index_01.html, ...)
    private static final Pattern SLIDE = Pattern.compile("\\d+_[A-Za-z]+_\\d\\d_\\d\\d\\.html|index\\.html|00_Index_\\d\\d\\.html");

    public static void main(String[] args) throws IOException
    {
        Path in = Path.of(args.length > 0 ? args[0] : "target/slides/plain").toAbsolutePath().normalize();
        Path out = Path.of(args.length > 1 ? args[1] : "assets/pres-dark").toAbsolutePath().normalize();
        Path sources = Path.of(args.length > 2 ? args[2] : "target/slides/src").toAbsolutePath().normalize();
        Path original = Path.of(args.length > 3 ? args[3] : "assets/docs").toAbsolutePath().normalize();
        if (in.equals(out) || out.startsWith(in) || in.startsWith(out) || original.startsWith(out) || sources.startsWith(out))
        {
            throw new IllegalArgumentException("The output folder " + out + " must be separate from the work folder " + in
                    + " and the slide sources " + original);
        }
        Path css = Path.of("src/main/java/tools/dark/dark-slides.css").toAbsolutePath().normalize();
        String style = "<style id=\"dark-mode\">\n" + Files.readString(css, StandardCharsets.UTF_8) + "</style>\n";

        List<Path> pages;
        try (Stream<Path> files = Files.list(in))
        {
            pages = files.filter(file -> SLIDE.matcher(file.getFileName().toString()).matches())
                    .filter(file -> !hidden(sources, file)).sorted().toList();
        }
        if (pages.isEmpty())
        {
            throw new IllegalStateException("No slide pages in " + in + "; run mvn -Pslides generate-resources first");
        }

        empty(out);
        for (Path page : pages)
        {
            String html = Files.readString(page, StandardCharsets.UTF_8);
            int head = html.lastIndexOf("</head>");
            if (head < 0)
            {
                throw new IOException("No </head> in " + page);
            }
            Files.writeString(out.resolve(page.getFileName()), html.substring(0, head) + style + html.substring(head),
                    StandardCharsets.UTF_8);
        }
        System.out.println("Dark pages: " + pages.size() + " slide pages in " + out);
    }

    // A page is hidden when its AsciiDoc source (same name, .adoc) has a :slide-hidden: line
    private static boolean hidden(Path sources, Path page)
    {
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
