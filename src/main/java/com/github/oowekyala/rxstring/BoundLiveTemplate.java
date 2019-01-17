package com.github.oowekyala.rxstring;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.reactfx.EventSource;
import org.reactfx.EventStream;
import org.reactfx.Subscription;
import org.reactfx.value.Val;
import org.reactfx.value.ValBase;


/**
 * Template bound to a data context.
 *
 * @author Clément Fournier
 * @since 1.0
 */
class BoundLiveTemplate<D> extends ValBase<String> {

    private final int[] myOffsets;
    private final Subscription myCurCtxSubscription;
    private final StringBuilder myStringBuilder;
    private final EventSource<ReplaceEvent> myReplaceEvents = new EventSource<>();


    BoundLiveTemplate(D dataContext, Function<D, List<Val<String>>> dataBinder) {
        Objects.requireNonNull(dataContext);

        List<Val<String>> unsubscribedVals = Objects.requireNonNull(dataBinder.apply(dataContext));
        this.myOffsets = new int[unsubscribedVals.size()];
        this.myStringBuilder = new StringBuilder();

        Subscription subscription = () -> {};

        int curOffset = 0;
        for (int i = 0; i < myOffsets.length; i++) {
            myOffsets[i] = curOffset;

            subscription = subscription.and(
                initVal(unsubscribedVals.get(i), i)
            );
        }

        this.myCurCtxSubscription = subscription;
    }


    void unbind() {
        myCurCtxSubscription.unsubscribe();
    }


    EventStream<ReplaceEvent> replaceEvents() {
        return myReplaceEvents;
    }


    @Override
    protected Subscription connect() {
        return myReplaceEvents.subscribe(any -> invalidate());
    }


    @Override
    protected String computeValue() {
        return myStringBuilder.toString();
    }

    // for construction


    private String safe(String s) {
        return Objects.toString(s);
    }

    private Subscription initVal(Val<String> source, int idx) {

        String initialValue = safe(source.getValue());

        myOffsets[idx] = myStringBuilder.length();
        myStringBuilder.append(initialValue);

        return source.changes()
                     .subscribe(change -> {

                         String oldVal = safe(change.getOldValue());
                         String newVal = safe(change.getNewValue());

                         ReplaceEvent event = new ReplaceEvent(myOffsets[idx], myOffsets[idx] + oldVal.length(), newVal);

                         myStringBuilder.replace(event.getStartIndex(), event.getEndIndex(), event.getValue());

                         int diff = oldVal.length() - newVal.length();

                         for (int j = idx + 1; j < myOffsets.length; j++) {
                             myOffsets[j] += diff;
                         }

                         myReplaceEvents.push(event);

                     });
    }


}
