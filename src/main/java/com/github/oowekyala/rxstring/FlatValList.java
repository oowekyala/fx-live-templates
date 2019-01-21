package com.github.oowekyala.rxstring;

import static java.util.Collections.singletonList;

import org.reactfx.Subscription;
import org.reactfx.collection.LiveList;
import org.reactfx.collection.LiveListBase;
import org.reactfx.collection.QuasiListChange;
import org.reactfx.collection.QuasiListModification;
import org.reactfx.collection.UnmodifiableByDefaultLiveList;
import org.reactfx.value.Val;

import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;


/**
 * Wrapper around an ObservableList&lt;ObservableValue&lt;T&gt;&gt; that presents
 * the interface of a LiveList&lt;T&gt; and subscribes to the changes of its individual
 * elements. See {@link ReactfxUtil#flattenVals(ObservableList)}.
 *
 * @author Clément Fournier
 */
class FlatValList<T> extends LiveListBase<T> implements UnmodifiableByDefaultLiveList<T> {

    private final ObservableList<? extends ObservableValue<? extends T>> mySource;
    private final LiveList<T> mapped;


    FlatValList(ObservableList<? extends ObservableValue<? extends T>> source) {
        this.mySource = source;
        this.mapped = LiveList.map(source, ObservableValue::getValue);
    }


    @Override
    public int size() {
        return mySource.size();
    }


    @Override
    public T get(int index) {
        return mySource.get(index).getValue();
    }


    @Override
    protected Subscription observeInputs() {

        return Subscription.multi(
            LiveList.observeQuasiChanges(mapped, this::notifyObservers),
            ReactfxUtil.dynamic(mySource,
                                (element, i) -> Val.observeChanges(element,
                                                                   (obs, oldV, newV) -> componentChanged(i, oldV, newV)))
        );
    }


    private void componentChanged(int idx, T oldV, T newV) {
        notifyObservers(componentChange(idx, oldV, newV));
    }


    private static <T> QuasiListChange<T> componentChange(int sourceIdx, T oldV, T newV) {
        return () -> singletonList(
            QuasiListModification.create(sourceIdx, singletonList(oldV), 1)
        );
    }
}