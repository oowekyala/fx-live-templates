package com.github.oowekyala.rxstring;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.reactfx.collection.LiveList;
import org.reactfx.value.Val;

import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;


/**
 * A type used to map observable lists of Ts to observable lists of strings.
 *
 * @param <T> Type of values of the list
 */
public class SeqRenderer<T> implements BiFunction<LiveTemplateBuilder<?>, ObservableList<? extends ObservableValue<? extends T>>, LiveList<Val<String>>> {


    private final BiFunction<? super LiveTemplateBuilder<?>, ? super ObservableList<? extends ObservableValue<? extends T>>, ? extends LiveList<Val<String>>> myFun;


    private SeqRenderer(BiFunction<? super LiveTemplateBuilder<?>, ? super ObservableList<? extends ObservableValue<? extends T>>, ? extends LiveList<Val<String>>> myFun) {
        this.myFun = myFun;
    }


    /** Doesn't use the context. */
    private SeqRenderer(Function<? super ObservableList<? extends ObservableValue<? extends T>>, ? extends LiveList<Val<String>>> fun) {
        this((ctx, lst) -> fun.apply(lst));
    }


    @Override
    public LiveList<Val<String>> apply(LiveTemplateBuilder<?> context, ObservableList<? extends ObservableValue<? extends T>> ts) {
        return myFun.apply(context, ts);
    }


    private SeqRenderer<T> delimited(String prefix, String suffix, String delim) {
        return new SeqRenderer<>((ctx, obsList) -> {
            LiveList<Val<String>> base = this.apply(ctx, obsList);
            return new DelimitedListView<>(base, Val.constant(delim), Val.constant(prefix), Val.constant(suffix));
        });
    }


    /**
     * Returns a seq renderer that displays its elements with the given {@link ItemRenderer},
     * and adds delimiters and a prefix and suffix. If the sequence is empty, only the prefix
     * and suffix are shown.
     *
     * @param itemRenderer Renderer for elements
     * @param prefix       Prefix
     * @param suffix       Suffix
     * @param delim        Delimiter
     * @param <T>          Type of items
     *
     * @return A delimited seq renderer
     */
    public static <T> SeqRenderer<T> delimited(ItemRenderer<T> itemRenderer, String prefix, String suffix, String delim) {
        return forItems(itemRenderer).delimited(prefix, suffix, delim);
    }


    /**
     * A seq renderer that renders all its elements with the given item renderer.
     *
     * @param itemRenderer Renderer for items
     * @param <T>          Type of items
     *
     * @return A simple seq renderer
     */
    public static <T> SeqRenderer<T> forItems(ItemRenderer<? super T> itemRenderer) {
        return new SeqRenderer<>((ctx, seq) -> LiveList.map(seq, tObs -> itemRenderer.apply(ctx, tObs)));
    }

}
