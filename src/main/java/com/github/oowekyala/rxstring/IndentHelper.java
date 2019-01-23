package com.github.oowekyala.rxstring;

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


}
