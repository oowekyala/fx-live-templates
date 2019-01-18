package com.github.oowekyala.rxstring;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactfx.value.Val;

import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;


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
    default <T> LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<T>> extractor) {
        return bind(extractor, Objects::toString);
    }


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
     * Binds a property of the data context that returns an observable list of items.
     * Items will be mapped to strings with the specified {@link SeqRenderer} function.
     * Changes in individual items of the list are reflected in the value of the template.
     * Only minimal changes are pushed: items are rendered incrementally (ie the whole list
     * is not rendered every time there's a change in one item. If the renderer function
     * itself is a live template, then the minimal changes from that template will be forwarded,
     * so the changes will be even finer.
     *
     * @param renderer  Renderer function
     * @param extractor Value extractor
     * @param <T>       Type of items of the list
     *
     * @return This builder
     */
    <T> LiveTemplateBuilder<D> bindSeq(SeqRenderer<? super T> renderer,
                                       Function<D, ? extends ObservableList<? extends T>> extractor);


    default LiveTemplateBuilder<D> bindSeq(Function<D, ? extends ObservableList<String>> extractor) {
        return bindSeq(SeqRenderer.identity(), extractor);
    }


    interface SeqRenderer<T> extends Function<T, ObservableValue<String>> {


        static <T> SeqRenderer<T> asString() {
            return asString(Objects::toString);
        }


        static <T> SeqRenderer<T> asString(Function<? super T, String> f) {
            return f.andThen(Val::constant)::apply;
        }


        static SeqRenderer<? extends ObservableValue<String>> selfValue() {
            return obs -> Val.map(obs, Function.identity());
        }


        static SeqRenderer<String> identity() {
            return asString(Function.identity());
        }


        static <T> SeqRenderer<T> templated(Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {
            return t -> {
                LiveTemplateBuilder<T> builder = LiveTemplate.builder();
                subTemplateBuilder.accept(builder);
                LiveTemplate<T> template = builder.toTemplate();
                template.setDataContext(t);
                return template;
            };
        }
    }

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
