package tools.analysis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * A small bar-chart canvas in plain Java2D (no chart library), drawing the charts the analysis docs embed.
 * It mirrors the few matplotlib calls the earlier Python tools used: bars and text in data coordinates,
 * category labels on one axis and a numeric axis on the other, a bold title, a grey subtitle with the
 * takeaway, a legend under the chart and an optional footnote. Sizes are in points at 150 dpi.
 *
 * Bars are either vertical (categories along x, numbers up y, with a light grid) or horizontal
 * (categories down y, numbers along x).
 */
final class Plot
{
    static final Color WHITE = Color.WHITE;
    private static final double DPI = 150;
    private static final String FONT = "SansSerif";

    record Entry(String label, Color fill, String hatch)
    {
    }

    private record Bar(double x0, double y0, double x1, double y1, Color fill, String hatch)
    {
    }

    private record Label(double x, double y, String text, double size, Color color, boolean bold, String ha, String va,
                         boolean vertical, Color box, double offsetPt)
    {
    }

    private final boolean horizontal;
    private final int width;
    private final int axesHeight;
    private final List<Bar> bars = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private double lo;
    private double hi = 1;
    private double catLo = -0.5;
    private double catHi = 0.5;
    private double[] catPos = {};
    private String[] catNames = {};
    private double catSize = 11;
    private String numberAxisLabel = "";
    private String title = "";
    private String subtitle = "";
    private String footnote = "";
    private List<Entry> legend = List.of();
    private int legendColumns = 1;
    private double legendSize = 9.5;

    /** A canvas widthIn inches wide (at 150 dpi); the plot area is about heightIn × 0.6 inches tall. */
    Plot(double widthIn, double heightIn, boolean horizontal)
    {
        this.horizontal = horizontal;
        this.width = (int) Math.round(widthIn * DPI);
        this.axesHeight = (int) Math.max(200, Math.round(heightIn * DPI * 0.6));
    }

    static Color colour(String hex)
    {
        return Color.decode(hex);
    }

    // The numeric axis range (y for vertical bars, x for horizontal bars)
    void limits(double low, double high)
    {
        lo = low;
        hi = high;
    }

    /** A bar from a0 to a1 across the category axis, b0 to b1 along the numeric axis. */
    void bar(double a0, double a1, double b0, double b1, Color fill, String hatch)
    {
        bars.add(new Bar(a0, b0, a1, b1, fill, hatch == null ? "" : hatch));
    }

    /**
     * Text at (category position a, value b). ha/va: left|center|right, bottom|center|top, in the
     * reading direction of the canvas; offsetPt moves it away from the bar end along the numeric axis.
     */
    void text(double a, double b, String text, double size, Color color, boolean bold, String ha, String va,
            boolean vertical, Color box, double offsetPt)
    {
        labels.add(new Label(a, b, text, size, color, bold, ha, va, vertical, box, offsetPt));
    }

    void categories(double[] positions, String[] names, double size)
    {
        catPos = positions;
        catNames = names;
        catSize = size;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double p : positions)
        {
            min = Math.min(min, p);
            max = Math.max(max, p);
        }
        catLo = min - 0.6;
        catHi = max + 0.6;
    }

    void numberAxisLabel(String label)
    {
        numberAxisLabel = label;
    }

    void title(String text)
    {
        title = text;
    }

    void subtitle(String wrappedText)
    {
        subtitle = wrappedText;
    }

    void footnote(String wrappedText)
    {
        footnote = wrappedText == null ? "" : wrappedText;
    }

    void legend(List<Entry> entries, int columns, double size)
    {
        legend = entries;
        legendColumns = Math.max(1, columns);
        legendSize = size;
    }

    // ---------------------------------------------------------------- drawing

    private static double px(double pt)
    {
        return pt * DPI / 72;
    }

    private static Font font(double pt, boolean bold)
    {
        return new Font(FONT, bold ? Font.BOLD : Font.PLAIN, 1).deriveFont((float) px(pt));
    }

    void save(Path path) throws IOException
    {
        // Measure first on a scratch image, then draw on one of the right height
        Graphics2D probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        int margin = 24;
        double titleH = px(14) * 1.3;
        String[] subLines = subtitle.isEmpty() ? new String[0] : subtitle.split("\n");
        double subH = subLines.length * px(10.5) * 1.35;
        String[] footLines = footnote.isEmpty() ? new String[0] : footnote.split("\n");
        double footH = footLines.length * px(8.5) * 1.35;

        FontMetrics catMetrics = probe.getFontMetrics(font(catSize, false));
        double catLabelW = 0;
        for (String name : catNames)
        {
            for (String line : name.split("\n"))
            {
                catLabelW = Math.max(catLabelW, catMetrics.stringWidth(line));
            }
        }
        FontMetrics tickMetrics = probe.getFontMetrics(font(10, false));
        double[] ticks = ticks(lo, hi);
        double tickLabelW = 0;
        for (double t : ticks)
        {
            tickLabelW = Math.max(tickLabelW, tickMetrics.stringWidth(tickText(t, ticks)));
        }

        double left = margin + (horizontal ? catLabelW + 14 : tickLabelW + 14 + (numberAxisLabel.isEmpty() ? 0 : px(11) * 1.6));
        double right = width - margin;
        double top = margin + titleH + 8 + subH + 14;
        double bottom = top + axesHeight;
        double belowAxis = horizontal
                ? px(10) * 1.6 + (numberAxisLabel.isEmpty() ? 0 : px(11) * 1.6)
                : catNameLines() * px(catSize) * 1.3 + 8;
        double legendTop = bottom + belowAxis + 14;
        FontMetrics legendMetrics = probe.getFontMetrics(font(legendSize, false));
        int rows = (legend.size() + legendColumns - 1) / legendColumns;
        double rowH = px(legendSize) * 1.6;
        int height = (int) Math.ceil(legendTop + rows * rowH + (footLines.length > 0 ? 10 + footH : 0) + margin);
        // Widen the image (not the plot area) when a subtitle or footnote line is longer, like matplotlib's tight bbox
        double textRight = 0;
        for (String line : subLines)
        {
            textRight = Math.max(textRight, left + probe.getFontMetrics(font(10.5, false)).stringWidth(line));
        }
        for (String line : footLines)
        {
            textRight = Math.max(textRight, margin + probe.getFontMetrics(font(8.5, false)).stringWidth(line));
        }
        int imageWidth = Math.max(width, (int) Math.ceil(textRight + margin));
        probe.dispose();

        BufferedImage image = new BufferedImage(imageWidth, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(WHITE);
        g.fillRect(0, 0, imageWidth, height);

        // Title and subtitle, left aligned
        g.setColor(Color.BLACK);
        g.setFont(font(14, true));
        g.drawString(title, margin, (float) (margin + px(14)));
        g.setFont(font(10.5, false));
        g.setColor(colour("#333333"));
        for (int i = 0; i < subLines.length; i++)
        {
            g.drawString(subLines[i], (float) left, (float) (margin + titleH + 8 + px(10.5) * (1 + i * 1.35)));
        }

        Axes axes = new Axes(left, right, top, bottom);

        // Grid and numeric ticks
        g.setFont(font(10, false));
        for (double t : ticks)
        {
            String label = tickText(t, ticks);
            if (horizontal)
            {
                double x = axes.number(t);
                g.setColor(Color.BLACK);
                g.draw(new Line2D.Double(x, bottom, x, bottom + 5));
                drawText(g, label, x, bottom + 8, "center", "top", false);
            }
            else
            {
                double y = axes.number(t);
                g.setColor(new Color(0, 0, 0, 0x26));
                g.draw(new Line2D.Double(left, y, right, y));
                g.setColor(Color.BLACK);
                g.draw(new Line2D.Double(left - 5, y, left, y));
                drawText(g, label, left - 8, y, "right", "center", false);
            }
        }

        // Bars
        for (Bar bar : bars)
        {
            Rectangle2D rect = axes.rect(bar);
            g.setColor(bar.fill());
            g.fill(rect);
            hatch(g, rect, bar.hatch());
            g.setColor(WHITE);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(rect);
        }

        // Axis lines (left and bottom only)
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new Line2D.Double(left, top, left, bottom));
        g.draw(new Line2D.Double(left, bottom, right, bottom));

        // Category labels
        g.setFont(font(catSize, false));
        for (int i = 0; i < catPos.length; i++)
        {
            if (horizontal)
            {
                double y = axes.category(catPos[i]);
                g.draw(new Line2D.Double(left - 5, y, left, y));
                drawText(g, catNames[i], left - 9, y, "right", "center", false);
            }
            else
            {
                double x = axes.category(catPos[i]);
                g.draw(new Line2D.Double(x, bottom, x, bottom + 5));
                drawText(g, catNames[i], x, bottom + 8, "center", "top", false);
            }
        }

        // Number-axis label
        if (!numberAxisLabel.isEmpty())
        {
            g.setFont(font(11, false));
            if (horizontal)
            {
                drawText(g, numberAxisLabel, (left + right) / 2, bottom + px(10) * 1.6 + 6, "center", "top", false);
            }
            else
            {
                drawText(g, numberAxisLabel, margin + px(11) * 0.6, (top + bottom) / 2, "center", "center", true);
            }
        }

        // Text in data coordinates
        for (Label label : labels)
        {
            g.setFont(font(label.size(), label.bold()));
            double x;
            double y;
            if (horizontal)
            {
                x = axes.number(label.y()) + px(label.offsetPt());
                y = axes.category(label.x());
            }
            else
            {
                x = axes.category(label.x());
                y = axes.number(label.y()) - px(label.offsetPt());
            }
            if (label.box() != null)
            {
                Rectangle2D extent = textBounds(g, label.text(), x, y, label.ha(), label.va(), label.vertical());
                g.setColor(label.box());
                g.fill(new Rectangle2D.Double(extent.getX() - 3, extent.getY() - 1, extent.getWidth() + 6, extent.getHeight() + 2));
            }
            g.setColor(label.color());
            drawText(g, label.text(), x, y, label.ha(), label.va(), label.vertical());
        }

        // Legend under the chart, left aligned
        g.setFont(font(legendSize, false));
        double[] columnW = new double[legendColumns];
        for (int i = 0; i < legend.size(); i++)
        {
            int column = i / Math.max(rows, 1);
            columnW[column] = Math.max(columnW[column], 34 + legendMetrics.stringWidth(legend.get(i).label()) + 28);
        }
        for (int i = 0; i < legend.size(); i++)
        {
            int column = i / Math.max(rows, 1);
            int row = i % Math.max(rows, 1);
            double x = left;
            for (int c = 0; c < column; c++)
            {
                x += columnW[c];
            }
            double y = legendTop + row * rowH;
            Entry entry = legend.get(i);
            Rectangle2D swatch = new Rectangle2D.Double(x, y + rowH * 0.2, 26, rowH * 0.6);
            g.setColor(entry.fill());
            g.fill(swatch);
            hatch(g, swatch, entry.hatch());
            g.setColor(Color.BLACK);
            drawText(g, entry.label(), x + 34, y + rowH / 2, "left", "center", false);
        }

        // Footnote
        g.setFont(font(8.5, false));
        g.setColor(colour("#555555"));
        double footTop = legendTop + rows * rowH + 10;
        for (int i = 0; i < footLines.length; i++)
        {
            g.drawString(footLines[i], margin, (float) (footTop + px(8.5) * (1 + i * 1.35)));
        }
        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private int catNameLines()
    {
        int lines = 1;
        for (String name : catNames)
        {
            lines = Math.max(lines, name.split("\n").length);
        }
        return lines;
    }

    private final class Axes
    {
        final double left;
        final double right;
        final double top;
        final double bottom;

        Axes(double left, double right, double top, double bottom)
        {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }

        double number(double v)
        {
            double t = (v - lo) / (hi - lo);
            return horizontal ? left + t * (right - left) : bottom - t * (bottom - top);
        }

        double category(double c)
        {
            double t = (c - catLo) / (catHi - catLo);
            // horizontal bars: higher category positions are drawn nearer the top, like matplotlib
            return horizontal ? bottom - t * (bottom - top) : left + t * (right - left);
        }

        Rectangle2D rect(Bar bar)
        {
            double a0 = category(bar.x0());
            double a1 = category(bar.x1());
            double b0 = number(bar.y0());
            double b1 = number(bar.y1());
            if (horizontal)
            {
                return new Rectangle2D.Double(Math.min(b0, b1), Math.min(a0, a1), Math.abs(b1 - b0), Math.abs(a1 - a0));
            }
            return new Rectangle2D.Double(Math.min(a0, a1), Math.min(b0, b1), Math.abs(a1 - a0), Math.abs(b1 - b0));
        }
    }

    // White hatch lines over the fill: "//" diagonal, "xx" crossed, ".." dots
    private static void hatch(Graphics2D g, Rectangle2D rect, String hatch)
    {
        if (hatch.isEmpty())
        {
            return;
        }
        Shape clip = g.getClip();
        g.clip(rect);
        g.setColor(WHITE);
        g.setStroke(new BasicStroke(1.4f));
        double step = 11;
        double x0 = rect.getX();
        double y0 = rect.getY();
        double w = rect.getWidth();
        double h = rect.getHeight();
        if (hatch.contains("/") || hatch.contains("x"))
        {
            for (double d = -h; d < w + h; d += step)
            {
                g.draw(new Line2D.Double(x0 + d, y0 + h, x0 + d + h, y0));
            }
        }
        if (hatch.contains("x") || hatch.contains("\\"))
        {
            for (double d = -h; d < w + h; d += step)
            {
                g.draw(new Line2D.Double(x0 + d, y0, x0 + d + h, y0 + h));
            }
        }
        if (hatch.contains("."))
        {
            for (double y = y0 + 3; y < y0 + h; y += 8)
            {
                for (double x = x0 + 3; x < x0 + w; x += 8)
                {
                    g.fill(new Rectangle2D.Double(x, y, 2.4, 2.4));
                }
            }
        }
        g.setClip(clip);
    }

    // Draws possibly multi-line text anchored at (x, y); vertical text reads bottom to top
    private static void drawText(Graphics2D g, String text, double x, double y, String ha, String va, boolean vertical)
    {
        String[] lines = text.split("\n");
        FontMetrics metrics = g.getFontMetrics();
        double lineH = metrics.getAscent() + metrics.getDescent();
        double blockH = lineH * lines.length;
        AffineTransform saved = g.getTransform();
        if (vertical)
        {
            g.translate(x, y);
            g.rotate(-Math.PI / 2);
            // after rotating, the anchor's "bottom" (towards larger y on screen) is the text's left
            String rotatedHa = switch (va)
            {
                case "bottom" -> "left";
                case "top" -> "right";
                default -> "center";
            };
            String rotatedVa = switch (ha)
            {
                case "left" -> "top";
                case "right" -> "bottom";
                default -> "center";
            };
            drawLines(g, lines, 0, 0, rotatedHa, rotatedVa, metrics, lineH, blockH);
            g.setTransform(saved);
            return;
        }
        drawLines(g, lines, x, y, ha, va, metrics, lineH, blockH);
    }

    private static void drawLines(Graphics2D g, String[] lines, double x, double y, String ha, String va,
            FontMetrics metrics, double lineH, double blockH)
    {
        double top = switch (va)
        {
            case "top" -> y;
            case "center" -> y - blockH / 2;
            default -> y - blockH;
        };
        for (int i = 0; i < lines.length; i++)
        {
            double w = metrics.stringWidth(lines[i]);
            double lx = switch (ha)
            {
                case "left" -> x;
                case "right" -> x - w;
                default -> x - w / 2;
            };
            g.drawString(lines[i], (float) lx, (float) (top + i * lineH + metrics.getAscent()));
        }
    }

    private static Rectangle2D textBounds(Graphics2D g, String text, double x, double y, String ha, String va,
            boolean vertical)
    {
        FontMetrics metrics = g.getFontMetrics();
        String[] lines = text.split("\n");
        double w = 0;
        for (String line : lines)
        {
            w = Math.max(w, metrics.stringWidth(line));
        }
        double h = (metrics.getAscent() + metrics.getDescent()) * lines.length;
        double left = switch (ha)
        {
            case "left" -> x;
            case "right" -> x - w;
            default -> x - w / 2;
        };
        double top = switch (va)
        {
            case "top" -> y;
            case "center" -> y - h / 2;
            default -> y - h;
        };
        return vertical ? new Rectangle2D.Double(x - h / 2, y - w, h, w) : new Rectangle2D.Double(left, top, w, h);
    }

    // About six round tick values from lo to hi (1, 2, 2.5 or 5 times a power of ten)
    private static double[] ticks(double lo, double hi)
    {
        double span = hi - lo;
        if (span <= 0)
        {
            return new double[]{lo};
        }
        double raw = span / 6;
        double power = Math.pow(10, Math.floor(Math.log10(raw)));
        double step = power * 10;
        for (double m : new double[]{1, 2, 2.5, 5, 10})
        {
            if (raw <= m * power)
            {
                step = m * power;
                break;
            }
        }
        List<Double> values = new ArrayList<>();
        for (double t = Math.ceil(lo / step) * step; t <= hi + step * 1e-9; t += step)
        {
            values.add(Math.abs(t) < step * 1e-9 ? 0 : t);
        }
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++)
        {
            out[i] = values.get(i);
        }
        return out;
    }

    private static String tickText(double t, double[] ticks)
    {
        double step = ticks.length > 1 ? ticks[1] - ticks[0] : 1;
        int decimals = 0;
        while (decimals < 6 && Math.abs(step * Math.pow(10, decimals) - Math.rint(step * Math.pow(10, decimals))) > 1e-6)
        {
            decimals++;
        }
        return decimals == 0 ? Py.grouped(Math.round(t)) : Py.f(t, decimals, true);
    }
}
