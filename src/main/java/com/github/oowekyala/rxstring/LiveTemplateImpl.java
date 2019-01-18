package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

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
class LiveTemplateImpl<D> implements LiveTemplate<D> {

    private final Var<D> myDataContext = Var.newSimpleVar(null);
    private final Var<BoundLiveTemplate<D>> myCurBound = Var.newSimpleVar(null);
    private final Val<String> myDelegateStringVal;

    // Those are shared with all the bound templates this instance generates
    private final List<ReplaceHandler> myInternalReplaceHandlers = new ArrayList<>();
    private final List<ReplaceHandler> myUserReplaceHandlers = new ArrayList<>();


    LiveTemplateImpl(List<BindingExtractor<D>> dataBinder) {

        myDataContext.values().subscribe(newCtx -> {

            if (!myCurBound.isEmpty()) {
                myCurBound.getValue().unbind();
                // cannot be null?
                myCurBound.setValue(null);
            }

            if (newCtx != null) {
                myCurBound.setValue(new BoundLiveTemplate<>(newCtx, dataBinder, myUserReplaceHandlers, myInternalReplaceHandlers));
            }
        });

        myDelegateStringVal = myCurBound.filter(Objects::nonNull).flatMap(Function.identity());
    }


    void addInternalReplaceHandler(ReplaceHandler handler) {
        myInternalReplaceHandlers.add(handler);
    }


    void removeInternalReplaceHandler(ReplaceHandler handler) {
        myInternalReplaceHandlers.remove(handler);
    }


    @Override
    public Var<D> dataContextProperty() {
        return myDataContext;
    }


    @Override
    public void addReplaceHandler(ReplaceHandler handler) {
        myUserReplaceHandlers.add(Objects.requireNonNull(handler));
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

}
