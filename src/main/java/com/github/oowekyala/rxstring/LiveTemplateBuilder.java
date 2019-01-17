package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.reactfx.value.Val;

import javafx.beans.value.ObservableValue;


/**
 * @author Clément Fournier
 * @since 1.0
 */
public class LiveTemplateBuilder<D> {

    private final List<Function<? super D, ? extends Val<String>>> myBindings;


    private LiveTemplateBuilder(List<Function<? super D, ? extends Val<String>>> bindings) {
        this.myBindings = new ArrayList<>(bindings);
    }


    LiveTemplateBuilder() {
        this(Collections.emptyList());
    }


    public LiveTemplateBuilder<D> append(String s) {
        myBindings.add(d -> Val.constant(s));
        return this;
    }


    public LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<String>> binder) {
        myBindings.add(binder.andThen(Val::wrap));
        return this;
    }


    public LiveTemplateBuilder<D> bindTemplate(Function<? super D, LiveTemplate<?>> binder) {
        myBindings.add(binder);
        return this;
    }


    public LiveTemplateBuilder<D> copy() {
        return new LiveTemplateBuilder<>(myBindings);
    }


    public LiveTemplate<D> toTemplate() {
        return new LiveTemplateImpl<>(lift(myBindings));
    }


    private static <T, K> Function<T, List<K>> lift(List<Function<? super T, ? extends K>> binders) {
        return t -> binders.stream().map(b -> b.apply(t)).collect(Collectors.toList());
    }
}
