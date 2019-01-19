package com.github.oowekyala.rxstring;

import java.util.function.Function;

import org.reactfx.collection.LiveList;
import org.reactfx.value.Val;

import javafx.collections.ObservableList;


/**
 * A type used to map observable lists of Ts to observable lists of strings.
 *
 * @param <T> Type of values of the list
 */
public class SeqRenderer<T> implements Function<ObservableList<? extends T>, LiveList<Val<String>>> {


    private final Function<? super ObservableList<? extends T>, ? extends LiveList<Val<String>>> myFun;


    SeqRenderer(Function<? super ObservableList<? extends T>, ? extends LiveList<Val<String>>> fun) {
        myFun = fun;
    }


    @Override
    public LiveList<Val<String>> apply(ObservableList<? extends T> ts) {
        return myFun.apply(ts);
    }


    private SeqRenderer<T> delimited(String prefix, String suffix, String delim) {
        return new SeqRenderer<>(obsList -> {
            LiveList<Val<String>> base = this.apply(obsList);
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
        return itemRenderer.toSeq().delimited(prefix, suffix, delim);
    }

}
