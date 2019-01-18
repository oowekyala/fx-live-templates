package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactfx.value.Val;

import com.github.oowekyala.rxstring.BindingExtractor.ConstantBinding;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;


/**
 * @author Clément Fournier
 * @since 1.0
 */
class LiveTemplateBuilderImpl<D> implements LiveTemplateBuilder<D> {

    // TODO everything could be mapped to seqs here and support low-level delimiting logic

    private final List<BindingExtractor<D>> myBindings;
    private String myDefaultIndent = "    ";


    private LiveTemplateBuilderImpl(List<BindingExtractor<D>> bindings) {
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
            myBindings.add(BindingExtractor.makeConstant(binding.constant + string));
        } else {
            myBindings.add(BindingExtractor.makeConstant(string));
        }
        return this;
    }


    @Override
    public LiveTemplateBuilder<D> appendIndent(int level) {
        return appendIndent(level, myDefaultIndent);
    }


    @Override
    public LiveTemplateBuilder<D> withDefaultIndent(String indentStyle) {
        myDefaultIndent = Objects.requireNonNull(indentStyle);
        return this;
    }


    @Override
    public <T> LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<T>> extractor, Function<? super T, String> renderer) {
        myBindings.add(BindingExtractor.lift(extractor.andThen(Val::wrap).andThen(val -> val.map(renderer))));
        return this;
    }


    @Override
    public <T> LiveTemplateBuilder<D> bindTemplate(Function<? super D, ? extends ObservableValue<T>> extractor,
                                                   Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {
        myBindings.add(BindingExtractor.makeTemplateBinding(extractor, subTemplateBuilder));
        return this;
    }


    @Override
    public <T> LiveTemplateBuilder<D> bindSeq(ValueRenderer<? super T> renderer, Function<D, ? extends ObservableList<? extends T>> extractor) {
        myBindings.add(BindingExtractor.makeSeqBinding(extractor, renderer));
        return this;
    }


    @Override
    public LiveTemplateBuilder<D> copy() {
        return new LiveTemplateBuilderImpl<>(myBindings);
    }


    @Override
    public LiveTemplate<D> toTemplate() {
        return new LiveTemplateImpl<>(myBindings);
    }

}
