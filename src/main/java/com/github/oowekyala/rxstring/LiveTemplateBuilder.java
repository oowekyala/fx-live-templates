package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactfx.Subscription;
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
     * Appends [level] times the {@linkplain #withDefaultIndent(String) default indent string}.
     *
     * @param level Indent level
     *
     * @return This builder
     *
     * @see #appendIndent(int, String)
     */
    LiveTemplateBuilder<D> appendIndent(int level);


    /**
     * Sets the default indentation string used by {@link #appendIndent(int)}.
     *
     * @param indentStyle New default indent style
     *
     * @return This builder
     *
     * @throws NullPointerException if the identStyle is null
     */
    LiveTemplateBuilder<D> withDefaultIndent(String indentStyle);


    /**
     * Appends [level] times the given [indentStyle].
     *
     * @param level       Indent level
     * @param indentStyle Indent string
     *
     * @return This builder
     *
     * @throws NullPointerException if the identStyle is null
     */
    default LiveTemplateBuilder<D> appendIndent(int level, String indentStyle) {
        while (level-- > 0) {
            append(indentStyle);
        }

        return this;
    }


    /**
     * Binds a property of the data context to be rendered with {@link Object#toString()}.
     * If the object is null, value has a null value, the empty string will be used.
     *
     * @param extractor Extracts the observable value to render from the data context
     *
     * @return This builder
     *
     * @see #bind(Function, Function)
     */
    default <T> LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<T>> extractor) {
        return bind(extractor, Object::toString);
    }


    /**
     * Binds a property of the data context to be rendered with the given string conversion
     * function. If the conversion function returns null, then the empty string will be used
     * instead.
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
     * When the property has a null value, the string is not output.
     *
     * @param extractor          Extracts an observable value representing the
     *                           data context of the sub-template
     * @param subTemplateBuilder A function side-effecting on the builder of the sub-template
     *                           to configure it
     * @param <T>                Type of the data context for the sub-template
     *
     * @return This builder
     */
    <T> LiveTemplateBuilder<D> bindTemplate(Function<? super D, ? extends ObservableValue<T>> extractor,
                                            Consumer<LiveTemplateBuilder<T>> subTemplateBuilder);


    /**
     * Binds a property of the data context that returns an observable list of items.
     * Items will be mapped to strings with the specified {@link ValueRenderer} function.
     * Changes in individual items of the list are reflected in the value of the template.
     * Only minimal changes are pushed: items are rendered incrementally (ie the whole list
     * is not rendered every time there's a change in one item. If the renderer function
     * itself is a live template, then the minimal changes from that template will be forwarded,
     * so the changes will be even finer.
     *
     * FIXME this doesn't play well with JavaFX's native ListProperty because it fires too many events
     *
     * @param renderer  Renderer function
     * @param extractor Value extractor
     * @param <T>       Type of items of the list
     *
     * @return This builder
     */
    <T> LiveTemplateBuilder<D> bindSeq(ValueRenderer<? super T> renderer,
                                       Function<D, ? extends ObservableList<? extends T>> extractor);


    /**
     * Binds a property of the data context that returns an observable list of items,
     * that are rendered with {@link ValueRenderer#templated(Consumer)}. The sub template
     * builder inherits the configuration of this builder (like the {@linkplain #withDefaultIndent(String)
     * default indentation}).
     *
     * @param extractor          Value extractor
     * @param subTemplateBuilder A function side-effecting on the builder of the sub-template
     *                           to configure it
     * @param <T>                Type of items of the list
     *
     * @return This builder
     */
    default <T> LiveTemplateBuilder<D> bindTemplatedSeq(Function<D, ? extends ObservableList<? extends T>> extractor,
                                                        Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {
        return bindSeq(ValueRenderer.templated(this, subTemplateBuilder), extractor);
    }


    /**
     * Binds an observable list of strings, rendered with {@link ValueRenderer#identity()}.
     *
     * @param extractor Value extractor
     *
     * @return This builder
     */
    default LiveTemplateBuilder<D> bindSeq(Function<D, ? extends ObservableList<String>> extractor) {
        return bindSeq(ValueRenderer.identity(), extractor);
    }


    /**
     * Builds a new live template ready for use, but with neither data context nor replace handlers.
     * This builder can still be used after that.
     *
     * @return A new live template
     *
     * @see #toBoundTemplate(Object, ReplaceHandler...)
     * @see #toTemplateSubscription(Object, ReplaceHandler, ReplaceHandler...)
     */
    LiveTemplate<D> toTemplate();


    /**
     * Builds a new live template already bound to the specified data context.
     * The handlers are also added. This builder can still be used after that.
     *
     * @param dataContext     Object on which to bind
     * @param replaceHandlers Handlers to add to the returned template
     *
     * @return A new live template
     */
    default LiveTemplate<D> toBoundTemplate(D dataContext, ReplaceHandler... replaceHandlers) {
        LiveTemplate<D> template = toTemplate();
        for (ReplaceHandler handler : replaceHandlers) {
            template.addReplaceHandler(handler);
        }
        template.setDataContext(dataContext);
        return template;
    }


    /**
     * Builds a new live template, binds it to the specified data context and handlers,
     * and returns a subscription to unbind the template from its data context. This can
     * only be useful if you plan to only use the replace handlers to affect the external
     * world, hence why you're supposed to hand-in at least one.
     *
     * @param dataContext Object on which to bind
     * @param hd          First replace handler
     * @param tl          Rest of the replace handlers
     *
     * @return A new live template
     */
    default Subscription toTemplateSubscription(D dataContext, ReplaceHandler hd, ReplaceHandler... tl) {
        List<ReplaceHandler> handlers = new ArrayList<>(tl.length + 1);
        handlers.add(Objects.requireNonNull(hd));
        handlers.addAll(Arrays.asList(tl));
        LiveTemplate<D> template = toBoundTemplate(dataContext, handlers.toArray(new ReplaceHandler[0]));
        return () -> template.setDataContext(null);
    }


    /**
     * A type used to render objects of type T in a template.
     * This interface provides many ready-to-use implementations.
     *
     * @param <T> Type of values to map
     */
    interface ValueRenderer<T> extends Function<T, ObservableValue<String>> {

        /**
         * A value renderer for anything, that maps it to string using
         * {@link Object#toString()}. When the value is null, the empty
         * string is used instead of the string "null".
         *
         * @param <T> Any reference type
         */
        static <T> ValueRenderer<T> asString() {
            return asString(Object::toString);
        }


        /**
         * A value renderer that maps Ts to string using the provided mapping.
         *
         * @param f   Mapper from T to String
         * @param <T> Type of values this renderer can handle
         */
        static <T> ValueRenderer<T> asString(Function<? super T, String> f) {
            return f.andThen(Val::constant)::apply;
        }


        /**
         * A value renderer that renders ObservableValues&lt;String&gt; to
         * their current value.
         */
        static ValueRenderer<? extends ObservableValue<String>> selfValue() {
            return obs -> Val.map(obs, Function.identity());
        }


        /**
         * A trivial value renderer for strings.
         */
        static ValueRenderer<String> identity() {
            return asString(Function.identity());
        }


        /**
         * A value renderer that renders Ts using a nested live template. The builder for the
         * nested template starts with a fresh configuration.
         *
         * @param subTemplateBuilder A function side-effecting on the builder of the sub-template
         *                           to configure it
         * @param <T>                Type of values to render
         *
         * @return A value renderer for Ts
         */
        static <T> ValueRenderer<T> templated(Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {
            return templated(null, subTemplateBuilder);
        }


        /**
         * A value renderer that renders Ts using a nested live template. This is what {@link LiveTemplateBuilder#bindTemplatedSeq(Function, Consumer)}
         * and {@link LiveTemplateBuilder#bindTemplate(Function, Consumer)} use under the hood.
         *
         * @param parent             Parent builder, which copies its configuration (like default indent,
         *                           but not its bindings) to the child
         * @param subTemplateBuilder A function side-effecting on the builder of the sub-template
         *                           to configure it
         * @param <T>                Type of values to render
         *
         * @return A value renderer for Ts
         */
        static <T> ValueRenderer<T> templated(LiveTemplateBuilder<?> parent, Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {
            LiveTemplateBuilder<T> childBuilder =
                parent == null ? LiveTemplate.builder()
                               : ((LiveTemplateBuilderImpl) parent).spawnChildWithSameConfig();

            subTemplateBuilder.accept(childBuilder);
            // create a single builder that will spawn several templates

            return t -> {
                LiveTemplate<T> template = childBuilder.toTemplate();
                template.setDataContext(t);
                return template;
            };
        }
    }
}
