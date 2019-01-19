package com.github.oowekyala.rxstring;

import java.util.function.Function;

import org.reactfx.collection.LiveList;
import org.reactfx.value.Val;

import javafx.collections.ObservableList;


/**
 * A type used to map observable lists to observable lists of strings.
 * This is not really meant to be used from user code for now. Instead
 * you can use a {@link ValueRenderer} and pass it to {@link LiveTemplateBuilder#bindSeq(ValueRenderer, Function)}.
 *
 * @param <T> Type of values of the list
 */
@FunctionalInterface
public interface SeqRenderer<T> extends Function<ObservableList<? extends T>, LiveList<Val<String>>> {
    // TODO support delimiter logic!
}
