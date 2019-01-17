package com.github.oowekyala.rxstring;

import java.util.ArrayList;
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

    private final List<Function<D, Val<String>>> myBindings = new ArrayList<>();


    LiveTemplateBuilder() {

    }


    public LiveTemplateBuilder<D> append(String s) {
        myBindings.add(d -> Val.constant(s));
        return this;
    }


    public LiveTemplateBuilder<D> bind(Function<D, ObservableValue<String>> binder) {
        myBindings.add(binder.andThen(Val::wrap));
        return this;
    }


    public LiveTemplate<D> toTemplate() {
        return new LiveTemplateImpl<>(hoist(myBindings));
    }


    private static <T, K> Function<T, List<K>> hoist(List<Function<T, K>> binders) {
        return t -> binders.stream().map(b -> b.apply(t)).collect(Collectors.toList());
    }


}
