package com.github.oowekyala.rxstring;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.reactfx.EventSource;
import org.reactfx.Subscription;
import org.reactfx.value.Val;
import org.reactfx.value.ValBase;


/**
 * @author Clément Fournier
 * @since 1.0
 */
class BoundLiveTemplate<D> extends ValBase<String> {

    private final int[] myOffsets;
    private final Subscription myCurCtxSubscription;
    private final StringBuilder myStringBuilder;
    private final EventSource<?> invalidator = new EventSource<>();
    private final Val<ReplaceHandler> myReplaceHandler;



    BoundLiveTemplate(D dataContext, Function<D, List<Val<String>>> dataBinder, Val<ReplaceHandler> replaceHandler) {
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
        this.myReplaceHandler = replaceHandler.map(this::wrapUserHandler).orElseConst(myStringBuilder::replace);
    }


    void unbind() {
        myCurCtxSubscription.unsubscribe();
    }


    @Override
    protected Subscription connect() {
        return invalidator.subscribe(any -> invalidate());
    }


    @Override
    protected String computeValue() {
        return myStringBuilder.toString();
    }

    // for construction


    private String safe(String s) {
        return Objects.toString(s);
    }


    private ReplaceHandler wrapUserHandler(ReplaceHandler handler) {
        return (start, end, value) -> {
            myStringBuilder.replace(start, end, value);

            if (handler != null) {
                handler.replace(start, end, value);
            }
        };
    }


    private Subscription initVal(Val<String> source, int idx) {

        String initialValue = safe(source.getValue());

        myOffsets[idx] = myStringBuilder.length();
        myStringBuilder.append(initialValue);

        return source.changes()
                     .subscribe(change -> {

                         String oldVal = safe(change.getOldValue());
                         String newVal = safe(change.getNewValue());

                         myReplaceHandler.getValue().replace(myOffsets[idx], myOffsets[idx] + oldVal.length(), newVal);

                         int diff = oldVal.length() - newVal.length();

                         for (int j = idx + 1; j < myOffsets.length; j++) {
                             myOffsets[j] += diff;
                         }

                         invalidator.push(null);

                     });
    }


}
