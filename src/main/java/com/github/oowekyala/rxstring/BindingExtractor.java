package com.github.oowekyala.rxstring;

import java.util.function.Consumer;
import java.util.function.Function;

import org.reactfx.collection.LiveList;
import org.reactfx.value.Val;

import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;


/**
 * @author Clément Fournier
 * @since 1.0
 */
abstract class BindingExtractor<D, T> implements Function<D, T> {


    static class SeqBinding<D, T> extends BindingExtractor<D, LiveList<Val<String>>> {

        private final Function<D, ? extends ObservableList<T>> extractor;
        private final Function<? super T, Val<String>> renderer;


        SeqBinding(Function<D, ? extends ObservableList<T>> extractor, Function<? super T, Val<String>> renderer) {
            this.extractor = extractor;
            this.renderer = renderer;
        }


        @Override
        public LiveList<Val<String>> apply(D d) {
            return LiveList.map(extractor.apply(d), renderer);
        }

    }

    // Those are simple to handle

    static final class ConstantBinding<T> extends ValExtractor<T> {

        final String constant;


        ConstantBinding(String constant) {
            super(d -> Val.constant(constant));
            this.constant = constant;
        }
    }

    static final class TemplateBinding<D, Sub> extends BindingExtractor<D, LiveTemplate<Sub>> {


        private final Function<? super D, LiveTemplate<Sub>> extractor;


        TemplateBinding(Function<? super D, ? extends ObservableValue<Sub>> extractor,
                        Consumer<LiveTemplateBuilder<Sub>> subTemplateBuilder) {

            Function<ObservableValue<Sub>, LiveTemplate<Sub>> templateMaker = obsT -> {
                LiveTemplateBuilder<Sub> builder = LiveTemplate.builder();
                subTemplateBuilder.accept(builder);
                LiveTemplate<Sub> template = builder.toTemplate();
                template.dataContextProperty().bind(obsT);
                return template;
            };

            this.extractor = extractor.andThen(templateMaker);

        }


        @Override
        public LiveTemplate<Sub> apply(D d) {
            return extractor.apply(d);
        }
    }

    static class ValExtractor<D> extends BindingExtractor<D, Val<String>> {

        private final Function<? super D, ? extends Val<String>> extractor;


        ValExtractor(Function<? super D, ? extends Val<String>> extractor) {
            this.extractor = extractor;
        }


        @Override
        public Val<String> apply(D d) {
            return extractor.apply(d);
        }
    }
}
