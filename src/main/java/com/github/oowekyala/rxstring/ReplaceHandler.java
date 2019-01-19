package com.github.oowekyala.rxstring;

import java.util.logging.Level;


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
     * Returns a new replace handler based on this one
     * but whose parameters are offset by the given constant.
     */
    default ReplaceHandler withOffset(int offset) {
        return (s, e, v) -> replace(s + offset, e + offset, v);
    }


    default ReplaceHandler unfailing(boolean bool) {
        return bool ? unfailing() : this;
    }


    /** Returns a handler that can never throw exceptions. */
    default ReplaceHandler unfailing() {
        return (start, end, value) -> {
            try {
                replace(start, end, value);
            } catch (Exception e) {
                LiveTemplate.LOGGER.log(Level.WARNING, e, () -> "An exception was thrown by an external replacement handler");
            }
        };
    }

}
