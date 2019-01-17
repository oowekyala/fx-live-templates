package com.github.oowekyala.rxstring;

import static java.util.Collections.singletonList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

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
import javafx.collections.ObservableSet;


/**
 * Wrapper around an ObservableList&lt;ObservableValue&lt;T&gt;&gt; that presents
 * the interface of a LiveList&lt;T&gt; and subscribes to the changes of its individual
 * elements.
 *
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


    /**
     * Like {@link Subscription#dynamic(ObservableSet, Function)} but for an observable list.
     *
     * @param elems List
     * @param f     Mapper, the first param is the element, the second is its index in the elems list
     * @param <T>   Type of elements
     *
     * @return A subscription that closes the open subscriptions to the elements of the list
     */
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


    /**
     * Creates a new LiveList that reflects the values of the elements of the
     * given list of observables. The change events pushed by the returned list
     * correspond to either changes in the source collection, or changes in the
     * individual elements. Both type of changes are considered regular list changes.
     *
     * @param source List of observables
     * @param <T>    Type of values of the returned list
     *
     * @return A new live list
     *
     * @throws NullPointerException If the source collection is null
     */
    public static <T> LiveList<T> fromVals(ObservableList<? extends ObservableValue<? extends T>> source) {
        return new ReactiveList<>(Objects.requireNonNull(source));
    }
}
