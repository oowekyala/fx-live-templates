package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactfx.Subscription;
import org.reactfx.collection.LiveArrayList;
import org.reactfx.collection.LiveList;
import org.reactfx.value.Val;

import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;


/**
 * Builds a {@link LiveTemplate} with a fluent API.
 *
 * <p>A template can have the following structural elements:
 * <ul>
 * <li>String constants: those are independent of the data context. See
 * {@link #append(String)}, {@link #appendLine(String)}, {@link #appendIndent(int)}.</li>
 * <li>{@link #render(Function, ValueRenderer) Constant bindings}: those will be
 * extracted from the data context at the time the template is bound, but are not
 * observable values themselves, so will only be rendered once.</li>
 * <li>Observable bindings: those will be extracted from the data context
 * at the time the template is bound. Each time a property of the data context
 * changes value, the template updates its string value and calls its {@linkplain LiveTemplate#addReplaceHandler(ReplaceHandler) replace handlers}.
 * At the time of construction though, those are specified by an extraction
 * function, and a {@linkplain ValueRenderer rendering function}. Some rendering
 * functions are remarkable:
 * <ul>
 * <li>{@linkplain #bind(Function, ValueRenderer) String rendering}: the value of the property
 * is just converted to a string.</li>
 * <li>{@linkplain #bindTemplate(Function, Consumer) Subtemplate rendering}: the
 * value of the property is bound to the datacontext of a sub-template. </li>
 * <li>{@linkplain #bindSeq(Function) Sequence rendering}: if a property is an
 * {@link ObservableList}, then changes to its individual components can be rendered
 * independently. The individual components can be rendered using any rendering method.
 * </li>
 * </ul>
 * </li>
 * </ul>
 * <ul>
 *
 * Builders own a small set of configuration properties that are ignored by the templates they build,
 * but allow using shorthands during the construction process. These are
 * <ul>
 * <li>{@linkplain #withDefaultIndent(String) the default indentation}</li>
 * <li>TODO default string escape</li>
 * </ul>
 * The sub-templates specified with {@link #bindTemplate(Function, Consumer)} also inherit these properties.
 *
 * @author Clément Fournier
 * @since 1.0
 */
public interface LiveTemplateBuilder<D> {

    // builder configuration


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
     * Returns the default indentation string, specified by
     * {@link #withDefaultIndent(String)}.
     */
    String getDefaultIndent();

    // constant binding


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
    default LiveTemplateBuilder<D> appendIndent(int level) {
        return appendIndent(level, getDefaultIndent());
    }


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

    // property binding


    /**
     * Renders a value of the data context using the specified renderer. If the
     * value is not observable, then this will only be rendered once, at the time
     * of binding. Use {@link #bind(Function, ValueRenderer)} or {@link #bindTemplate(Function, Consumer)}
     * if your value is observable, {@link #bindSeq(SeqRenderer, Function)} if it's
     * an observable list.
     *
     * @param extractor Extracts the value to render from the data context
     * @param renderer  An object specifying how the value should be converted to a string
     * @param <T>       Type of value to extract
     *
     * @return This builder
     *
     * @see #bindSeq(SeqRenderer, Function)
     * @see #bind(Function, ValueRenderer)
     */
    default <T> LiveTemplateBuilder<D> render(Function<? super D, ? extends T> extractor, ValueRenderer<T> renderer) {
        return bindSeq(renderer, extractor.andThen(LiveArrayList<T>::new)::apply);
    }


    /**
     * Binds a property of the data context to be rendered with {@link Object#toString()}.
     * If the object is null, value has a null value, the empty string will be used.
     *
     * @param extractor Extracts the observable value to render from the data context
     *
     * @return This builder
     *
     * @see #bind(Function, ValueRenderer)
     */
    default <T> LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<T>> extractor) {
        return bind(extractor, ValueRenderer.asString());
    }


    /**
     * Binds a property of the data context to be rendered with the given string conversion
     * function. If the conversion function returns null, then the empty string will be used
     * instead.
     *
     * @param extractor Extracts the observable value to render from the data context
     * @param renderer  An object specifying how the value should be converted to a string
     *
     * @return This builder
     *
     * @see #bindTemplate(Function, Consumer)
     * @see #bindSeq(SeqRenderer, Function)
     * @see #render(Function, ValueRenderer)
     */
    default <T> LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<T>> extractor, ValueRenderer<T> renderer) {
        return render(extractor, renderer.lift());
    }


    /**
     * Binds a property of the data context to be presented with a sub-template.
     * The value of the property will be used as the data context of the sub template.
     * When the property has a null value, the empty string is rendered instead.
     *
     * <p>It's recommended to use a sub-template when the value's string representation
     * has many string constants that don't depend on changes in the value's state, or
     * when it depends on several independent observable values. With a sub-template, the
     * changes to the string value will be scoped down to the individual changes in the
     * properties of the sub context, which improves the resolution of the external handler calls.
     *
     * @param extractor          Extracts an observable value representing the
     *                           data context of the sub-template
     * @param subTemplateBuilder A function side-effecting on the builder of the sub-template
     *                           to configure it
     * @param <T>                Type of the data context for the sub-template
     *
     * @return This builder
     *
     * @see #bind(Function, ValueRenderer)
     */
    default <T> LiveTemplateBuilder<D> bindTemplate(Function<? super D, ? extends ObservableValue<T>> extractor,
                                                    Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {
        return bind(extractor, ValueRenderer.templated(this, subTemplateBuilder));
    }


    /**
     * Binds a property of the data context that returns an observable list of items.
     * The list will be mapped to a list of strings with the with the specified {@link SeqRenderer}.
     * Most of the time you'll want to use {@link #bindSeq(ValueRenderer, Function)}.
     *
     * <p>Changes in individual items of the list are reflected in the value of the template.
     * Only minimal changes are pushed: items are rendered incrementally (ie the whole list
     * is not rendered every time there's a change in one item). If the renderer function
     * itself produces live templates, then the minimal changes from that template will be
     * forwarded, so the changes will be even finer.
     *
     * FIXME this doesn't play well with JavaFX's native ListProperty because it fires too many events
     *
     * @param renderer  Renderer function
     * @param extractor List extractor
     * @param <T>       Type of items of the list
     *
     * @return This builder
     *
     * @see #bindSeq(ValueRenderer, Function)
     * @see #bindTemplatedSeq(Function, Consumer)
     */
    <T> LiveTemplateBuilder<D> bindSeq(SeqRenderer<? super T> renderer,
                                       Function<D, ? extends ObservableList<? extends T>> extractor);


    /**
     * Binds a property of the data context that returns an observable list of items.
     * Each item will be mapped to a string using the specified {@link ValueRenderer}.
     *
     * @param renderer  Renderer function for items
     * @param extractor List extractor
     * @param <T>       Type of items of the list
     *
     * @return This builder
     *
     * @see #bindSeq(SeqRenderer, Function)
     */
    default <T> LiveTemplateBuilder<D> bindSeq(ValueRenderer<? super T> renderer,
                                               Function<D, ? extends ObservableList<? extends T>> extractor) {
        return bindSeq(renderer.toSeq(), extractor);
    }


    /**
     * Binds a property of the data context that returns an observable list of items,
     * that are rendered with {@link ValueRenderer#templated(LiveTemplateBuilder, Consumer)}. The sub template
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
     * A type used to map observable lists to observable lists of strings.
     * This is not really meant to be used from user code for now. Instead
     * you can use a {@link ValueRenderer} and pass it to {@link #bindSeq(ValueRenderer, Function)}.
     *
     * @param <T> Type of values of the list
     */
    @FunctionalInterface
    interface SeqRenderer<T> extends Function<ObservableList<? extends T>, LiveList<Val<String>>> {
        // TODO support delimiter logic!
    }

    /**
     * A type used to render objects of type T in a template.
     * This interface provides many ready-to-use implementations.
     *
     * @param <T> Type of values to map
     */
    @FunctionalInterface
    interface ValueRenderer<T> extends Function<T, Val<String>> {

        /**
         * Returns a renderer that can render ObservableValues of T.
         */
        default ValueRenderer<ObservableValue<T>> lift() {
            // The default implementation is to use {@link Val#flatMap(Function)},
            // but the templated renderer has to preserve the fact that the end Val<String>
            // is in fact a sub template
            return tObs -> Val.flatMap(tObs, this);
        }


        /**
         * Lifts this renderer to a {@link SeqRenderer}.
         */
        default SeqRenderer<T> toSeq() {
            return seq -> LiveList.map(seq, this);
        }


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
         * @param parent             Parent builder, which copies its local configuration (like default indent,
         *                           but not its bindings) to the child
         * @param subTemplateBuilder A function side-effecting on the builder of the sub-template
         *                           to configure it
         * @param <T>                Type of values to render
         *
         * @return A value renderer for Ts
         */
        static <T> ValueRenderer<T> templated(LiveTemplateBuilder<?> parent, Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {
            LiveTemplateBuilder<T> childBuilder =
                parent == null
                ? LiveTemplate.builder()
                : ((LiveTemplateBuilderImpl) parent).spawnChildWithSameConfig();

            subTemplateBuilder.accept(childBuilder);
            // create a single builder that will spawn several templates
            // only build the template once

            // only build the template once
            LiveTemplate<T> subTemplate = childBuilder.toTemplate();

            return new ValueRenderer<T>() {
                @Override
                public Val<String> apply(T t) {
                    return childBuilder.toBoundTemplate(t);
                }


                @Override
                public ValueRenderer<ObservableValue<T>> lift() {
                    return tObs -> {
                        // Cannot use flatmap here, the Val<String> must be a subtemplate
                        // and not a FlatMappedVal
                        subTemplate.dataContextProperty().unbind();
                        subTemplate.dataContextProperty().bind(tObs);
                        return subTemplate;
                    };
                }
            };
        }
    }
}
