package com.github.oowekyala.rxstring;

import static java.util.Collections.singletonList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.reactfx.EventStreams;
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
 * @author Clément Fournier
 * @since 1.0
 */
public class ReactiveList<T> extends LiveListBase<T>
    implements UnmodifiableByDefaultLiveList<T> {


    private final ObservableList<? extends ObservableValue<? extends T>> mySource;
    private final LiveList<T> mapped;


    private ReactiveList(ObservableList<? extends ObservableValue<? extends T>> source) {
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
            dynamicListSub(mySource, (component, i) -> Val.observeChanges(component,
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


    private static <T> Subscription dynamicListSub(ObservableList<T> elems,
                                                   BiFunction<? super T, Integer, ? extends Subscription> f) {

        List<Subscription> elemSubs = new ArrayList<>(elems.size());

        for (int i = 0; i < elems.size(); i++) {
            elemSubs.add(f.apply(elems.get(i), i));
        }

        Subscription lstSub = EventStreams.changesOf(elems).subscribe(ch -> {
            while (ch.next()) {
                if (ch.wasPermutated()) {
                    Subscription left = elemSubs.get(ch.getFrom());
                    Subscription right = elemSubs.set(ch.getTo(), left);
                    elemSubs.set(ch.getFrom(), right);
                } else if (ch.wasRemoved()) {
                    // getFrom == getTo
                    int i = ch.getFrom();
                    for (T ignored : ch.getRemoved()) {
                        elemSubs.remove(i).unsubscribe();
                        i++;
                    }

                } else if (ch.wasAdded()) {
                    // [getFrom..getTo] === getAddedSubList
                    int i = ch.getFrom();
                    for (T added : ch.getAddedSubList()) {
                        elemSubs.add(i, f.apply(added, i));
                        i++;
                    }
                }
            }
        });

        return () -> {
            lstSub.unsubscribe();
            elemSubs.forEach(Subscription::unsubscribe);
        };
    }


    public static <T> ReactiveList<T> create(ObservableList<? extends ObservableValue<? extends T>> source) {
        return new ReactiveList<>(source);
    }
}
