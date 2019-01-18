package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

import org.reactfx.EventSource;
import org.reactfx.Subscription;
import org.reactfx.collection.LiveList;
import org.reactfx.value.Val;
import org.reactfx.value.ValBase;


/**
 * The actual implementation of a live template, bound to a known data context.
 *
 * @author Clément Fournier
 * @since 1.0
 */
class BoundLiveTemplate<D> extends ValBase<String> {

    // Represents the offsets of each outer binding
    private final int[] myOuterOffsets;
    // Represents the offsets associated with each component of a list binding
    // the offsets in inner arrays are relative to the offset of their outer array (in myOuterOffsets)
    private final List<List<Integer>> mySequenceOffsets;

    private final Subscription myCurCtxSubscription;
    private final StringBuffer myStringBuffer;
    private final EventSource<?> invalidator = new EventSource<>();
    private final List<ReplaceHandler> myUserHandler;
    private final List<ReplaceHandler> myInternalReplaceHandlers;


    BoundLiveTemplate(D dataContext,
                      List<BindingExtractor<D>> bindings,
                      List<ReplaceHandler> userReplaceHandler,
                      List<ReplaceHandler> internalReplaceHandlers) {
        Objects.requireNonNull(dataContext);

        // the size of these is absolutely constant
        this.myOuterOffsets = new int[bindings.size()];
        this.mySequenceOffsets = new ArrayList<>(Collections.nCopies(bindings.size(), null));

        this.myStringBuffer = new StringBuffer();

        Subscription subscription = () -> {};

        for (int i = 0; i < myOuterOffsets.length; i++) {
            subscription = subscription.and(
                initBinding(dataContext, bindings.get(i), i)
            );
        }

        this.myCurCtxSubscription = subscription;
        this.myUserHandler = userReplaceHandler;
        this.myInternalReplaceHandlers = internalReplaceHandlers;

        myUserHandler.forEach(h -> safeHandlerCall(h, 0, 0, myStringBuffer.toString()));
    }


    @Override
    public String toString() {
        return "BoundLiveTemplate{" +
            "myStringBuffer=" + myStringBuffer +
            ", myOuterOffsets=" + Arrays.toString(myOuterOffsets) +
            ", myUserHandlers=" + myUserHandler +
            ", myInternalReplaceHandlers=" + myInternalReplaceHandlers +
            '}';
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
    // for construction


    private void propagateOffsetChange(int outerIdx, int innerIdx, int diff) {
        if (diff == 0) {
            return;
        }

        List<Integer> inner = mySequenceOffsets.get(outerIdx);

        // the relative change is propagated to all elements right of this one in the inner table
        for (int j = innerIdx + 1; j < inner.size(); j++) {
            inner.set(j, inner.get(j) + diff);
        }

        // and to all outer indices right of this one
        // the inner indices of the other sequences need not be touched
        for (int j = outerIdx + 1; j < myOuterOffsets.length; j++) {
            myOuterOffsets[j] += diff;
        }
    }


    private void handleContentChange(int outerIdx, int innerIdx, int start, int end, String value) {
        myStringBuffer.replace(start, end, value);

        // propagate the change to the templates that contain this one
        myInternalReplaceHandlers.forEach(h -> h.replace(start, end, value));

        propagateOffsetChange(outerIdx, innerIdx, value.length() - (end - start));

        invalidator.push(null);
        myUserHandler.forEach(h -> safeHandlerCall(h, start, end, value));

    }


    @SuppressWarnings("unchecked")
    private Subscription initBinding(D context, BindingExtractor<D> bindingExtractor, int outerIdx) {
        myOuterOffsets[outerIdx] = myStringBuffer.length();

        LiveList<Val<String>> lst = bindingExtractor.extract(context);
        mySequenceOffsets.set(outerIdx, new ArrayList<>(Collections.nCopies(lst.size(), 0)));

        return Subscription.dynamic(lst, (elt, innerIdx) -> initVal(bindingExtractor, elt, outerIdx, innerIdx));
    }


    /**
     * Initialises a single Val at the given indices.
     */
    private Subscription initVal(BindingExtractor<D> origin, Val<String> stringSource, int outerIdx, int innerIdx) {

        String initialValue = Objects.toString(stringSource.getValue());
        myStringBuffer.append(initialValue);

        return origin.bindSingleVal(stringSource,
                                    () -> absoluteOffset(outerIdx, innerIdx),
                                    (start, end, value) -> handleContentChange(outerIdx, innerIdx, start, end, value));
    }


    private int absoluteOffset(int outerIdx, int innerIdx) {
        return myOuterOffsets[outerIdx] + mySequenceOffsets.get(outerIdx).get(innerIdx);
    }

    private static void safeHandlerCall(ReplaceHandler h, int start, int end, String value) {
        try {
            h.replace(start, end, value);
        } catch (Exception e) {
            LiveTemplate.LOGGER.log(Level.WARNING, e, () -> "An exception was thrown by an external replacement handler");
        }
    }


}
