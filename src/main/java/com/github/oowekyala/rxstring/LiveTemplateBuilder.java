package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
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


    public LiveTemplateBuilder<D> endLine() {
        return appendLine("");
    }


    public LiveTemplateBuilder<D> appendLine(String s) {
        return append(s + "\n");
    }


    public LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<?>> binder) {
        myBindings.add(binder.andThen(Val::wrap).andThen(val -> val.map(Object::toString)));
        return this;
    }


    public <T> LiveTemplateBuilder<D> bindTemplate(Function<? super D, ? extends ObservableValue<T>> extractor,
                                                   Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {

        Function<ObservableValue<T>, LiveTemplate<T>> templateMaker = obsT -> {
            LiveTemplateBuilder<T> builder = LiveTemplate.builder();
            subTemplateBuilder.accept(builder);
            LiveTemplate<T> template = builder.toTemplate();
            template.dataContextProperty().bind(obsT);
            return template;
        };

        // it's important that the binder return a LiveTemplate, otherwise the minimal changes are not detected
        myBindings.add(extractor.andThen(templateMaker));
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
