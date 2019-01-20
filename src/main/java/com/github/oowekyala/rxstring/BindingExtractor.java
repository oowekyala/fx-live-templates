package com.github.oowekyala.rxstring;

import java.util.function.Supplier;

import org.reactfx.Subscription;
import org.reactfx.collection.LiveArrayList;
import org.reactfx.collection.LiveList;
import org.reactfx.value.Val;


/**
 * Extracts a LiveList[Val[String]] from a data context and binds its values.
 * Used by the {@link BoundLiveTemplate}, created by the {@link LiveTemplateBuilderImpl}.
 *
 * @author Clément Fournier
 * @since 1.0
 */
@FunctionalInterface
interface BindingExtractor<D> {

    LiveList<Val<String>> extract(D context);


    /**
     * Binds the given val to relevant replacement logic, and returns a subscription to unbind it.
     * Text deletion should not be handled in this subscription, it's handled upstream. The subscription
     * is just supposed to stop observing the val and not cleanup.
     */
    // on pourrait dispatcher mais ballec
    default Subscription bindSingleVal(LiveTemplate<?> parent,
                                       Val<String> val,
                                       Supplier<Integer> absoluteOffset,
                                       ReplaceHandler callback) {

        if (val instanceof LiveTemplateImpl) {

            LiveTemplateImpl<?> subTemplate = (LiveTemplateImpl<?>) val;

            // the subtemplate inherits some config properties from its outer parents,
            // eg whether to use a patch strategy
            subTemplate.importConfigFrom(parent);
            // add a replace handler to the bound value of the child

            return subTemplate.addInternalReplaceHandler(callback.withOffset(absoluteOffset))
                              .and(() -> {
                                  subTemplate.dataContextProperty().unbind();
                                  subTemplate.setDataContext(null);
                              });
        } else {
            return val.orElseConst("") // so that the values in changes are never null
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
