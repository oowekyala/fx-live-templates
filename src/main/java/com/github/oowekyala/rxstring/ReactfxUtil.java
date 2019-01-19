package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.reactfx.EventStreams;
import org.reactfx.Subscription;

import javafx.collections.ObservableList;


/**
 * Ersatz until reactfx merges my PR.
 *
 * @author Clément Fournier
 * @since 1.0
 */
public final class ReactfxUtil {

    private ReactfxUtil() {

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
                        int i = ch.getFrom();
                        for (T ignored : ch.getRemoved()) {
                            elemSubs.remove(i).unsubscribe();
                            i++;
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
