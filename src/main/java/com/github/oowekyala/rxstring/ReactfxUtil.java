package com.github.oowekyala.rxstring;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.reactfx.EventStreams;
import org.reactfx.Subscription;

import javafx.collections.ObservableList;


/**
 * Ersatz until reactfx merges my PR.
 *
 * @author Clément Fournier
 * @since 1.0
 */
final class ReactfxUtil {

    private ReactfxUtil() {

    }


    public static <E, F> List<F> lazyMappedView(
        List<? extends E> source,
        Function<? super E, ? extends F> f) {
        return new AbstractList<F>() {

            private List<F> cache = new ArrayList<>(Collections.nCopies(source.size(), null));


            @Override
            public F get(int index) {
                if (cache.get(index) == null) {
                    cache.set(index, f.apply(source.get(index)));
                }
                return cache.get(index);
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

}
