package com.github.oowekyala.rxstring;

import java.util.function.Consumer;
import java.util.function.Function;

import javafx.beans.value.ObservableValue;


/**
 * Builds a {@link LiveTemplate} with a fluent API.
 *
 * @author Clément Fournier
 * @since 1.0
 */
public interface LiveTemplateBuilder<D> {


    /**
     * Appends a string constant to the rest of the builder.
     *
     * @param string String to append
     *
     * @return This builder
     *
     * @throws NullPointerException if the string is null
     */
    LiveTemplateBuilder<D> append(String string);


    /**
     * Appends a single newline (\n) to the currently built template.
     *
     * @return This builder
     */
    default LiveTemplateBuilder<D> endLine() {
        return append("\n");
    }


    /**
     * Appends the given string followed by a single newline (\n) to the currently built template.
     *
     * @param line String to append
     *
     * @return This builder
     *
     * @throws NullPointerException if the string is null
     */
    default LiveTemplateBuilder<D> appendLine(String line) {
        return append(line).endLine();
    }


    /**
     * Binds a property of the data context to be rendered with {@link Object#toString()}.
     *
     * @param extractor Extracts the observable value to render from the data context
     *
     * @return This builder
     *
     * @see #bind(Function, Function)
     */
    <T> LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<T>> extractor);


    /**
     * Binds a property of the data context to be rendered with the given string conversion
     * function.
     *
     * @param extractor Extracts the observable value to render from the data context
     * @param renderer  Converts the value to a string. Should handle null values if the
     *                  observable value may be null.
     *
     * @return This builder
     */
    <T> LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<T>> extractor, Function<? super T, String> renderer);


    /**
     * Binds a property of the data context to be presented with a sub-template.
     *
     * @param extractor          Extracts an observable value representing the
     *                           data context of the sub-template
     * @param subTemplateBuilder A builder for the sub template
     * @param <T>                Type of the data context for the sub-template
     *
     * @return This builder
     */
    <T> LiveTemplateBuilder<D> bindTemplate(Function<? super D, ? extends ObservableValue<T>> extractor,
                                            Consumer<LiveTemplateBuilder<T>> subTemplateBuilder);


    /**
     * Returns a new builder that has all the current state of this builder.
     */
    LiveTemplateBuilder<D> copy();


    /**
     * Builds a new live template ready for use. This builder can still be used
     * after that.
     *
     * @return A new live template
     */
    LiveTemplate<D> toTemplate();
}
