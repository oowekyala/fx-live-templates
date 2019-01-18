package com.github.oowekyala.rxstring;

/**
 * Callback handling text replacement events.
 * Only replace will be called by this framework.
 *
 * @author Clément Fournier
 * @since 1.0
 */
@FunctionalInterface
public interface ReplaceHandler {


    /**
     * Replaces some text in the external model.
     *
     * @param start Start of the replaced range, inclusive
     * @param end   End of the replaced range, exclusive
     * @param value Replacement value
     */
    void replace(int start, int end, String value);


    /**
     * Inserts a string at the given position.
     *
     * @param start The offset of insertion, inclusive
     * @param value The string to insert
     */
    default void insert(int start, String value) {
        replace(start, start, value);
    }


    /**
     * Deletes the text at a specified range.
     *
     * @param start Start of the deleted range, inclusive
     * @param end   End of the deleted range, exclusive
     */
    default void delete(int start, int end) {
        replace(start, end, "");
    }

}
