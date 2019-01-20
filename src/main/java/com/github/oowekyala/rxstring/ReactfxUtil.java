package com.github.oowekyala.rxstring;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.reactfx.EventStreams;
import org.reactfx.RigidObservable;
import org.reactfx.Subscription;
import org.reactfx.collection.LiveListBase;
import org.reactfx.collection.UnmodifiableByDefaultLiveList;
import org.reactfx.util.TriFunction;
import org.reactfx.value.Val;

import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;


/**
 * @author Clément Fournier
 * @since 1.0
 */
final class ReactfxUtil {

    private ReactfxUtil() {

    }


    public static boolean isConst(ObservableValue<?> val) {
        return val instanceof RigidObservable;
    }


    // this breaks laziness
    public static <T, R> Val<R> mapPreserveConst(Val<? extends T> val, Function<? super T, ? extends R> f) {
        return isConst(val) ? Val.constant(f.apply(val.getValue())) : val.map(f);
    }


    /**
     * A mapped view that remembers already computed elements. The function
     * should be pure.
     *
     * @param source Source collection
     * @param f      Mapper
     * @param <E>    Source elt type
     * @param <F>    Target elt type
     */
    public static <E, F> List<F> lazyMappedView(List<? extends E> source, Function<? super E, ? extends F> f) {
        return new AbstractList<F>() {

            private Map<E, F> cache = new WeakHashMap<>();


            @Override
            public F get(int index) {
                return cache.computeIfAbsent(source.get(index), f);
            }


            @Override
            public int size() {
                return source.size();
            }
        };
    }


    /**
     * Dynamically subscribes to all elements of the given observable list.
     * When an element is added to the list, it is automatically subscribed to.
     * When an element is removed from the list, it is automatically unsubscribed
     * from.
     *
     * @param elems Observable list of elements that will be subscribed to
     * @param f     Function to subscribe to an element of the list. The first parameter
     *              is the element, the second is its index in the new source list
     * @param <T>   Type of elements
     *
     * @return An aggregate subscription that tracks elementary subscriptions.
     * When the returned subscription is unsubscribed, all elementary
     * subscriptions are unsubscribed as well, and no new elementary
     * subscriptions will be created.
     */
    // Until ReactFX merges my PR
    static <T> Subscription dynamic(ObservableList<? extends T> elems,
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
                } else {
                    if (ch.wasRemoved()) {
                        // oldList[from : from + removed.size] === removed
                        int from = ch.getFrom();
                        for (T ignored : ch.getRemoved()) {
                            elemSubs.remove(from).unsubscribe();
                        }
                    }
                    if (ch.wasAdded()) {
                        // newList[from : to] === addedSubList
                        int i = ch.getFrom();
                        for (T added : ch.getAddedSubList()) {
                            elemSubs.add(i, f.apply(added, i));
                            i++;
                        }
                    }
                }
            }
        });

        return () -> {
            lstSub.unsubscribe();
            elemSubs.forEach(Subscription::unsubscribe);
        };
    }


    static <T> Subscription dynamicRecombine(ObservableList<? extends T> prevElems,
                                             ObservableList<? extends T> elems,
                                             // prev elt or null, new elt, index -> sub
                                             TriFunction<? super T, ? super T, Integer, ? extends Subscription> f) {

        List<Subscription> elemSubs = new ArrayList<>(elems.size());

        for (int i = 0, j = 0; i < elems.size(); i++, j++) {
            T prev = prevElems != null && j < prevElems.size() ? prevElems.get(j) : null;
            elemSubs.add(f.apply(prev, elems.get(i), i));
        }

        Subscription lstSub = EventStreams.changesOf(elems).subscribe(ch -> {
            while (ch.next()) {
                if (ch.wasPermutated()) {
                    Subscription left = elemSubs.get(ch.getFrom());
                    Subscription right = elemSubs.set(ch.getTo(), left);
                    elemSubs.set(ch.getFrom(), right);
                } else {
                    List<? extends T> removed = ch.getRemoved();
                    int addedSize = ch.getAddedSize();
                    int from = ch.getFrom();

                    if (addedSize < removed.size()) {
                        // oldList[from : from + removed.size] === removed
                        for (int i = addedSize; i < removed.size(); i++) {
                            // unsubscribe only those removed elements that don't have a matching added element
                            elemSubs.remove(from + addedSize).unsubscribe();
                        }
                    }

                    // newList[from : to] === addedSubList
                    int removedIdx = 0;
                    int i = from;
                    for (T added : ch.getAddedSubList()) {
                        T prev = removedIdx < removed.size() ? removed.get(removedIdx) : null;

                        Subscription sub = f.apply(prev, added, i);
                        if (prev != null) {
                            // set bc it wasn't removed before
                            // the un-subscription is the responsibility of the subscription maker
                            elemSubs.set(i, sub);
                        } else {
                            elemSubs.add(i, sub);
                        }
                        i++;
                        removedIdx++;
                    }
                }
            }
        });

        return () -> {
            lstSub.unsubscribe();
            elemSubs.forEach(Subscription::unsubscribe);
        };
    }


    interface RebindSubscription<D> extends Subscription {


    }

}
