package com.github.oowekyala.rxstring;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactfx.Subscription;
import org.reactfx.collection.LiveArrayList;
import org.reactfx.collection.LiveList;
import org.reactfx.value.Val;

import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;


/**
 * @author Clément Fournier
 * @since 1.0
 */
@FunctionalInterface
interface BindingExtractor<D> {

    LiveList<Val<String>> extract(D context);


    // on pourrait dispatcher mais ballec
    default Subscription bindSingleVal(Val<String> val, Supplier<Integer> absoluteOffset, ReplaceHandler callback) {

        if (val instanceof LiveTemplateImpl) {

            LiveTemplateImpl<?> subTemplate = (LiveTemplateImpl<?>) val;

            // add a replace handler to the bound value of the child

            ReplaceHandler subHandler = (relativeStart, relativeEnd, value) -> {
                int absolute = absoluteOffset.get();

                callback.replace(
                    // the offsets here must be offset by the start of the subtemplate
                    absolute + relativeStart,
                    absolute + relativeEnd,
                    value);
            };

            subTemplate.addInternalReplaceHandler(subHandler);

            return () -> subTemplate.removeInternalReplaceHandler(subHandler);


        } else {
            return val.orElseConst("null") // so that the values in changes are never null
                      .changes()
                      .subscribe(change -> {
                          int startOffset = absoluteOffset.get();
                          int endOffset = startOffset + change.getOldValue().length();

                          callback.replace(startOffset, endOffset, change.getNewValue());
                      });
        }
    }


    static <T> ConstantBinding<T> makeConstant(String s) {
        return new ConstantBinding<>(s);
    }


    static <T> BindingExtractor<T> lift(Function<? super T, ObservableValue<String>> f) {
        return t -> new LiveArrayList<>(f.andThen(Val::wrap).apply(t));
    }


    static <D, T> SeqBinding<D> makeSeqBinding(Function<D, ? extends ObservableList<? extends T>> extractor,
                                               Function<? super T, ? extends ObservableValue<String>> renderer) {
        return d -> LiveList.map(extractor.apply(d), renderer.andThen(Val::wrap));
    }


    static <D, Sub> BindingExtractor<D> makeTemplateBinding(Function<? super D, ? extends ObservableValue<Sub>> extractor,
                                                            Consumer<LiveTemplateBuilder<Sub>> subTemplateBuilder) {

        Function<ObservableValue<Sub>, LiveTemplate<Sub>> templateMaker = obsT -> {
            LiveTemplateBuilder<Sub> builder = LiveTemplate.builder();
            subTemplateBuilder.accept(builder);
            LiveTemplate<Sub> template = builder.toTemplate();
            template.dataContextProperty().bind(obsT);
            return template;
        };

        return lift(extractor.andThen(templateMaker));
    }


    /**
     * Marker interface for sequence bindings.
     */
    interface SeqBinding<D> extends BindingExtractor<D> {
    }


    /**
     * Represents a constant binding. Distinct from the others because it allows
     * to compact them in the {@link LiveTemplateBuilderImpl}.
     *
     * @param <D> Anything
     */
    final class ConstantBinding<D> implements BindingExtractor<D> {
        final String constant;


        private ConstantBinding(String constant) {
            this.constant = constant;
        }


        @Override
        public LiveList<Val<String>> extract(D context) {
            return new LiveArrayList<>(Val.constant(constant));
        }
    }

}
