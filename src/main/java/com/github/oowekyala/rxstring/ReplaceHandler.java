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


}
