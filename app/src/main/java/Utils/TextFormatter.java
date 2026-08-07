package Utils;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to format text with markdown-style formatting
 */
public class TextFormatter {

    private static final int BULLET_GAP_WIDTH = 20;
    private static final int BULLET_COLOR = Color.parseColor("#008b8b"); // Teal color

    /**
     * Format a response string to apply markdown styling
     * @param text The original text from API
     * @return SpannableStringBuilder with formatting applied
     */
    public static SpannableStringBuilder formatResponse(String text) {
        if (text == null || text.isEmpty()) return new SpannableStringBuilder("");

        // Create a copy of the original text
        SpannableStringBuilder ssb = new SpannableStringBuilder(text);

        try {
            // Apply formatting in a specific order
            formatListItems(ssb);
            applyHeaderFormatting(ssb);
            applyBoldFormatting(ssb);
            applyItalicFormatting(ssb);
            applyStrikethroughFormatting(ssb);
            applyUnderlineFormatting(ssb);
            formatTables(ssb);
        } catch (Exception e) {
            // If any formatting fails, return the original text
            return new SpannableStringBuilder(text);
        }

        return ssb;
    }

    /**
     * Apply header formatting for lines starting with # or ##
     */
    private static void applyHeaderFormatting(SpannableStringBuilder ssb) {
        Pattern pattern = Pattern.compile("(?m)^(#{1,3})\\s+(.+)$");
        Matcher matcher = pattern.matcher(ssb);

        List<int[]> replacements = new ArrayList<>();

        while (matcher.find()) {
            int headerLevel = matcher.group(1).length();
            int start = matcher.start();
            int end = matcher.end();

            // Store replacement details
            replacements.add(new int[]{start, end, headerLevel});
        }

        // Apply replacements in reverse order to avoid index shifting
        for (int i = replacements.size() - 1; i >= 0; i--) {
            int[] replace = replacements.get(i);
            int start = replace[0];
            int end = replace[1];
            int headerLevel = replace[2];

            // Extract header text
            String headerText = ssb.subSequence(start, end).toString().substring(headerLevel + 1);

            // Determine text size
            float textSize;
            switch (headerLevel) {
                case 1: textSize = 1.5f; break;
                case 2: textSize = 1.3f; break;
                default: textSize = 1.2f; break;
            }

            // Replace header markers
            ssb.replace(start, end, headerText);

            // Apply styling
            ssb.setSpan(new StyleSpan(Typeface.BOLD),
                    start, start + headerText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            ssb.setSpan(new RelativeSizeSpan(textSize),
                    start, start + headerText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            ssb.setSpan(new ForegroundColorSpan(BULLET_COLOR),
                    start, start + headerText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /**
     * Apply bold formatting to text between ** or __
     * Fixed to correctly handle bold markers
     */
    private static void applyBoldFormatting(SpannableStringBuilder ssb) {
        // Improved regex pattern to better handle bold text
        // This looks for ** or __ surrounding text, being careful not to include whitespace in matches
        Pattern boldPattern = Pattern.compile("(\\*\\*|__)([^\\*_]+?)(\\*\\*|__)");
        Matcher matcher = boldPattern.matcher(ssb);

        List<int[]> replacements = new ArrayList<>();

        while (matcher.find()) {
            String boldMarker = matcher.group(1);
            String boldText = matcher.group(2);
            String endMarker = matcher.group(3);

            // Ensure start and end markers match
            if ((boldMarker.equals("**") && endMarker.equals("**")) ||
                    (boldMarker.equals("__") && endMarker.equals("__"))) {
                replacements.add(new int[]{matcher.start(), matcher.end(),
                        boldMarker.length(), boldText.length()});
            }
        }

        // Apply replacements in reverse order to avoid index shifting
        for (int i = replacements.size() - 1; i >= 0; i--) {
            int[] replace = replacements.get(i);
            int start = replace[0];
            int end = replace[1];
            int markerLength = replace[2];
            int textLength = replace[3];

            try {
                // Extract the text without markers
                String textToStyle = ssb.subSequence(start + markerLength, end - markerLength).toString();

                // Replace the entire match with just the text
                ssb.replace(start, end, textToStyle);

                // Apply bold style to the text
                ssb.setSpan(new StyleSpan(Typeface.BOLD),
                        start, start + textToStyle.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (IndexOutOfBoundsException e) {
                // If replacement fails, skip this instance
                continue;
            }
        }
    }

    /**
     * Apply italic formatting to text between _ or *
     * Improved to handle single asterisks or underscores
     */
    private static void applyItalicFormatting(SpannableStringBuilder ssb) {
        // Better regex for italic text - single * or _
        // Make sure we don't match ** or __ which are for bold
        Pattern italicPattern = Pattern.compile("(?<![\\*_])(\\*|_)(?![\\*_])([^\\*_]+?)(?<![\\*_])(\\*|_)(?![\\*_])");
        Matcher matcher = italicPattern.matcher(ssb);

        List<int[]> replacements = new ArrayList<>();

        while (matcher.find()) {
            String italicMarker = matcher.group(1);
            String italicText = matcher.group(2);
            String endMarker = matcher.group(3);

            // Ensure start and end markers match
            if (italicMarker.equals(endMarker)) {
                replacements.add(new int[]{
                        matcher.start(), matcher.end(),
                        italicMarker.length(), italicText.length()
                });
            }
        }

        // Apply replacements in reverse order
        for (int i = replacements.size() - 1; i >= 0; i--) {
            int[] replace = replacements.get(i);
            int start = replace[0];
            int end = replace[1];
            int markerLength = replace[2];
            int textLength = replace[3];

            try {
                // Extract the text without markers
                String textToStyle = ssb.subSequence(start + markerLength, end - markerLength).toString();

                // Replace the entire match with just the text
                ssb.replace(start, end, textToStyle);

                // Apply italic style
                ssb.setSpan(new StyleSpan(Typeface.ITALIC),
                        start, start + textToStyle.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (IndexOutOfBoundsException e) {
                // If replacement fails, skip this instance
                continue;
            }
        }
    }

    /**
     * Apply strikethrough formatting to text between ~~ markers
     */
    private static void applyStrikethroughFormatting(SpannableStringBuilder ssb) {
        Pattern strikePattern = Pattern.compile("~~(.+?)~~");
        Matcher matcher = strikePattern.matcher(ssb);

        List<int[]> replacements = new ArrayList<>();

        while (matcher.find()) {
            String strikeText = matcher.group(1);
            replacements.add(new int[]{
                    matcher.start(), matcher.end(),
                    strikeText.length()
            });
        }

        // Apply replacements in reverse order
        for (int i = replacements.size() - 1; i >= 0; i--) {
            int[] replace = replacements.get(i);
            int start = replace[0];
            int end = replace[1];
            int textLength = replace[2];

            try {
                // Extract the text without markers
                String textToStyle = ssb.subSequence(start + 2, end - 2).toString();

                // Replace the entire match with just the text
                ssb.replace(start, end, textToStyle);

                // Apply strikethrough style
                ssb.setSpan(new StrikethroughSpan(),
                        start, start + textToStyle.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (IndexOutOfBoundsException e) {
                // If replacement fails, skip this instance
                continue;
            }
        }
    }

    /**
     * Apply underline formatting to text between __ markers
     * Added to support underline formatting
     */
    private static void applyUnderlineFormatting(SpannableStringBuilder ssb) {
        Pattern underlinePattern = Pattern.compile("__(.+?)__");
        Matcher matcher = underlinePattern.matcher(ssb);

        List<int[]> replacements = new ArrayList<>();

        while (matcher.find()) {
            String underlineText = matcher.group(1);
            replacements.add(new int[]{
                    matcher.start(), matcher.end(),
                    underlineText.length()
            });
        }

        // Apply replacements in reverse order
        for (int i = replacements.size() - 1; i >= 0; i--) {
            int[] replace = replacements.get(i);
            int start = replace[0];
            int end = replace[1];
            int textLength = replace[2];

            try {
                // Replace the entire match with just the text
                String textToStyle = ssb.subSequence(start + 2, end - 2).toString();
                ssb.replace(start, end, textToStyle);

                // Apply underline style
                ssb.setSpan(new UnderlineSpan(),
                        start, start + textToStyle.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (IndexOutOfBoundsException e) {
                // If replacement fails, skip this instance
                continue;
            }
        }
    }

    /**
     * Format markdown list items (lines starting with - or * or numbers)
     * Improved to better handle both bulleted and numbered lists
     */
    private static void formatListItems(SpannableStringBuilder ssb) {
        String[] lines = ssb.toString().split("\n");
        SpannableStringBuilder newSsb = new SpannableStringBuilder();

        boolean inList = false;
        int listItemCount = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Check for bullet list items (- or *)
            if (line.matches("^[-*]\\s+.+")) {
                // Extract content after the bullet
                String content = line.substring(2);
                inList = true;
                listItemCount++;

                // Add the content with bullet span
                int start = newSsb.length();
                newSsb.append(content);

                // Apply bullet span
                newSsb.setSpan(new BulletSpan(BULLET_GAP_WIDTH, BULLET_COLOR),
                        start, newSsb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // Check for numbered list items (1. 2. etc)
            else if (line.matches("^\\d+\\.\\s+.+")) {
                // Extract number and content
                int dotIndex = line.indexOf(".");
                String numberText = line.substring(0, dotIndex);
                String content = line.substring(dotIndex + 2); // Skip ". "
                inList = true;
                listItemCount++;

                // Add formatted numbered list item
                int start = newSsb.length();
                newSsb.append(numberText + ". " + content);

                // Style the number part
                newSsb.setSpan(new StyleSpan(Typeface.BOLD),
                        start, start + numberText.length() + 2, // Include the ". "
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // Handle regular lines
            else {
                // If we were in a list and now we're not, add extra spacing
                if (inList && listItemCount > 0) {
                    newSsb.append("\n");
                    inList = false;
                    listItemCount = 0;
                }

                // Regular line
                newSsb.append(line);
            }

            // Add newline except for the last line
            if (i < lines.length - 1) {
                newSsb.append("\n");
            }
        }

        // Replace content
        ssb.replace(0, ssb.length(), newSsb);
    }

    /**
     * Format Markdown tables to improve readability
     * Completely revised to better handle table detection and formatting
     */
    /**
     * Format Markdown tables so they  cleanly in chat bubbles.
     *  – Header row is bold & teal.
     *  – Separator row (| --- | --- |) is ignored.
     *  – Each data row is kept on a single line with “ | ” between cells.
     */
    /**
     * Transform a Markdown pipe-table into readable key-value blocks.
     *
     * ─ How it works ─
     *   • Detect the first pipe-row → this is the header (keys).
     *   • Ignore the separator row (--- | :--- |).
     *   • Each subsequent row becomes:
     *         <Key1>: <Cell1>
     *         <Key2>: <Cell2>
     *         ...
     *       blank line
     *   • Keys are bold + teal to stand out.
     *
     * The result reads well in narrow chat bubbles and requires
     * no extra libraries or layout files.
     */
    private static void formatTables(SpannableStringBuilder ssb) {
        String[] lines = ssb.toString().split("\n", -1);
        SpannableStringBuilder out = new SpannableStringBuilder();

        Pattern pipeRow = Pattern.compile("^\\s*[^\\n]*\\|[^\\n]*\\|.*$");
        Pattern sepRow  = Pattern.compile("^\\s*[:\\- ]+\\|[:\\- \\|]+$");

        String[] header = null;   // will hold the column titles

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String trimmed = raw.trim();

            boolean isPipeRow = pipeRow.matcher(trimmed).matches();

            if (isPipeRow) {
                // clean leading/ending pipes and split cells
                String clean = trimmed;
                if (clean.startsWith("|")) clean = clean.substring(1);
                if (clean.endsWith("|"))   clean = clean.substring(0, clean.length() - 1);
                String[] cells = clean.split("\\|");

                // header detection
                if (header == null) {                // first pipe-row → header
                    header = Arrays.stream(cells).map(String::trim).toArray(String[]::new);
                    continue;                        // header isn’t shown
                }

                // skip separator row (--- | ---)
                if (sepRow.matcher(trimmed).matches()) {
                    continue;
                }

                // data row → emit key-value lines
                for (int c = 0; c < cells.length; c++) {
                    String key   = c < header.length ? header[c].trim() : "Col " + (c + 1);
                    String value = cells[c].trim();

                    int start = out.length();
                    out.append(key).append(": ");

                    // style the key
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, start + key.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(BULLET_COLOR), start, start + key.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    out.append(value).append("\n");
                }
                out.append("\n");                     // blank line after each row
            } else {
                // ordinary text line
                header = null;                       // reset for next potential table
                out.append(raw).append(i < lines.length - 1 ? "\n" : "");
            }
        }

        ssb.replace(0, ssb.length(), out);
    }



    /** pad a string on the right with spaces until it reaches width */
    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }


    /**
     * Helper method to clean and format table rows
     */
    private static String cleanTableRow(String row) {
        // Remove leading/trailing pipes and normalize spaces
        String cleaned = row.trim();
        if (cleaned.startsWith("|")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.endsWith("|")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        // Split by pipe and trim each cell
        String[] cells = cleaned.split("\\|");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < cells.length; i++) {
            result.append(cells[i].trim());
            if (i < cells.length - 1) {
                result.append(" | "); // Add consistent spacing
            }
        }

        return result.toString();
    }
}