package tools.analysis;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Number formatting, text wrapping and JSON, written to give exactly the text the earlier Python tools wrote,
 * so the regenerated docs, Mermaid files and summary.json stay byte-identical:
 *   f(x, 1, true)  = Python f"{x:,.1f}"   (round half to even on the exact binary value, like Python)
 *   g(x, 3)        = Python f"{x:.3g}"
 *   repr(x)        = Python str(float) / repr(float)
 *   json(value)    = Python json.dumps(value, indent=1)
 */
final class Py
{
    private Py()
    {
    }

    // f"{x:.Nf}", with "," grouping when grouping is true
    static String f(double x, int decimals, boolean grouping)
    {
        if (Double.isNaN(x))
        {
            return "nan";
        }
        if (Double.isInfinite(x))
        {
            return x > 0 ? "inf" : "-inf";
        }
        BigDecimal value = new BigDecimal(x).setScale(decimals, RoundingMode.HALF_EVEN);
        boolean negative = value.signum() < 0 || (value.signum() == 0 && (x < 0 || isNegativeZero(x)));
        String digits = value.abs().toPlainString();
        if (grouping)
        {
            int point = digits.indexOf('.');
            String whole = point >= 0 ? digits.substring(0, point) : digits;
            String rest = point >= 0 ? digits.substring(point) : "";
            digits = group(whole) + rest;
        }
        return (negative ? "-" : "") + digits;
    }

    static String f(double x, int decimals)
    {
        return f(x, decimals, false);
    }

    // f"{n:,}" for a whole number
    static String grouped(long n)
    {
        return (n < 0 ? "-" : "") + group(Long.toString(Math.abs(n)));
    }

    private static String group(String whole)
    {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < whole.length(); i++)
        {
            if (i > 0 && (whole.length() - i) % 3 == 0)
            {
                out.append(',');
            }
            out.append(whole.charAt(i));
        }
        return out.toString();
    }

    private static boolean isNegativeZero(double x)
    {
        return Double.doubleToRawLongBits(x) == Long.MIN_VALUE;
    }

    // f"{x:.Pg}": P significant digits, trailing zeros removed, scientific outside 1e-4 <= |x| < 10^P
    static String g(double x, int precision)
    {
        if (Double.isNaN(x))
        {
            return "nan";
        }
        if (Double.isInfinite(x))
        {
            return x > 0 ? "inf" : "-inf";
        }
        if (x == 0)
        {
            return isNegativeZero(x) ? "-0" : "0";
        }
        BigDecimal rounded = new BigDecimal(x).round(new MathContext(precision, RoundingMode.HALF_EVEN));
        int exponent = rounded.precision() - rounded.scale() - 1;
        if (exponent >= -4 && exponent < precision)
        {
            return stripZeros(rounded.setScale(Math.max(precision - 1 - exponent, 0), RoundingMode.HALF_EVEN).toPlainString());
        }
        BigDecimal mantissa = rounded.movePointLeft(exponent).setScale(precision - 1, RoundingMode.HALF_EVEN);
        return stripZeros(mantissa.toPlainString()) + (exponent < 0 ? "e-" : "e+") + pad2(Math.abs(exponent));
    }

    private static String stripZeros(String number)
    {
        if (number.indexOf('.') < 0)
        {
            return number;
        }
        String out = number.replaceAll("0+$", "");
        return out.endsWith(".") ? out.substring(0, out.length() - 1) : out;
    }

    private static String pad2(int n)
    {
        return n < 10 ? "0" + n : Integer.toString(n);
    }

    // str(x) / repr(x) for a float: shortest round-trip digits, scientific below 1e-4 or from 1e16
    static String repr(double x)
    {
        if (Double.isNaN(x))
        {
            return "nan";
        }
        if (Double.isInfinite(x))
        {
            return x > 0 ? "inf" : "-inf";
        }
        if (x == 0)
        {
            return isNegativeZero(x) ? "-0.0" : "0.0";
        }
        // Double.toString gives the shortest digits that round-trip (JDK 19+), like Python
        BigDecimal shortest = new BigDecimal(Double.toString(Math.abs(x)));
        String sign = x < 0 ? "-" : "";
        String digits = shortest.unscaledValue().toString().replaceAll("0+$", "");
        digits = digits.isEmpty() ? "0" : digits;
        int exponent = shortest.precision() - shortest.scale() - 1;
        if (exponent >= -4 && exponent < 16)
        {
            String plain = shortest.stripTrailingZeros().toPlainString();
            return sign + (plain.contains(".") ? plain : plain + ".0");
        }
        String mantissa = digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        return sign + mantissa + (exponent < 0 ? "e-" : "e+") + pad2(Math.abs(exponent));
    }

    // A value printed by an f-string without a format spec: whole-number types as they are, floats as repr
    static String str(Object value)
    {
        if (value instanceof Double d)
        {
            return repr(d);
        }
        return String.valueOf(value);
    }

    // sum() of floats as Python 3.12+ does it: Neumaier compensated summation
    static double sum(double[] values)
    {
        double result = 0.0;
        double c = 0.0;
        for (double x : values)
        {
            double t = result + x;
            if (Math.abs(result) >= Math.abs(x))
            {
                c += (result - t) + x;
            }
            else
            {
                c += (x - t) + result;
            }
            result = t;
        }
        if (c != 0 && Double.isFinite(c))
        {
            result += c;
        }
        return result;
    }

    // textwrap.fill(text, width): greedy word wrap on spaces
    static String wrap(String text, int width)
    {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+"))
        {
            if (line.length() > 0 && line.length() + 1 + word.length() > width)
            {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0)
            {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0)
        {
            lines.add(line.toString());
        }
        return String.join("\n", lines);
    }

    // ---------------------------------------------------------------- JSON

    // json.dumps(value, indent=1): maps keep insertion order, non-ASCII is escaped, floats print as repr
    static String json(Object value)
    {
        StringBuilder out = new StringBuilder();
        write(out, value, 0);
        return out.toString();
    }

    private static void write(StringBuilder out, Object value, int depth)
    {
        if (value == null)
        {
            out.append("null");
        }
        else if (value instanceof String s)
        {
            quote(out, s);
        }
        else if (value instanceof Boolean b)
        {
            out.append(b ? "true" : "false");
        }
        else if (value instanceof Double d)
        {
            out.append(Double.isNaN(d) ? "NaN" : Double.isInfinite(d) ? (d > 0 ? "Infinity" : "-Infinity") : repr(d));
        }
        else if (value instanceof Number n)
        {
            out.append(n.toString());
        }
        else if (value instanceof Map<?, ?> map)
        {
            if (map.isEmpty())
            {
                out.append("{}");
                return;
            }
            out.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                out.append(first ? "\n" : ",\n").append(" ".repeat(depth + 1));
                quote(out, String.valueOf(entry.getKey()));
                out.append(": ");
                write(out, entry.getValue(), depth + 1);
                first = false;
            }
            out.append("\n").append(" ".repeat(depth)).append("}");
        }
        else if (value instanceof List<?> list)
        {
            if (list.isEmpty())
            {
                out.append("[]");
                return;
            }
            out.append("[");
            boolean first = true;
            for (Object item : list)
            {
                out.append(first ? "\n" : ",\n").append(" ".repeat(depth + 1));
                write(out, item, depth + 1);
                first = false;
            }
            out.append("\n").append(" ".repeat(depth)).append("]");
        }
        else
        {
            // json.dump(..., default=str)
            quote(out, String.valueOf(value));
        }
    }

    private static void quote(StringBuilder out, String s)
    {
        out.append('"');
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default ->
                {
                    if (c < 0x20 || c > 0x7e)
                    {
                        out.append(String.format("\\u%04x", (int) c));
                    }
                    else
                    {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    // json.load: objects become LinkedHashMap, arrays ArrayList, whole numbers Long, other numbers Double
    static Object parseJson(String text)
    {
        int[] at = {0};
        Object value = parseValue(text, at);
        skip(text, at);
        if (at[0] != text.length())
        {
            throw new IllegalArgumentException("JSON: unexpected text at " + at[0]);
        }
        return value;
    }

    private static Object parseValue(String t, int[] at)
    {
        skip(t, at);
        char c = t.charAt(at[0]);
        if (c == '{')
        {
            Map<String, Object> map = new LinkedHashMap<>();
            at[0]++;
            skip(t, at);
            if (t.charAt(at[0]) == '}')
            {
                at[0]++;
                return map;
            }
            while (true)
            {
                skip(t, at);
                String key = parseString(t, at);
                skip(t, at);
                expect(t, at, ':');
                map.put(key, parseValue(t, at));
                skip(t, at);
                if (t.charAt(at[0]) == ',')
                {
                    at[0]++;
                    continue;
                }
                expect(t, at, '}');
                return map;
            }
        }
        if (c == '[')
        {
            List<Object> list = new ArrayList<>();
            at[0]++;
            skip(t, at);
            if (t.charAt(at[0]) == ']')
            {
                at[0]++;
                return list;
            }
            while (true)
            {
                list.add(parseValue(t, at));
                skip(t, at);
                if (t.charAt(at[0]) == ',')
                {
                    at[0]++;
                    continue;
                }
                expect(t, at, ']');
                return list;
            }
        }
        if (c == '"')
        {
            return parseString(t, at);
        }
        for (String word : new String[]{"true", "false", "null", "NaN", "Infinity", "-Infinity"})
        {
            if (t.startsWith(word, at[0]))
            {
                at[0] += word.length();
                return switch (word)
                {
                    case "true" -> Boolean.TRUE;
                    case "false" -> Boolean.FALSE;
                    case "null" -> null;
                    case "NaN" -> Double.NaN;
                    case "Infinity" -> Double.POSITIVE_INFINITY;
                    default -> Double.NEGATIVE_INFINITY;
                };
            }
        }
        int start = at[0];
        while (at[0] < t.length() && "+-0123456789.eE".indexOf(t.charAt(at[0])) >= 0)
        {
            at[0]++;
        }
        String number = t.substring(start, at[0]);
        if (number.matches("-?\\d+"))
        {
            return Long.parseLong(number);
        }
        return Double.parseDouble(number);
    }

    private static String parseString(String t, int[] at)
    {
        expect(t, at, '"');
        StringBuilder out = new StringBuilder();
        while (true)
        {
            char c = t.charAt(at[0]++);
            if (c == '"')
            {
                return out.toString();
            }
            if (c != '\\')
            {
                out.append(c);
                continue;
            }
            char e = t.charAt(at[0]++);
            switch (e)
            {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' ->
                {
                    out.append((char) Integer.parseInt(t.substring(at[0], at[0] + 4), 16));
                    at[0] += 4;
                }
                default -> out.append(e);
            }
        }
    }

    private static void skip(String t, int[] at)
    {
        while (at[0] < t.length() && Character.isWhitespace(t.charAt(at[0])))
        {
            at[0]++;
        }
    }

    private static void expect(String t, int[] at, char c)
    {
        if (t.charAt(at[0]) != c)
        {
            throw new IllegalArgumentException("JSON: expected '" + c + "' at " + at[0]);
        }
        at[0]++;
    }
}
