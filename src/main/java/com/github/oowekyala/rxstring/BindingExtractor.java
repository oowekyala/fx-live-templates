package com.github.oowekyala.rxstring;

import org.reactfx.Subscription;
import org.reactfx.collection.LiveArrayList;
import org.reactfx.collection.LiveList;
import org.reactfx.value.Val;

import com.github.oowekyala.rxstring.ReactfxUtil.RebindSubscription;


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
    static RebindSubscription<Val<String>> bindSingleVal(LiveTemplate<?> parent,
                                                         Val<String> val,
                                                         ValIdx valIdx) {

        valIdx.replaceValue(val.getValue());

        if (val instanceof LiveTemplateImpl) {
            LiveTemplateImpl<?> subTemplate = (LiveTemplateImpl<?>) val;

            // initialise a new subtemplate

            // the subtemplate inherits some config properties from its outer parents,
            // eg whether to use a patch strategy
            subTemplate.importConfigFrom(parent);
            // add a replace handler to the bound value of the child

            subTemplate.addInternalReplaceHandler(valIdx::replaceRelative);

            return templateRebindSub(parent, subTemplate, valIdx);
        } else {

            return valRebindSub(parent, val, valIdx);
        }
    }


    static RebindSubscription<Val<String>> templateRebindSub(LiveTemplate<?> parent,
                                                             LiveTemplateImpl<?> subTemplate,
                                                             ValIdx valIdx) {
        return RebindSubscription.make(() -> {
            subTemplate.dataContextProperty().unbind();
            subTemplate.setDataContext(null);
        }, newItem -> {

            if (newItem instanceof LiveTemplateImpl) {
                // rebind the existing subtemplate

                subTemplate.importConfigFrom(parent);
                subTemplate.rebind((LiveTemplateImpl<?>) newItem);
                return templateRebindSub(parent, subTemplate, valIdx);
            } else {
                subTemplate.dataContextProperty().unbind();
                subTemplate.setDataContext(null);
                return bindSingleVal(parent, newItem, valIdx);
            }
        });
    }


    static RebindSubscription<Val<String>> valRebindSub(LiveTemplate<?> parent,
                                                        Val<String> someVal,
                                                        ValIdx valIdx) {
        Subscription sub = someVal.orElseConst("") // so that the values in changes are never null
                                  .changes()
                                  .subscribe(change -> valIdx.replaceValue(change.getNewValue()));

        return RebindSubscription.make(sub, newItem -> {
            sub.unsubscribe();
            valIdx.replaceValue(newItem.getValue());
            return bindSingleVal(parent, newItem, valIdx);
        });
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
