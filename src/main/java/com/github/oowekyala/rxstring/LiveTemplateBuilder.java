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
    LiveTemplateBuilder<D> append(String s);


    default LiveTemplateBuilder<D> endLine() {
        return appendLine("");
    }


    default LiveTemplateBuilder<D> appendLine(String s) {
        return append(s + "\n");
    }


    /**
     * Binds a property of the data context to be rendered with a {@link Object#toString()}.
     *
     * @param binder Extracts the observable value to render from the data context
     *
     * @return This builder
     */
    LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<?>> binder);


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
