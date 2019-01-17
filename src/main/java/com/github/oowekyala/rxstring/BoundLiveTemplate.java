package com.github.oowekyala.rxstring;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.reactfx.EventSource;
import org.reactfx.Subscription;
import org.reactfx.value.Val;
import org.reactfx.value.ValBase;


/**
 * @author Clément Fournier
 * @since 1.0
 */
class BoundLiveTemplate<D> extends ValBase<String> {

    private static final Logger LOG = Logger.getLogger(LiveTemplate.class.getName());

    private final int[] myOffsets;
    private final Subscription myCurCtxSubscription;
    private final StringBuilder myStringBuilder;
    private final EventSource<?> invalidator = new EventSource<>();
    private final Val<ReplaceHandler> myUserHandler;


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
        this.myUserHandler = replaceHandler;

        myUserHandler.ifPresent(h -> h.insert(0, myStringBuilder.toString()));
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


    private Subscription initVal(Val<String> source, int idx) {

        String initialValue = Objects.toString(source.getValue());

        myOffsets[idx] = myStringBuilder.length();
        myStringBuilder.append(initialValue);

        return source.orElseConst("null") // so that the values in changes are never null
                     .changes()
                     .subscribe(change -> {

                         String oldVal = change.getOldValue();
                         String newVal = change.getNewValue();

                         int startOffset = myOffsets[idx];
                         int endOffset = myOffsets[idx] + oldVal.length();

                         myStringBuilder.replace(startOffset, endOffset, newVal);

                         int diff = oldVal.length() - newVal.length();

                         // propagate offset changes to the right
                         for (int j = idx + 1; j < myOffsets.length; j++) {
                             myOffsets[j] += diff;
                         }

                         invalidator.push(null);
                         // call last so that if it fails this object stays in a consistent state
                         try {
                             myUserHandler.ifPresent(h -> h.replace(startOffset, endOffset, newVal));
                         } catch (Exception e) {
                             LOG.log(Level.SEVERE, e, () -> "An exception was thrown by an external replacement handler");
                         }
                     });
    }


}
