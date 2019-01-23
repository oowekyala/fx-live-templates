package com.github.oowekyala.rxstring;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.reactfx.EventStreams;
import org.reactfx.RigidObservable;
import org.reactfx.Subscription;
import org.reactfx.collection.LiveList;
import org.reactfx.value.Val;

import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;


/**
 * Some utilities that extend the functionality of ReactFX. <b>This is internal API.</b> It's exposed
 * here for convenience but we make <b>no guarantee of compatibility</b> from version to version. Some
 * of those are proposed to be merged in the main codebase.
 *
 * @author Clément Fournier
 * @since 1.0
 */
public final class ReactfxExtensions {

    private ReactfxExtensions() {

    }


    static boolean isConst(ObservableValue<?> val) {
        return val instanceof RigidObservable;
    }


    // this breaks laziness
    static <T, R> Val<R> mapPreserveConst(Val<? extends T> val, Function<? super T, ? extends R> f) {
        return !isConst(val) ? val.map(f)
                             : val.getValue() == null ? Val.constant(null)
                                                      : Val.constant(f.apply(val.getValue()));
    }


    static <T, R> Val<R> flatMapPreserveConst(ObservableValue<T> val,
                                              Function<? super T, ? extends ObservableValue<R>> f) {
        return !isConst(val) ? Val.flatMap(val, f)
                             : val.getValue() == null ? Val.constant(null)
                                                      : Val.constant(f.apply(val.getValue()).getValue());
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
    static <E, F> List<F> lazyMappedView(List<? extends E> source, Function<? super E, ? extends F> f) {
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
    public static <T> Subscription dynamic(ObservableList<? extends T> elems,
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


    /**
     * Dynamically subscribes to elements of the list. When a replacement change is caught,
     * deleted items are matched onto a corresponding added item. If a match is found, {@link RebindSubscription#rebind(Object)}
     * is called, which allows recombining the new item with the old item. If a deleted item
     * has no matching added element, then {@link RebindSubscription#unsubscribe()} is called.
     * This method returns itself a {@link RebindSubscription} that can rebind all existing items
     * on new items, using the method described above.
     *
     * @param elems Collection to observe
     * @param f     Producer of rebind subscriptions for individual elements
     * @param <T>   Type of elements
     *
     * @return A rebind subscription for the whole list
     */
    public static <T> RebindSubscription<ObservableList<T>> dynamicRecombine(ObservableList<? extends T> elems,
                                                                             // prev elt or null, new elt, index -> sub
                                                                             BiFunction<? super T, Integer, ? extends RebindSubscription<T>> f) {

        List<RebindSubscription<T>> elemSubs = new ArrayList<>(elems.size());

        for (int i = 0, j = 0; i < elems.size(); i++, j++) {
            elemSubs.add(f.apply(elems.get(i), i));
        }

        Subscription lstSub = EventStreams.changesOf(elems).subscribe(ch -> {
            while (ch.next()) {
                if (ch.wasPermutated()) {
                    RebindSubscription<T> left = elemSubs.get(ch.getFrom());
                    RebindSubscription<T> right = elemSubs.set(ch.getTo(), left);
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

                        if (prev != null) {
                            elemSubs.set(i, elemSubs.get(i).rebind(added));
                        } else {
                            elemSubs.add(i, f.apply(added, i));
                        }
                        i++;
                        removedIdx++;
                    }
                }
            }
        });

        return rebindSub(elemSubs, f, lstSub);
    }


    // helper for dynamicRecombine
    private static <T> RebindSubscription<ObservableList<T>> rebindSub(List<RebindSubscription<T>> elemSubs,
                                                                       BiFunction<? super T, Integer, ? extends RebindSubscription<T>> f,
                                                                       Subscription lstSub) {
        return RebindSubscription.make(() -> {
            lstSub.unsubscribe();
            elemSubs.forEach(Subscription::unsubscribe);
        }, newElems -> {

            for (int i = 0; i < newElems.size(); i++) {
                if (i < elemSubs.size()) {
                    // this one has a corresponding existing element
                    elemSubs.set(i, elemSubs.get(i).rebind(newElems.get(i)));
                } else {
                    // new element
                    elemSubs.add(f.apply(newElems.get(i), i));
                }
            }

            // those older elements have no corresponding new element
            // remove them
            for (int i = newElems.size(); i < elemSubs.size(); i++) {
                elemSubs.get(i).unsubscribe();
            }

            return rebindSub(elemSubs, f, lstSub);
        });
    }


    /**
     * Creates a new LiveList that reflects the values of the elements of the
     * given list of observables. If any of the observable values contained in
     * the source list change, a list change is pushed (lazily). Additions or
     * removals made to the source collection are also reflected by the returned
     * list. It kind of behaves like {@code source.map(ObservableValue::getValue)},
     * except the list is also updated when the individual elements change.
     *
     * <p>The returned list is unmodifiable but can be observed.
     *
     * @param source List of observables
     * @param <E>    Type of values of the returned list
     *
     * @return A new live list
     *
     * @throws NullPointerException If the source collection is null
     */
    public static <E> LiveList<E> flattenVals(ObservableList<? extends ObservableValue<? extends E>> source) {
        return new FlatValList<>(Objects.requireNonNull(source));
    }


    /**
     * Returns a view on the original list that pretends its elements are separated by delimiters,
     * and that the first and last are a special prefix and suffix. E.g. calling this method with
     * parameters source: [A,B], prefix: p, suffix:s, delimiter:d will return a list view with the
     * elements [p,A,d,B,s]. The empty list is mapped to [p,s], so the returned list view has never
     * less than two elements. When elements are added or removed from the source list, events are
     * fired for those events as well as for the possible modifications of delimiter position. E.g.
     * if the source is empty and you add elements A and B, then the returned list will fire a list
     * change adding the sublist [A,d,B].
     *
     * @param source    Source list view
     * @param prefix    First element of the returned list view
     * @param suffix    Last element of the returned list view
     * @param delimiter Element inserted between any element of the source list
     *
     * @author Clément Fournier
     * @since 1.0
     */
    public static <E> LiveList<E> asDelimited(ObservableList<? extends E> source, E prefix, E suffix, E delimiter) {
        return new DelimitedListView<>(source, prefix, suffix, delimiter);
    }


    /**
     * A subscription that has two ways of unsubscribing: one giving a chance
     * to replace an existing underlying data source (rebind), and one deleting
     * completely.
     *
     * @param <D> Type of data source
     */
    public interface RebindSubscription<D> extends Subscription {

        RebindSubscription<D> rebind(D newItem);


        @Override
        default RebindSubscription<D> and(Subscription other) {
            return make(other.and(this), this::rebind);
        }


        static <D> RebindSubscription<D> make(Subscription unbinder, Function<D, RebindSubscription<D>> rebinder) {
            return new RebindSubscription<D>() {
                @Override
                public RebindSubscription<D> rebind(D newItem) {
                    return rebinder.apply(newItem);
                }


                @Override
                public void unsubscribe() {
                    unbinder.unsubscribe();
                }
            };
        }
    }

}
