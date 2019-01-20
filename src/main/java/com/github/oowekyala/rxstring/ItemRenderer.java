package com.github.oowekyala.rxstring;

import java.util.function.Consumer;
import java.util.function.Function;

import org.reactfx.value.Val;

import javafx.beans.value.ObservableValue;


/**
 * A type used to render objects of type T in a template.
 *
 * @param <T> Type of values to map
 */
public class ItemRenderer<T> implements Function<T, Val<String>> {

    private final Function<? super T, ? extends Val<String>> myFun;
    private final boolean myNoEscape;


    ItemRenderer(boolean ignoreEscape, Function<? super T, ? extends Val<String>> fun) {
        myFun = fun;
        this.myNoEscape = ignoreEscape;
    }


    /**
     * Returns a renderer that can render ObservableValues of T.
     */
    ItemRenderer<ObservableValue<T>> lift() {
        // The default implementation is to use {@link Val#flatMap(Function)},
        // but the templated renderer has to preserve the fact that the end Val<String>
        // is in fact a sub template
        return new ItemRenderer<>(myNoEscape, tObs -> Val.flatMap(tObs, this));
    }


    /**
     * Applies the given escape function after this one. If this renderer has already applied
     * an escape, then returns this renderer without change. Template renderers are always
     * unchanged.
     *
     * @param escapeFun Escape function
     *
     * @return A new renderer
     */
    public ItemRenderer<T> escapeWith(Function<String, String> escapeFun) {
        return myNoEscape ? this : new ItemRenderer<>(/* ignoreEscape */true, myFun.andThen(v -> ReactfxUtil.mapPreserveConst(v, escapeFun)));
    }


    @Override
    public Val<String> apply(T t) {
        return myFun.apply(t);
    }


    @Override
    public <V> ItemRenderer<V> compose(Function<? super V, ? extends T> before) {
        return new ItemRenderer<>(myNoEscape, myFun.compose(before));
    }


    /**
     * A value renderer for anything, that maps it to string using
     * {@link Object#toString()}. When the value is null, the empty
     * string is used instead of the string "null".
     *
     * @param <T> Any reference type
     */
    public static <T> ItemRenderer<T> asString() {
        return asString(Object::toString);
    }


    /**
     * A value renderer that maps Ts to string using the provided asString.
     *
     * @param f   Mapper from T to String
     * @param <T> Type of values this renderer can handle
     */
    public static <T> ItemRenderer<T> asString(Function<? super T, String> f) {
        return new ItemRenderer<>(false, f.andThen(Val::constant));
    }


    /**
     * A value renderer that maps Ts to an observable string using the provided asString.
     * This is the most general way to create a value renderer.
     *
     * @param fun          Mapper from T to String
     * @param ignoreEscape If true, the value of this renderer won't be escaped by {@link LiveTemplateBuilder#withDefaultEscape(Function)}.
     * @param <T>          Type of values this renderer can handle
     */
    public static <T> ItemRenderer<T> mappingObservable(Function<? super T, ? extends ObservableValue<String>> fun, boolean ignoreEscape) {
        return new ItemRenderer<>(ignoreEscape, fun.andThen(Val::wrap));
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
    static <T> ItemRenderer<T> templated(LiveTemplateBuilder<?> parent, Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {
        LiveTemplateBuilder<T> childBuilder =
            parent == null
            ? LiveTemplate.newBuilder()
            : ((LiveTemplateBuilderImpl) parent).spawnChildWithSameConfig();

        subTemplateBuilder.accept(childBuilder);
        // create a single builder that will spawn several templates
        // only build the template once

        // only build the template once
        LiveTemplate<T> subTemplate = childBuilder.toTemplate();

        // ensure the template itself is never escaped in full, even after a lift call

        return new ItemRenderer<T>(true, childBuilder::toBoundTemplate) {

            @Override
            public ItemRenderer<ObservableValue<T>> lift() {
                return new ItemRenderer<>(true, tObs -> {
                    // Cannot use flatmap here, the Val<String> must be a subtemplate
                    // and not a FlatMappedVal
                    subTemplate.dataContextProperty().unbind();
                    subTemplate.dataContextProperty().bind(tObs);
                    return subTemplate;
                });
            }
        };
    }
}
