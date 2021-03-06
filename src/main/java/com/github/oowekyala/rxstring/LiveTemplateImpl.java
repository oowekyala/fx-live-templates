package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactfx.Subscription;
import org.reactfx.value.Val;
import org.reactfx.value.Var;


/**
 * Implementation for {@link LiveTemplate}. Basically contains logic to switch
 * the data context and implements {@link Val}. The core logic is in {@link BoundLiveTemplate},
 * which are spawned by this class when the data context changes.
 *
 * @author Clément Fournier
 * @since 1.0
 */
final class LiveTemplateImpl<D> implements LiveTemplate<D> {

    private final Var<D> myDataContext = Var.newSimpleVar(null);
    private final Var<BoundLiveTemplate<D>> myCurBound = Var.newSimpleVar(null);
    private final Val<String> myDelegateStringVal;

    // Those are shared with all the bound templates this instance generates
    private final Var<ReplaceHandler> myInternalReplaceHandlers = Var.newSimpleVar(null);
    private final List<ReplaceHandler> myUserReplaceHandlers = new ArrayList<>();
    private final Var<Boolean> useDiffMatchPatch = Var.newSimpleVar(true);
    private final List<BindingExtractor<D>> myDataBindings;


    LiveTemplateImpl(List<BindingExtractor<D>> dataBinder) {
        this.myDataBindings = Collections.unmodifiableList(dataBinder);

        myDataContext.values().subscribe(newCtx -> {

            if (myCurBound.isPresent() && newCtx != null) {
                myCurBound.getValue().rebind(newCtx);
            } else if (newCtx != null) {
                myCurBound.setValue(new BoundLiveTemplate<>(newCtx, this, myDataBindings, myUserReplaceHandlers, myInternalReplaceHandlers));
            } else {
                myCurBound.ifPresent(BoundLiveTemplate::unbind);
                myCurBound.setValue(null);
            }
        });

        myDelegateStringVal = myCurBound.filter(Objects::nonNull).flatMap(Function.identity());
    }


    Subscription addInternalReplaceHandler(ReplaceHandler handler) {
        myInternalReplaceHandlers.setValue(handler);
        return () -> myInternalReplaceHandlers.setValue(null);
    }


    @SuppressWarnings("unchecked")
    void rebind(LiveTemplateImpl<?> other/*non null*/) {
        D newCtx = (D) other.getDataContext();
        this.dataContextProperty().unbind();
        this.setDataContext(newCtx);
    }


    @Override
    public Var<D> dataContextProperty() {
        return myDataContext;
    }


    @Override
    public Var<Boolean> isUseDiffMatchPatchStrategyProperty() {
        return useDiffMatchPatch;
    }


    @Override
    public Subscription addReplaceHandler(ReplaceHandler handler) {
        myCurBound.getOpt().ifPresent(bound -> handler.replace(0, 0, bound.getValue()));
        myUserReplaceHandlers.add(Objects.requireNonNull(handler));
        return () -> removeReplaceHandler(handler);
    }


    @Override
    public void removeReplaceHandler(ReplaceHandler handler) {
        myUserReplaceHandlers.remove(handler);
    }


    @Override
    public String getValue() {
        return myDelegateStringVal.getValue();
    }


    @Override
    public void addObserver(Consumer<? super String> observer) {
        myDelegateStringVal.addObserver(observer);
    }


    @Override
    public void removeObserver(Consumer<? super String> observer) {
        myDelegateStringVal.removeObserver(observer);
    }


    /**
     * Copies the configuration of the given template into this one one.
     * User replace handlers are not copied.
     */
    void importConfigFrom(LiveTemplate<?> liveTemplate) {
        this.setUseDiffMatchPatchStrategy(liveTemplate.isUseDiffMatchPatchStrategy());
    }


    @Override
    public LiveTemplate<D> copy() {
        LiveTemplateImpl<D> copy = (LiveTemplateImpl<D>) LiveTemplateBuilderImpl.newBuilder(myDataBindings).toTemplate();
        copy.importConfigFrom(this);
        return copy;
    }


    // test only
    Val<Long> totalSubscriptions() {
        return myCurBound.map(BoundLiveTemplate::totalSubscriptions);
    }


}
