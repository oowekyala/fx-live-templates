package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.reactfx.value.Val;

import javafx.beans.value.ObservableValue;


/**
 * @author Clément Fournier
 * @since 1.0
 */
class LiveTemplateBuilderImpl<D> implements LiveTemplateBuilder<D> {

    private final List<Function<? super D, ? extends Val<String>>> myBindings;


    private LiveTemplateBuilderImpl(List<Function<? super D, ? extends Val<String>>> bindings) {
        this.myBindings = new ArrayList<>(bindings);
    }


    LiveTemplateBuilderImpl() {
        this(Collections.emptyList());
    }


    @Override
    public LiveTemplateBuilder<D> append(String string) {
        Objects.requireNonNull(string);

        if (myBindings.size() > 0 && myBindings.get(myBindings.size() - 1) instanceof ConstantBinding) {
            // merge consecutive constants
            ConstantBinding binding = (ConstantBinding) myBindings.remove(myBindings.size() - 1);
            myBindings.add(new ConstantBinding(binding.constant + string));
        } else {
            myBindings.add(new ConstantBinding(string));
        }
        return this;
    }


    @Override
    public <T> LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<T>> extractor) {
        return bind(extractor, Objects::toString);
    }


    @Override
    public <T> LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<T>> extractor, Function<? super T, String> renderer) {
        myBindings.add(extractor.andThen(Val::wrap).andThen(val -> val.map(renderer)));
        return this;
    }


    @Override
    public <T> LiveTemplateBuilder<D> bindTemplate(Function<? super D, ? extends ObservableValue<T>> extractor,
                                                   Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {

        Function<ObservableValue<T>, LiveTemplate<T>> templateMaker = obsT -> {
            LiveTemplateBuilder<T> builder = LiveTemplate.builder();
            subTemplateBuilder.accept(builder);
            LiveTemplate<T> template = builder.toTemplate();
            template.dataContextProperty().bind(obsT);
            return template;
        };

        // it's important that the binder return a LiveTemplate (not eg a FlatMappedVal), otherwise
        // the minimal changes cannot detected
        myBindings.add(extractor.andThen(templateMaker));
        return this;
    }


    @Override
    public LiveTemplateBuilder<D> copy() {
        return new LiveTemplateBuilderImpl<>(myBindings);
    }


    @Override
    public LiveTemplate<D> toTemplate() {
        return new LiveTemplateImpl<>(lift(myBindings));
    }


    private static <T, K> Function<T, List<K>> lift(List<Function<? super T, ? extends K>> binders) {
        return t -> binders.stream().map(b -> b.apply(t)).collect(Collectors.toList());
    }


    private static class ConstantBinding implements Function<Object, Val<String>> {

        private final String constant;


        private ConstantBinding(String constant) {
            this.constant = constant;
        }


        @Override
        public Val<String> apply(Object o) {
            return Val.constant(constant);
        }
    }
}
