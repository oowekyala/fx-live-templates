package com.github.oowekyala.rxstring;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactfx.value.Val;

import javafx.beans.value.ObservableValue;


/**
 * A type used to render objects of type T in a template.
 *
 * TODO make item renderers config-sensitive
 * TODO add indented() item renderer
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
    public abstract ItemRenderer<T> escapeWith(Function<String, String> escapeFun);


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
     * renderer is used to display the item.
     *
     * @param renderer Base renderer
     * @param prefix   Prefix
     * @param suffix   Suffix
     * @param <T>      Type of items to render
     *
     * @return A new renderer
     */
    public static <T> ItemRenderer<T> surrounded(ItemRenderer<T> renderer, String prefix, String suffix) {
        return ItemRenderer.<T>templated(null, b -> b.append(prefix).<T>render(t -> t, renderer).append(suffix));
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
        return new MappedItemRenderer<T>(ignoreEscape, fun.andThen(Val::wrap));
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
        return new TemplatedItemRenderer<T>(parent, subTemplateBuilder);
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
        private MappedItemRenderer(boolean ignoreEscape, Function<? super T, ? extends Val<String>> myFun) {
            this(ignoreEscape, (ctx, t) -> myFun.apply(t));
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

        private final Consumer<LiveTemplateBuilder<T>> subTemplateBuilder;
        private LiveTemplate<T> subTemplate;


        TemplatedItemRenderer(LiveTemplateBuilder<?> parent, Consumer<LiveTemplateBuilder<T>> subtemplateBuilder) {
            this.subTemplateBuilder = subtemplateBuilder;

        }


        @Override
        public ItemRenderer<T> escapeWith(Function<String, String> escapeFun) {
            return this;
        }


        @Override
        public Val<String> apply(LiveTemplateBuilder<?> parent, ObservableValue<? extends T> tObs) {
            if (subTemplate == null) {
                LiveTemplateBuilder<T> childBuilder = parent instanceof LiveTemplateBuilderImpl
                                                      ? ((LiveTemplateBuilderImpl) parent).spawnChildWithSameConfig()
                                                      : LiveTemplate.newBuilder();

                subTemplateBuilder.accept(childBuilder);
                // only build the template once
                subTemplate = childBuilder.toBoundTemplate(tObs.getValue());
            }
            if (!ReactfxUtil.isConst(tObs)) {
                subTemplate.dataContextProperty().unbind();
                subTemplate.dataContextProperty().bind(tObs);

            } else {
                subTemplate.setDataContext(tObs.getValue());
            }
            return subTemplate;
        }
    }
}
