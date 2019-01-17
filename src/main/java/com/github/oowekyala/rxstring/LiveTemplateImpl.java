package com.github.oowekyala.rxstring;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactfx.value.Val;
import org.reactfx.value.Var;


/**
 * @author Clément Fournier
 * @since 1.0
 */
class LiveTemplateImpl<D> implements LiveTemplate<D> {

    private final Var<D> myDataContext = Var.newSimpleVar(null);
    private final Var<ReplaceHandler> myReplaceHandler = Var.newSimpleVar(null);
    private final Var<BoundLiveTemplate<D>> myCurBound = Var.newSimpleVar(null);
    private final Val<String> myDelegateStringVal;


    public LiveTemplateImpl(Function<D, List<Val<String>>> dataBinder) {

        myDataContext.values().subscribe(newCtx -> {

            if (!myCurBound.isEmpty()) {
                myCurBound.getValue().unbind();
                myCurBound.setValue(null);
            }

            if (newCtx != null) {
                myCurBound.setValue(new BoundLiveTemplate<>(newCtx, dataBinder, myReplaceHandler));
            }
        });

        myDelegateStringVal = myCurBound.flatMap(Function.identity());
    }


    @Override
    public Val<D> dataContextProperty() {
        return myDataContext;
    }


    @Override
    public void setDataContext(D context) {
        myDataContext.setValue(context);
    }


    @Override
    public D getDataContext() {
        return myDataContext.getValue();
    }


    @Override
    public Val<ReplaceHandler> replacementHandler() {
        return myReplaceHandler;
    }


    @Override
    public void setReplaceHandler(ReplaceHandler handler) {
        myReplaceHandler.setValue(handler);
    }


    @Override
    public ReplaceHandler getReplaceHandler() {
        return myReplaceHandler.getValue();
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