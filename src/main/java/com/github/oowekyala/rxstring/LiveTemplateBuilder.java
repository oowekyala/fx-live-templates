package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactfx.Subscription;
import org.reactfx.collection.LiveArrayList;
import org.reactfx.value.Val;

import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.util.Callback;


/**
 * Builds a {@link LiveTemplate} with a fluent API.
 *
 * <p>A template can have the following structural elements:
 * <ul>
 * <li>{@link #append(String) String constants}: those are independent from the data context and will always be rendered.
 * The relevant methods are prefixed with "append".</li>
 * <li>Observable bindings: those are observable properties extracted from the data context
 * at the time the template is bound. The string representation will be updated
 * each time the property changes value. At the time of construction though, those
 * are specified by an extraction function, and a {@linkplain ItemRenderer rendering function}.
 * Some rendering functions are remarkable:
 * <ul>
 * <li>{@linkplain #bind(Function, Function) String rendering}: the value of the property
 * is just converted to a string.</li>
 * <li>{@linkplain #bindTemplate(Function, Consumer) Subtemplate rendering}: the
 * value of the property is bound to the data context of a sub-template.</li>
 * <li>{@linkplain #bindSeq(Function) Sequence rendering}: if a property is an
 * {@link ObservableList}, then changes to its individual components can be rendered
 * independently. The individual components can be rendered using any rendering method,
 * including as {@linkplain #bindTemplatedSeq(Function, Consumer) subtemplates}.
 * </li>
 * </ul>
 * Other kinds of rendering are supported through {@link ItemRenderer}s, see {@link #bind(Function, ItemRenderer)}.
 * </li>
 * <li>{@link #render(Function, ItemRenderer) Constant bindings}: those will be
 * extracted from the data context at the time the template is bound, but are not
 * observable values themselves, so will only be rendered once. They use {@link ItemRenderer}s
 * for rendering too. The relevant methods are prefixed with "render"</li>
 * </ul>
 *
 * <p>Builders own a small set of configuration properties that are ignored by the templates they build,
 * but allow using shorthands during the construction process. These are
 * <ul>
 * <li>{@linkplain #withDefaultIndent(String) the default indentation}</li>
 * <li>{@linkplain #withDefaultEscape(Function) the default escape function}</li>
 * </ul>
 * The sub-templates specified with {@link #bindTemplate(Function, Consumer)} and {@link #bindTemplatedSeq(Function, Consumer)}
 * also inherit these properties.
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
     * @throws NullPointerException if the indentStyle is null
     */
    LiveTemplateBuilder<D> withDefaultIndent(String indentStyle);


    /**
     * Returns the default indentation string, specified by
     * {@link #withDefaultIndent(String)}.
     */
    String getDefaultIndent();


    /**
     * Sets the post-processing function that will be applied as the last step of
     * all data-to-string conversions. This is useful e.g. if you're building a
     * template for an XML document, and would like to specify that all conversion
     * functions must be escaped for XML. The default is just the identity function.
     *
     * <p>Note: the escape function is not applied to whole {@linkplain #bindTemplate(Function, Consumer) sub-templates},
     * but is passed on to their builder and applied to their own {@linkplain #bind(Function, ItemRenderer) bind calls}
     * unless replaced. The escape function also doesn't apply to {@linkplain #append(String) string constants}
     * and sequence bindings that use the overload {@link #bindSeq(Function, SeqRenderer)}.
     *
     * @param stringEscapeFunction String escape function
     *
     * @return This builder
     *
     * @throws NullPointerException if the argument is null
     */
    LiveTemplateBuilder<D> withDefaultEscape(Function<String, String> stringEscapeFunction);


    /**
     * Returns the default escape function, specified by
     * {@link #withDefaultEscape(Function)}.
     */
    Function<String, String> getDefaultEscapeFunction();

    // string constants


    /**
     * Appends a string constant to the rest of the builder.
     * String constants are always rendered, and never {@link #withDefaultEscape(Function) escaped}.
     *
     * @param string String to append
     *
     * @return This builder
     *
     * @throws NullPointerException if the string is null
     * @see #appendIndent(int)
     * @see #appendLine(String)
     */
    LiveTemplateBuilder<D> append(String string);


    /**
     * Appends a single newline (\n) to the currently built template.
     *
     * @return This builder
     *
     * @see #appendLine(String)
     * @see #append(String)
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
     * @see #endLine()
     * @see #append(String)
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
     * of binding. Use {@link #bind(Function, ItemRenderer)} or {@link #bindTemplate(Function, Consumer)}
     * if your value is observable, {@link #bindSeq(Function, SeqRenderer)} if it's
     * an observable list.
     *
     * @param extractor Extracts the value to render from the data context
     * @param renderer  An object specifying how the value should be converted to a string
     * @param <T>       Type of value to extract
     *
     * @return This builder
     *
     * @see #bindSeq(Function, SeqRenderer)
     * @see #bind(Function, ItemRenderer)
     */
    default <T> LiveTemplateBuilder<D> render(Function<? super D, ? extends T> extractor, ItemRenderer<T> renderer) {
        return bind(extractor.andThen(Val::constant), renderer);
    }


    /**
     * Renders a value of the data context with a sub-template. The sub-template will
     * update automatically based on the change in properties of the extracted data
     * context. The data context itself can never change though (see {@linkplain #bindTemplate(Function, Consumer)}
     * if your data context may change).
     *
     * <p>It's recommended to use a sub-template when the value's string representation
     * has many string constants that don't depend on changes in the value's state, or
     * when it depends on several independent observable values. With a sub-template, the
     * changes to the string value will be scoped down to the individual changes in the
     * properties of the sub context, which improves the resolution of the external handler calls.
     *
     * @param extractor          Extracts the data context of the sub-template from the data context
     *                           of this template
     * @param subTemplateBuilder A function side-effecting on the builder of the sub-template
     *                           to configure it
     * @param <T>                Type of the data context for the sub-template
     *
     * @return This builder
     *
     * @see #bind(Function, ItemRenderer)
     * @see #bindTemplate(Function, Consumer)
     * @see #bindTemplatedSeq(Function, Consumer)
     */
    default <T> LiveTemplateBuilder<D> renderTemplate(Function<? super D, ? extends T> extractor,
                                                      Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {
        return render(extractor, ItemRenderer.templated(subTemplateBuilder));
    }


    /**
     * Binds a property of the data context to be rendered with {@link Object#toString()}.
     * If the property has a null value, the empty string will be used.
     *
     * @param extractor Extracts the observable value to render from the data context
     * @param <T>       Type of values to render
     *
     * @return This builder
     *
     * @see #bind(Function, ItemRenderer)
     */
    default <T> LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<T>> extractor) {
        return bind(extractor, ItemRenderer.asString());
    }


    /**
     * Binds a property of the data context to be rendered with the given string conversion
     * function. When the property has a null value, or if the conversion function returns null,
     * the empty string will be rendered instead.
     *
     * @param extractor Extracts the observable value to render from the data context
     * @param renderer  An object specifying how the value should be converted to a string
     * @param <T>       Type of values to render
     *
     * @return This builder
     *
     * @see #bindTemplate(Function, Consumer)
     * @see #bindSeq(Function, SeqRenderer)
     * @see #render(Function, ItemRenderer)
     */
    default <T> LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<? extends T>> extractor, ItemRenderer<? super T> renderer) {
        //noinspection Convert2MethodRef
        return bindSeq(extractor.andThen(obs -> new LiveArrayList<>(obs)).andThen(ReactfxUtil::flattenVals)::apply, renderer);
    }


    /**
     * Binds a property of the data context to be rendered with the given string conversion
     * function. If the conversion function returns null, then the empty string will be used
     * instead.
     *
     * @param extractor Extracts the observable value to render from the data context
     * @param renderer  An object specifying how the value should be converted to a string
     * @param <T>       Type of values to render
     *
     * @return This builder
     *
     * @see #bindTemplate(Function, Consumer)
     * @see #bindSeq(Function, SeqRenderer)
     * @see #render(Function, ItemRenderer)
     */
    default <T> LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<? extends T>> extractor, Function<? super T, String> renderer) {
        return bind(extractor, ItemRenderer.asString(renderer));
    }


    /**
     * Binds a property of the data context to be presented with a sub-template.
     * The value of the property will be used as the data context of the sub template.
     * When the property has a null value, the empty string is rendered instead.
     *
     * <p>This difference from {@link #renderTemplate(Function, Consumer)} is that the
     * data context itself may change.
     *
     * @param extractor          Extracts an observable value representing the
     *                           data context of the sub-template
     * @param subTemplateBuilder A function side-effecting on the builder of the sub-template
     *                           to configure it
     * @param <T>                Type of the data context for the sub-template
     *
     * @return This builder
     *
     * @see #renderTemplate(Function, Consumer)
     * @see #bindTemplatedSeq(Function, Consumer)
     * @see #bind(Function, ItemRenderer)
     */
    default <T> LiveTemplateBuilder<D> bindTemplate(Function<? super D, ? extends ObservableValue<? extends T>> extractor,
                                                    Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {
        return bind(extractor, ItemRenderer.templated(subTemplateBuilder));
    }


    /**
     * Binds a property of the data context that returns an observable list of items.
     * The list will be mapped to a list of strings with the with the specified {@link SeqRenderer}.
     *
     *
     * <p>Changes in individual items of the list are reflected in the value of the template.
     * Only minimal changes are pushed: items are rendered incrementally (ie the whole list
     * is not rendered every time there's a change in one item). If the renderer function
     * itself produces live templates, then the minimal changes from that template will be
     * forwarded, so the changes will be even finer.
     *
     *
     * <p>Using a seq renderer allows you the most configurability.
     *
     * <p><b>Note:</b> the {@linkplain #withDefaultEscape(Function) default escape function}
     * is not applied to the items of this list.
     *
     * <p><b>Important note:</b> don't use an observable lists that track updates to their elements
     * like {@link javafx.collections.FXCollections#observableArrayList(Callback)} does. ReactFX
     * doesn't handle these update events and considers them as additions, so the same element
     * will be treated as if it was added several times.
     *
     * @param <T>       Type of items of the list
     * @param extractor List extractor
     * @param renderer  Renderer function
     *
     * @return This builder
     *
     * @see #bindSeq(Function, ItemRenderer)
     * @see #bindTemplatedSeq(Function, Consumer)
     */
    <T> LiveTemplateBuilder<D> bindSeq(Function<D, ? extends ObservableList<? extends T>> extractor,
                                       SeqRenderer<? super T> renderer);


    /**
     * Binds a property of the data context that returns an observable list of items.
     * Each item will be mapped to a string using the specified {@link ItemRenderer}.
     * To have a delimited list, use {@link #bindSeq(Function, SeqRenderer)} with
     * {@link SeqRenderer#delimited(String, String, String, ItemRenderer)}.
     *
     * @param <T>       Type of items of the list
     * @param extractor List extractor
     * @param renderer  Renderer function for items
     *
     * @return This builder
     *
     * @see #bindSeq(Function, SeqRenderer)
     */
    default <T> LiveTemplateBuilder<D> bindSeq(Function<D, ? extends ObservableList<? extends T>> extractor,
                                               ItemRenderer<? super T> renderer) {
        return bindSeq(extractor, SeqRenderer.forItems(renderer.escapeWith(getDefaultEscapeFunction())));
    }


    /**
     * Binds a property of the data context that returns an observable list of items.
     * Each item will be mapped to a string using the specified renderer function, then
     * the {@linkplain #withDefaultEscape(Function) escape handler}.
     *
     * @param <T>       Type of items of the list
     * @param extractor List extractor
     * @param renderer  Renderer function for items
     *
     * @return This builder
     *
     * @see #bindSeq(Function, ItemRenderer)
     * @see #bindSeq(Function, SeqRenderer)
     */
    default <T> LiveTemplateBuilder<D> bindSeq(Function<D, ? extends ObservableList<? extends T>> extractor,
                                               Function<? super T, String> renderer) {
        return bindSeq(extractor, ItemRenderer.asString(renderer));
    }


    /**
     * Binds a property of the data context that returns an observable list of items,
     * that are rendered as sub-templates. The sub template builder inherits the local
     * configuration of this builder (like the {@linkplain #withDefaultIndent(String)
     * default indentation}).
     *
     * @param extractor          Value extractor
     * @param subTemplateBuilder A function side-effecting on the builder of the sub-template
     *                           to configure it
     * @param <T>                Type of items of the list
     *
     * @return This builder
     *
     * @see #bindTemplate(Function, Consumer)
     * @see #bindSeq(Function, SeqRenderer)
     */
    default <T> LiveTemplateBuilder<D> bindTemplatedSeq(Function<D, ? extends ObservableList<? extends T>> extractor,
                                                        Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {
        return bindSeq(extractor, ItemRenderer.templated(subTemplateBuilder));
    }


    /**
     * Binds an observable list, where each item is rendered with {@link Object#toString()}.
     *
     * @param extractor Value extractor
     *
     * @return This builder
     *
     * @see #bindSeq(Function, SeqRenderer)
     */
    default LiveTemplateBuilder<D> bindSeq(Function<D, ? extends ObservableList<?>> extractor) {
        return bindSeq(extractor, ItemRenderer.asString());
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


}
