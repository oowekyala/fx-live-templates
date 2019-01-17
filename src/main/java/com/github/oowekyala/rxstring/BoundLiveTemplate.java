package com.github.oowekyala.rxstring;

import java.util.Arrays;
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
    private final StringBuffer myStringBuffer;
    private final EventSource<?> invalidator = new EventSource<>();
    private final Val<ReplaceHandler> myUserHandler;
    private final List<ReplaceHandler> myInternalReplaceHandlers;


    BoundLiveTemplate(D dataContext,
                      Function<D, List<Val<String>>> dataBinder,
                      Val<ReplaceHandler> userReplaceHandler,
                      List<ReplaceHandler> internalReplaceHandlers) {
        Objects.requireNonNull(dataContext);

        List<Val<String>> unsubscribedVals = Objects.requireNonNull(dataBinder.apply(dataContext));
        this.myOffsets = new int[unsubscribedVals.size()];
        this.myStringBuffer = new StringBuffer();

        Subscription subscription = () -> {};

        for (int i = 0; i < myOffsets.length; i++) {
            subscription = subscription.and(
                initVal(unsubscribedVals.get(i), i)
            );
        }

        this.myCurCtxSubscription = subscription;
        this.myUserHandler = userReplaceHandler;
        this.myInternalReplaceHandlers = internalReplaceHandlers;

        myUserHandler.ifPresent(h -> h.insert(0, myStringBuffer.toString()));
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
        return myStringBuffer.toString();
    }


    @Override
    public String toString() {
        return "BoundLiveTemplate{" +
            "myStringBuffer=" + myStringBuffer +
            ", myOffsets=" + Arrays.toString(myOffsets) +
            ", myUserHandler=" + myUserHandler.getOpt() +
            ", myInternalReplaceHandlers=" + myInternalReplaceHandlers +
            '}';
    }
    // for construction


    private void propagateOffsetChange(int from, int diff) {
        if (diff == 0) {
            return;
        }
        for (int j = from + 1; j < myOffsets.length; j++) {
            myOffsets[j] += diff;
        }
    }


    private void handleContentChange(int childIdx, int start, int end, String value) {
        myStringBuffer.replace(start, end, value);

        // propagate the change to the templates that contain this one
        myInternalReplaceHandlers.forEach(h -> h.replace(start, end, value));

        propagateOffsetChange(childIdx, value.length() - (end - start));

        invalidator.push(null);
        // call last so that if it fails this object stays in a consistent state
        try {
            myUserHandler.ifPresent(h -> h.replace(start, end, value));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, e, () -> "An exception was thrown by an external replacement handler");
        }
    }


    private Subscription initVal(Val<String> source, int idx) {

        String initialValue = Objects.toString(source.getValue());

        myOffsets[idx] = myStringBuffer.length();
        myStringBuffer.append(initialValue);

        if (source instanceof LiveTemplateImpl) {

            // add a replace handler to the bound value of the child

            LiveTemplateImpl<?> subTemplate = (LiveTemplateImpl<?>) source;

            ReplaceHandler subHandler = (relativeStart, relativeEnd, value) ->
                handleContentChange(idx,
                                    // the offsets here must be offset by the start of the subtemplate
                                    myOffsets[idx] + relativeStart,
                                    myOffsets[idx] + relativeEnd,
                                    value);

            subTemplate.addInternalReplaceHandler(subHandler);

            return () -> subTemplate.removeInternalReplaceHandler(subHandler);
        }

        return source.orElseConst("null") // so that the values in changes are never null
                     .changes()
                     .subscribe(change -> {
                         int startOffset = myOffsets[idx];
                         int endOffset = myOffsets[idx] + change.getOldValue().length();

                         handleContentChange(idx, startOffset, endOffset, change.getNewValue());
                     });
    }


}
