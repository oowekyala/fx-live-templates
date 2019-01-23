package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.reactfx.Subscription;
import org.reactfx.collection.LiveList;
import org.reactfx.collection.LiveListBase;
import org.reactfx.collection.QuasiListChange;
import org.reactfx.collection.QuasiListModification;
import org.reactfx.collection.UnmodifiableByDefaultLiveList;

import javafx.collections.ObservableList;


/**
 * A list view that pretends its elements are separated by delimiters, and that the
 * first and last are a special prefix and suffix.
 *
 * @author Clément Fournier
 * @since 1.0
 */
class DelimitedListView<E> extends LiveListBase<E> implements UnmodifiableByDefaultLiveList<E> {

    private static final int UNINITIALIZED = -6;
    private final ObservableList<? extends E> source;
    private final E delimiter;
    private final E prefix;
    private final E suffix;


    DelimitedListView(ObservableList<? extends E> source, E delimiter, E prefix, E suffix) {
        this.source = source;
        this.delimiter = delimiter;
        this.prefix = prefix;
        this.suffix = suffix;
    }


    @Override
    @SuppressWarnings("Duplicates")

    protected Subscription observeInputs() {
        return LiveList.<E>observeQuasiChanges(source, ch -> notifyObservers(mappedChangeView(ch)));
    }


    private QuasiListChange<E> mappedChangeView(
        QuasiListChange<? extends E> change) {
        return () -> {
            List<? extends QuasiListModification<? extends E>> mods = change.getModifications();
            return ReactfxExtensions.lazyMappedView(mods, mod -> new QuasiListModification<E>() {

                private List<E> myRemoved;
                private int myTo = UNINITIALIZED;


                @Override
                public int getFrom() {
                    int from = mod.getFrom();
                    if (from == 0) {
                        // we started from the first element, there's no delimiter before it
                        return 1;
                    } else {
                        // consider the change to originate from the delimiter preceding it
                        return sourceIdxToThisIdx(from) - 1;
                    }
                }


                @Override
                public int getTo() {
                    if (myTo != UNINITIALIZED) {
                        return myTo;
                    }

                    int removedSize = getRemovedSize();
                    int addedSize = getAddedSize();
                    int from = getFrom();
                    if (removedSize > 0 && addedSize == 0) {
                        // removal
                        myTo = from;
                        return myTo;
                    }
                    int sourceSize = source.size();

                    if (removedSize == 0) {
                        boolean isAddingToEmptyList = addedSize > 0 && sourceSize == mod.getAddedSize();
                        if (from == 1 && !isAddingToEmptyList) {
                            // there is a delimiter after in this case
                            // [a,b] -> [c,a,b] : c,     (0,1) => (1,3)
                            // [a]   -> [c,b,a] : c,b,   (0,2) => (1,5)
                            // [a,b]   -> [c,b] : c,b,   (0,2) => (1,5)
                            myTo = sourceIdxToThisIdx(mod.getTo());
                            return myTo;
                        }
                    }
                    // no delimiter after
                    // []    -> [c]     : c      (0,1) => (1,2)
                    // []    -> [a,b]   : a,b    (0,2) => (1,4)
                    // [a,b] -> [a,b,c] : ,c     (2,3) => (4,6)
                    myTo = sourceIdxToThisIdx(mod.getTo()) - 1;
                    return myTo;
                }


                @Override
                public int getAddedSize() {
                    // 0 -> 0
                    // 1 -> 1
                    // 2 -> 3
                    int added = mod.getAddedSize();
                    if (added == 0) {
                        return 0;
                    } else if (added == 1 && source.size() == 1) {
                        // [] -> [a]
                        return 1;
                    } else {
                        // count the delimiter in
                        // [a] -> [a,b]
                        // [] -> [a,b]
                        // [a,b] -> [a,b,c]
                        return added * 2 - 1;
                    }
                }


                @Override
                public List<? extends E> getRemoved() {
                    if (myRemoved == null) {
                        List<? extends E> removed = mod.getRemoved();
                        if (removed.size() > 0) {
                            List<E> all = new ArrayList<>();
                            for (E e : removed) {
                                all.add(delimiter);
                                all.add(e);
                            }
                            if (getFrom() == 1) {
                                all.remove(0);
                                if (source.size() == 1 && mod.getTo() != 1) {
                                    // [a,b] -> [b]
                                    // but not [a] -> [b]
                                    all.add(delimiter);
                                }
                            }
                            myRemoved = Collections.unmodifiableList(all);
                        } else {
                            myRemoved = Collections.emptyList();
                        }
                    }
                    return myRemoved;
                }
            });
        };
    }


    @Override
    public int size() {
        int base = source.size();
        return base == 0 ? 2 : 1 + 2 * source.size();
    }


    private int thisIdxToSourceIdx(int index) {
        return (index - 1) / 2;
    }


    private int sourceIdxToThisIdx(int index) {
        return (index * 2) + 1;
    }


    @Override
    public E get(int index) {
        int size = size();
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        } else if (index == 0) {
            return prefix;
        } else if (index == size - 1) {
            return suffix;
        } else {
            // every *even* position in this list is a delimiter, excluding first and last
            // [a,b]
            // [a,b,c]
            // []
            return index % 2 == 0 ? delimiter
                                  : source.get(thisIdxToSourceIdx(index));
        }
    }
}
