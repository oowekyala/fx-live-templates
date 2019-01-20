package com.github.oowekyala.rxstring;

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
                                       Val<String> previous,
                                       Val<String> val,
                                       ValIdx valIdx,
                                       ReplaceHandler callback) {

        if (previous == null) {
            String initialValue = val.getValue();
            // insert it whole
            callback.replace(0, 0, initialValue == null ? "" : initialValue);
        }

        Subscription sub = null;

        if (val instanceof LiveTemplateImpl) {
            LiveTemplateImpl<?> subTemplate = (LiveTemplateImpl<?>) val;

            if (previous instanceof LiveTemplateImpl) {
                // rebind the existing subtemplate
                LiveTemplateImpl<?> existing = (LiveTemplateImpl<?>) previous;

                existing.importConfigFrom(parent);
                Subscription handler = existing.addInternalReplaceHandler(callback);
                Subscription rebindResult = existing.rebind(subTemplate);

                if (rebindResult != null) {
                    return handler.and(rebindResult);
                } // else initialise the template anyway
            }

            // initialise a new subtemplate

            // the subtemplate inherits some config properties from its outer parents,
            // eg whether to use a patch strategy
            subTemplate.importConfigFrom(parent);
            // add a replace handler to the bound value of the child

            sub = subTemplate.addInternalReplaceHandler(callback)
                             .and(() -> {
                                 subTemplate.dataContextProperty().unbind();
                                 subTemplate.setDataContext(null);
                             });
        } else {

            sub = val.orElseConst("") // so that the values in changes are never null
                     .changes()
                     .subscribe(change -> callback.replace(0, change.getOldValue().length(), change.getNewValue()));
        }

        if (previous != null) {
            String initialValue = val.getValue();
            int prevLength = valIdx.length();
            // replace previous
            callback.replace(0, prevLength, initialValue == null ? "" : initialValue);

        }
        return sub;
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
