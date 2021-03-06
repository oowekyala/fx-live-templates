package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.reactfx.collection.LiveList;
import org.reactfx.value.Val;

import com.github.oowekyala.rxstring.BindingExtractor.ConstantBinding;
import javafx.collections.ObservableList;


/**
 * Implementation.
 *
 * @author Clément Fournier
 * @since 1.0
 */
final class LiveTemplateBuilderImpl<D> implements LiveTemplateBuilder<D> {

    private final List<BindingExtractor<D>> myBindings;
    private final InheritableConfig myInheritableConfig;


    private LiveTemplateBuilderImpl(List<BindingExtractor<D>> bindings, InheritableConfig baseConfig) {
        this.myBindings = new ArrayList<>(bindings);
        this.myInheritableConfig = new InheritableConfig(baseConfig);
    }


    LiveTemplateBuilderImpl() {
        this(Collections.emptyList(), new InheritableConfig());
    }


    private LiveTemplateBuilderImpl(InheritableConfig baseConfig) {
        this(Collections.emptyList(), baseConfig);
    }


    <T> LiveTemplateBuilder<T> spawnChildWithSameConfig() {
        return new LiveTemplateBuilderImpl<>(this.myInheritableConfig);
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
    public String getDefaultIndent() {
        return myInheritableConfig.defaultIndent;
    }


    @Override
    public Function<String, String> getDefaultEscapeFunction() {
        return myInheritableConfig.escapeFun;
    }


    @Override
    public LiveTemplateBuilder<D> withDefaultIndent(String indentStyle) {
        myInheritableConfig.defaultIndent = Objects.requireNonNull(indentStyle);
        return this;
    }


    @Override
    public LiveTemplateBuilder<D> withDefaultEscape(Function<String, String> stringEscapeFunction) {
        myInheritableConfig.escapeFun = Objects.requireNonNull(stringEscapeFunction);
        return this;
    }


    @Override
    public <T> LiveTemplateBuilder<D> bindSeq(Function<D, ? extends ObservableList<? extends T>> extractor, SeqRenderer<? super T> renderer) {
        myBindings.add(extractor.andThen(lst -> renderer.apply(this, LiveList.map(lst, Val::constant)))::apply);
        return this;
    }


    @Override
    public LiveTemplate<D> toTemplate() {
        return new LiveTemplateImpl<>(myBindings);
    }


    static <D> LiveTemplateBuilder<D> newBuilder(List<BindingExtractor<D>> bindings) {
        return new LiveTemplateBuilderImpl<>(new ArrayList<>(bindings), new InheritableConfig());
    }


    private static class InheritableConfig {
        String defaultIndent = "    ";
        Function<String, String> escapeFun = Function.identity();


        /** Default config. */
        InheritableConfig() {
        }


        /** Copy constructor. */
        InheritableConfig(InheritableConfig toCopy) {
            this.defaultIndent = toCopy.defaultIndent;
            this.escapeFun = toCopy.escapeFun;
        }
    }

}
