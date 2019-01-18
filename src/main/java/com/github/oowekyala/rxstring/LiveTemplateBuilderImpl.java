package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactfx.value.Val;

import com.github.oowekyala.rxstring.BindingExtractor.ConstantBinding;
import com.github.oowekyala.rxstring.BindingExtractor.TemplateBinding;
import com.github.oowekyala.rxstring.BindingExtractor.ValExtractor;
import javafx.beans.value.ObservableValue;


/**
 * @author Clément Fournier
 * @since 1.0
 */
class LiveTemplateBuilderImpl<D> implements LiveTemplateBuilder<D> {

    private final List<BindingExtractor<D, ?>> myBindings;


    private LiveTemplateBuilderImpl(List<BindingExtractor<D, ?>> bindings) {
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
            myBindings.add(new ConstantBinding<>(binding.constant + string));
        } else {
            myBindings.add(new ConstantBinding<>(string));
        }
        return this;
    }


    @Override
    public <T> LiveTemplateBuilder<D> bind(Function<? super D, ? extends ObservableValue<T>> extractor, Function<? super T, String> renderer) {
        myBindings.add(new ValExtractor<>(extractor.andThen(Val::wrap).andThen(val -> val.map(renderer))));
        return this;
    }


    @Override
    public <T> LiveTemplateBuilder<D> bindTemplate(Function<? super D, ? extends ObservableValue<T>> extractor,
                                                   Consumer<LiveTemplateBuilder<T>> subTemplateBuilder) {
        myBindings.add(new TemplateBinding<>(extractor, subTemplateBuilder));
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
