package com.github.oowekyala.rxstring;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactfx.value.Val;

import javafx.beans.value.ObservableValue;


/**
 * A type used to render objects of type T in a template. Item renderers can use
 * the local configuration of the {@link LiveTemplateBuilder} that invokes them,
 * e.g. to get the local indentation style. They can be composed to obtain new
 * renderers, see e.g. {@link #surrounded(String, String, ItemRenderer)} or
 * {@link #indented(int, ItemRenderer)}.
 *
 * @param <T> Type of values to map
 */
public abstract class ItemRenderer<T> implements BiFunction<LiveTemplateBuilder<?>, ObservableValue<? extends T>, Val<String>> {


    private ItemRenderer() {
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
    abstract ItemRenderer<T> escapeWith(Function<String, String> escapeFun);


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
        return new MappedItemRenderer<>(false, f.andThen(Val::constant));
    }


    /**
     * Returns a renderer that surrounds its item with the given prefix and suffix. The given
     * renderer is used to display the item. When the item is absent, the prefix and suffix are absent
     * too.
     *
     * @param <T>      Type of items to render
     * @param prefix   Prefix
     * @param suffix   Suffix
     * @param renderer Base renderer
     *
     * @return A new renderer
     */
    public static <T> ItemRenderer<T> surrounded(String prefix, String suffix, ItemRenderer<T> renderer) {
        return templated(b -> b.append(prefix).render(Function.identity(), renderer).append(suffix));
    }


    /**
     * Returns a renderer that prepends the {@linkplain LiveTemplateBuilder#withDefaultIndent(String) indentation}
     * currently defined by the template builder to the rendering of the given item. This can be
     * useful if the indentation should only be displayed when the bound value is not-null. Using
     * {@link LiveTemplateBuilder#appendIndent(int)} appends it as a string constant and it will
     * always be displayed.
     *
     * @param indentLevel Number of times to repeat the indentation
     * @param renderer    Base renderer
     * @param <T>         Type of item to render
     *
     * @return A new renderer
     */
    public static <T> ItemRenderer<T> indented(int indentLevel, ItemRenderer<T> renderer) {
        return templated(b -> b.appendIndent(indentLevel).render(Function.identity(), renderer));
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
        return new MappedItemRenderer<>(ignoreEscape, fun.andThen(Val::wrap));
    }


    /**
     * A value renderer that renders Ts using a nested live template. This is what {@link LiveTemplateBuilder#bindTemplatedSeq(Function, Consumer)}
     * and {@link LiveTemplateBuilder#bindTemplate(Function, Consumer)} use under the hood. This method
     * allows you to combine it with other renderers, e.g. {@link #surrounded(String, String, ItemRenderer)}.
     *
     * @param subTemplateBuilder A function side-effecting on the builder of the sub-template
     *                           to configure it
     * @param <T>                Type of values to render
     *
     * @return A value renderer for Ts
     */
    public static <T> ItemRenderer<T> templated(Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {
        return new TemplatedItemRenderer<>(subTemplateBuilder);
    }


    private static class MappedItemRenderer<T> extends ItemRenderer<T> {
        private final BiFunction<? super LiveTemplateBuilder<?>, ? super T, ? extends Val<String>> myFun;

        private final boolean myNoEscape;


        /** Most general constructor. */
        private MappedItemRenderer(boolean ignoreEscape,
                                   BiFunction<? super LiveTemplateBuilder<?>, ? super T, ? extends Val<String>> myFun) {
            this.myNoEscape = ignoreEscape;
            this.myFun = myFun;
        }


        /** Constructor for a renderer that doesn't use the builder config. */
        private MappedItemRenderer(boolean ignoreEscape, Function<? super T, ? extends Val<String>> fun) {
            this(ignoreEscape, (ctx, t) -> fun.apply(t));
        }


        @Override
        public Val<String> apply(LiveTemplateBuilder<?> liveTemplateBuilder, ObservableValue<? extends T> tObs) {
            return Val.flatMap(tObs, t -> myFun.apply(liveTemplateBuilder, t));
        }


        public ItemRenderer<T> escapeWith(Function<String, String> escapeFun) {
            return myNoEscape ? this : new MappedItemRenderer<>(true, myFun.andThen(v -> ReactfxUtil.mapPreserveConst(v, escapeFun)));
        }

    }


    private static class TemplatedItemRenderer<T> extends ItemRenderer<T> {

        private final Consumer<LiveTemplateBuilder<T>> subTemplateBuilderSpec;
        private LiveTemplateBuilder<T> subTemplateBuilder;


        TemplatedItemRenderer(Consumer<LiveTemplateBuilder<T>> subtemplateBuilder) {
            this.subTemplateBuilderSpec = subtemplateBuilder;
        }


        @Override
        public ItemRenderer<T> escapeWith(Function<String, String> escapeFun) {
            return this;
        }


        @Override
        public Val<String> apply(LiveTemplateBuilder<?> parent, ObservableValue<? extends T> tObs) {
            if (subTemplateBuilder == null) {
                LiveTemplateBuilder<T> childBuilder = parent instanceof LiveTemplateBuilderImpl
                                                      ? ((LiveTemplateBuilderImpl) parent).spawnChildWithSameConfig()
                                                      : LiveTemplate.newBuilder();

                subTemplateBuilderSpec.accept(childBuilder);
                // only build the template once
                subTemplateBuilder = childBuilder;
            }
            LiveTemplate<T> subTemplate = subTemplateBuilder.toTemplate();
            subTemplate.dataContextProperty().unbind();
            subTemplate.dataContextProperty().bind(tObs);

            return subTemplate;
        }
    }
}
