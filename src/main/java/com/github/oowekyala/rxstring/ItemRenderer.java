package com.github.oowekyala.rxstring;

import static java.lang.Character.isWhitespace;

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
        return MappedItemRenderer.mapping(false, f.andThen(Val::constant));
    }


    /**
     * A value renderer that maps Ts to an observable string using the provided asString.
     * This is the most general way to create a value renderer.
     *
     * @param <T>          Type of values this renderer can handle
     * @param ignoreEscape If true, the value of this renderer won't be escaped by {@link LiveTemplateBuilder#withDefaultEscape(Function)}.
     * @param fun          Mapper from T to String
     */
    public static <T> ItemRenderer<T> mappingObservable(boolean ignoreEscape, Function<? super T, ? extends ObservableValue<String>> fun) {
        return MappedItemRenderer.mapping(ignoreEscape, fun.andThen(Val::wrap));
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
        return templatedWrapper(renderer, b -> b.append(prefix), b -> b.append(suffix));
    }


    /**
     * Returns a renderer that prepends the {@linkplain LiveTemplateBuilder#withDefaultIndent(String) indentation}
     * currently defined by the template builder to the rendering of the given item. This can be
     * useful if the indentation should only be displayed when the bound value is not-null. Using
     * {@link LiveTemplateBuilder#appendIndent(int)} appends it as a string constant and it will
     * always be displayed. The indent is prepended only once, so if the renderer produces a multiline
     * output the next lines will not be indented. FIXME
     *
     * @param indentLevel Number of times to repeat the indentation
     * @param renderer    Base renderer
     * @param <T>         Type of item to render
     *
     * @return A new renderer
     */
    public static <T> ItemRenderer<T> indented(int indentLevel, ItemRenderer<T> renderer) {
        return templatedWrapper(renderer, b -> b.appendIndent(indentLevel), b -> {});
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


    /**
     * A value renderer that wraps the output of the given renderer to the specified text width.
     * Doing this over a template will break the minimal change calculation. This includes the
     * {@link #surrounded(String, String, ItemRenderer)} and {@link #indented(int, ItemRenderer)}
     * renderers because they're implemented as light subtemplates.
     *
     * @param wrapWidth     Max width of the text
     * @param preserveWords Whether to avoid cutting through words
     * @param wrapped       Renderer that's supposed to wrap the text
     * @param <T>           Type of values to render
     *
     * @return A value renderer for Ts
     */
    public static <T> ItemRenderer<T> wrapped(int wrapWidth, boolean preserveWords, ItemRenderer<T> wrapped) {
        return wrapped(wrapWidth, 0, preserveWords, wrapped);
    }


    /**
     * A value renderer that wraps the output of the given renderer to the specified text width.
     * Doing this over a template will break the minimal change calculation. This includes the
     * {@link #surrounded(String, String, ItemRenderer)} and {@link #indented(int, ItemRenderer)}
     * renderers because they're implemented as light subtemplates.
     *
     * @param wrapWidth     Max width of the text
     * @param indentLevel   Number of times to insert the builder's local default indentation style
     *                      at the beginning of each line
     * @param preserveWords Whether to avoid cutting through words
     * @param wrapped       Renderer that's supposed to wrap the text
     * @param <T>           Type of values to render
     *
     * @return A value renderer for Ts
     */
    public static <T> ItemRenderer<T> wrapped(int wrapWidth, int indentLevel, boolean preserveWords, ItemRenderer<T> wrapped) {
        return new MappedItemRenderer<>(false, (ctx, t) -> Val.map(wrapped.apply(ctx, t), s -> wrapToWidth(s, ctx.getDefaultIndent(), indentLevel, wrapWidth, preserveWords)));
    }


    private static String wrapToWidth(String toWrap,
                                      String indentStyle,
                                      int indentLevel,
                                      int width,
                                      boolean preserveWords) {
        if (toWrap.length() < width) {
            return toWrap;
        }

        StringBuilder builder = new StringBuilder(toWrap);
        int offset = width;
        // accumulates the offset difference between the builder and toWrap
        int builderShift = repeat(builder, 0, indentStyle, indentLevel);
        while (offset < toWrap.length()) {
            while (preserveWords && offset < toWrap.length() && !isWhitespace(toWrap.charAt(offset))) {
                offset++;
            }

            int cut = offset;

            while (preserveWords && offset < toWrap.length() && isWhitespace(toWrap.charAt(offset))) {
                offset++;
            }

            int wsLen = offset - cut;
            if (offset >= toWrap.length()) {
                break; // text ends in ws
            }
            builder.insert(builderShift + cut, "\n"); // insert a newline at cut point
            int afterCut = builderShift + cut + 1;
            // if there is whitespace space after the cut, delete it
            builder.replace(afterCut, afterCut + wsLen, "");

            // insert indent right after the cut
            builderShift += repeat(builder, afterCut, indentStyle, indentLevel);

            offset = cut + wsLen + 1 + width; // go to next line end in base string
        }

        return builder.toString();
    }


    private static int repeat(StringBuilder buffer, int fromOffset, String toRepeat, int times) {
        int insertedLen = 0;
        while (times-- > 0) {
            buffer.insert(fromOffset, toRepeat);
            insertedLen += toRepeat.length();
        }
        return insertedLen;
    }


    /**
     * Produces a templated render composed with a base renderer and some additional specs. If the
     * base renderer is also a templated renderer, its spec is merged with the others so that nesting
     * is avoided. E.g. {@code indented(1, surrounded("[", "]", templated(b->b.append("f")))} produces a single
     * nested template and not 3.
     */
    private static <T> ItemRenderer<T> templatedWrapper(ItemRenderer<T> base, Consumer<LiveTemplateBuilder<T>> before, Consumer<LiveTemplateBuilder<T>> after) {
        return new TemplatedItemRenderer<>(b -> {
            before.accept(b);

            if (base instanceof TemplatedItemRenderer) {
                ((TemplatedItemRenderer<T>) base).subTemplateBuilderSpec.accept(b);
            } else {
                b.render(Function.identity(), base);
            }

            after.accept(b);
        });
    }


    private static class MappedItemRenderer<T> extends ItemRenderer<T> {
        private final BiFunction<? super LiveTemplateBuilder<?>, ? super ObservableValue<? extends T>, ? extends Val<String>> myFun;

        private final boolean myNoEscape;


        /** Most general constructor. */
        private MappedItemRenderer(boolean ignoreEscape, BiFunction<? super LiveTemplateBuilder<?>, ? super ObservableValue<? extends T>, ? extends Val<String>> myFun) {
            this.myNoEscape = ignoreEscape;
            this.myFun = myFun;
        }

        // Constructors for renderers that don't use the builder config.


        @Override
        public Val<String> apply(LiveTemplateBuilder<?> liveTemplateBuilder, ObservableValue<? extends T> tObs) {
            return myFun.apply(liveTemplateBuilder, tObs);
        }


        public ItemRenderer<T> escapeWith(Function<String, String> escapeFun) {
            return myNoEscape ? this : new MappedItemRenderer<>(true, myFun.andThen(v -> ReactfxUtil.mapPreserveConst(v, escapeFun)));
        }


        static <T> MappedItemRenderer<T> mapping(boolean ignoreEscape, Function<? super T, ? extends Val<String>> fun) {
            return mappingObs(ignoreEscape, tObs -> Val.flatMap(tObs, fun));
        }


        static <T> MappedItemRenderer<T> mappingObs(boolean ignoreEscape, Function<? super ObservableValue<? extends T>, ? extends Val<String>> fun) {
            return new MappedItemRenderer<>(ignoreEscape, (ctx, tObs) -> fun.apply(tObs));
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
