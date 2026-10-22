package tools.presentation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;

/**
 * Prints each reveal.js deck in build/slides to a PDF in build/pdf, one slide per page, then joins those PDFs
 * in talk order into one PDF of the whole talk to hand out: build/<slide folder name>.pdf
 * (e.g. assets/presentation-40min/build/presentation-40min.pdf). Links between slides do not work in it.
 * Replaces decktape: Playwright drives a headless Chrome or Chromium from Java, so no Node.js or Ruby.
 *
 * Run by the slides profile in pom.xml (mvn -Pslides process-resources), or by hand with Java 25:
 *   java -cp <playwright, pdfbox and eclipse-collections jars> src/main/java/tools/presentation/SlidesToPdf.java <slides dir> <pdf dir>
 *
 * Browser: the CHROME environment variable if set, else an installed Google Chrome or Chromium
 * (macOS or Linux), else a Chromium pre-installed under PLAYWRIGHT_BROWSERS_PATH (as in the cloud
 * sandbox), else the Chromium that Playwright downloads once into its cache.
 */
public class SlidesToPdf
{
    // Deck files are <series>_<deck>.html (e.g. 03_Definitions_05.html), plus the opening pages 00_Index.html, 00_Index_01.html, ...; the per-slide pages also
    // land in build/slides when Maven converts every .adoc file, and are skipped.
    private static final Pattern DECK = Pattern.compile("\\d+_[A-Za-z]+_\\d\\d\\.html|00_Index(_\\d\\d)?\\.html");

    private static final String[] INSTALLED_CHROME = {
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Chromium.app/Contents/MacOS/Chromium",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium",
            "/usr/bin/chromium-browser"};

    public static void main(String[] args) throws IOException
    {
        Path slides = Path.of(args[0]).toAbsolutePath();
        Path pdf = Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(pdf);

        MutableList<Path> decks = Lists.mutable.empty();
        try (var files = Files.list(slides))
        {
            files.forEach(file ->
            {
                if (DECK.matcher(file.getFileName().toString()).matches())
                {
                    decks.add(file);
                }
            });
        }
        decks.sortThis();

        String chrome = findChrome();
        // With a local Chrome there is nothing to download; otherwise Playwright fetches its Chromium once.
        Playwright.CreateOptions createOptions = new Playwright.CreateOptions();
        if (chrome != null)
        {
            createOptions.setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
        }
        try (Playwright playwright = Playwright.create(createOptions))
        {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions();
            if (chrome != null)
            {
                launchOptions.setExecutablePath(Path.of(chrome));
            }
            try (Browser browser = playwright.chromium().launch(launchOptions))
            {
                for (Path deck : decks)
                {
                    print(browser, deck, pdf);
                }
            }
        }
        System.out.println("PDFs: " + decks.size() + " in " + pdf + (chrome != null ? " (browser: " + chrome + ")" : ""));

        // build/slides -> build; the slide folder's name names the PDF
        Path build = slides.getParent();
        Path talk = build.resolve(build.getParent().getFileName() + ".pdf");
        join(decks.collect(deck -> pdf.resolve(deck.getFileName().toString().replaceFirst("\\.html$", ".pdf"))), talk);
    }

    /**
     * Joins the deck PDFs, already in talk order (deck file names sort that way), into one PDF.
     */
    private static void join(MutableList<Path> deckPdfs, Path talk) throws IOException
    {
        PDFMergerUtility merger = new PDFMergerUtility();
        for (Path deckPdf : deckPdfs)
        {
            merger.addSource(deckPdf.toFile());
        }
        // PDFBox warns when it overwrites a file it may still be reading, so the last build's talk PDF goes first
        Files.deleteIfExists(talk);
        merger.setDestinationFileName(talk.toString());
        merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());
        System.out.println("Talk PDF: " + talk + " (" + deckPdfs.size() + " decks)");
    }

    private static void print(Browser browser, Path deck, Path pdfDir)
    {
        String name = deck.getFileName().toString().replaceFirst("\\.html$", ".pdf");
        Page page = browser.newPage();
        // ?print-pdf makes reveal.js lay out every slide as one page of its 16:9 size (1600x900)
        page.navigate(deck.toUri() + "?print-pdf", new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        page.waitForSelector(".reveal .pdf-page");
        int slides = page.locator(".reveal .pdf-page").count();
        page.pdf(new Page.PdfOptions()
                .setPath(pdfDir.resolve(name))
                .setPreferCSSPageSize(true)
                // reveal.js leaves an empty page after the last slide; print only the slides
                .setPageRanges("1-" + slides)
                .setPrintBackground(true));
        page.close();
        System.out.println("  " + name + ": " + slides + " slides");
    }

    private static String findChrome()
    {
        String fromEnv = System.getenv("CHROME");
        if (fromEnv != null && !fromEnv.isBlank())
        {
            return fromEnv;
        }
        for (String candidate : INSTALLED_CHROME)
        {
            if (Files.isExecutable(Path.of(candidate)))
            {
                return candidate;
            }
        }
        // A browser folder set up for Playwright in advance has a "chromium" link to its Chrome
        String browsers = System.getenv("PLAYWRIGHT_BROWSERS_PATH");
        if (browsers != null && !browsers.isBlank() && Files.isExecutable(Path.of(browsers, "chromium")))
        {
            return Path.of(browsers, "chromium").toString();
        }
        return null;
    }
}
