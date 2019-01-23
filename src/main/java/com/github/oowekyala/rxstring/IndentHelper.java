package com.github.oowekyala.rxstring;

import static java.lang.Character.isWhitespace;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Clément Fournier
 * @since 1.0
 */
class IndentHelper {


    private static final Pattern NEWLINE_PATTERN = Pattern.compile("\\v+");


    private IndentHelper() {

    }


    public static String filterIndents(String value, String indent) {

        Matcher newlineMatcher = NEWLINE_PATTERN.matcher(value);
        StringBuilder builder = new StringBuilder(value);

        int builderShift = 0;

        while (newlineMatcher.find()) {
            int afterNL = newlineMatcher.end();
            builder.insert(afterNL + builderShift, indent);
            builderShift += indent.length();
        }

        return builder.toString();
    }


    public static List<String> preprocessLines(String s) {
        List<String> lines = new ArrayList<>();
        StringBuilder curLine = new StringBuilder();
        for (String l : s.split("\\v")) {
            if (!l.trim().isEmpty()) {
                curLine.append(" ").append(l.trim());
            } else {
                // blank line
                if (curLine.length() != 0) {
                    lines.add(curLine.toString().trim());
                    curLine = new StringBuilder();
                }
            }
        }
        if (curLine.length() != 0) {
            lines.add(curLine.toString().trim());
        }

        return lines;
    }


    static String wrapToWidth(String toWrap,
                              String indentStyle,
                              int indentLevel,
                              int width,
                              boolean preserveWords) {
        if (toWrap.length() < width) {
            return toWrap;
        }

        List<String> lines = preprocessLines(toWrap);

        StringBuilder builder = new StringBuilder();
        int builderShift = 0;

        boolean first = true;
        String lineBreak = "\n\n";
        for (String line : lines) {

            if (!first) {
                builder.append(lineBreak);
                builderShift += lineBreak.length();
            }

            first = false;
            builder.append(line);

            int offset = width;

            // accumulates the offset difference between the builder and toWrap
            builderShift += repeat(builder, builderShift, indentStyle, indentLevel);

            while (offset < line.length()) {
                while (preserveWords && offset < line.length() && !isWhitespace(line.charAt(offset))) {
                    offset++;
                }

                int cut = offset;

                while (preserveWords && offset < line.length() && isWhitespace(line.charAt(offset))) {
                    offset++;
                }

                int wsLen = offset - cut;
                if (offset >= line.length()) {
                    break; // text ends in ws
                }
                builder.insert(builderShift + cut, "\n"); // insert a newline at cut point
                int afterCut = builderShift + cut + 1;
                // if there is whitespace space after the cut, delete it
                builder.replace(afterCut, afterCut + wsLen, "");

                // insert indent right after the cut
                builderShift += repeat(builder, afterCut, indentStyle, indentLevel);

                offset = cut + wsLen + 1 + width; // next line index
            }

            builderShift = builder.length();
        }

        return builder.toString();
    }


    static int repeat(StringBuilder buffer, int fromOffset, String toRepeat, int times) {
        if (times <= 0) {
            return 0;
        }
        int insertedLen = 0;
        while (times-- > 0) {
            buffer.insert(fromOffset, toRepeat);
            insertedLen += toRepeat.length();
        }
        return insertedLen;
    }
}
