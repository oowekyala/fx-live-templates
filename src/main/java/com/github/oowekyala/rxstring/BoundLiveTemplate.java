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
final class BoundLiveTemplate<D> extends ValBase<String> {

    // Represents the offsets of each outer binding
    private final int[] myOuterOffsets;
    // Represents the items of the individual sequences. The
    // shifts incurred by insertions into the
    // middle of a bound observable list. The items to the right
    // are shifted right once, which affects their innerIdx
    // These are local to each sequence
    private final List<List<ValIdx>> mySequences;

    private final Subscription myCurCtxSubscription;
    private final StringBuffer myStringBuffer;
    private final EventSource<?> invalidator = new EventSource<>();
    private final List<ReplaceHandler> myUserHandler;
    private final List<ReplaceHandler> myInternalReplaceHandlers;
    private final boolean isInitialized;


    BoundLiveTemplate(D dataContext,
                      List<BindingExtractor<D>> bindings,
                      List<ReplaceHandler> userReplaceHandler,
                      List<ReplaceHandler> internalReplaceHandlers) {
        Objects.requireNonNull(dataContext);

        // the size of these is absolutely constant
        this.myOuterOffsets = new int[bindings.size()];
        this.mySequences = new ArrayList<>(Collections.nCopies(bindings.size(), null));

        this.myStringBuffer = new StringBuffer();
        this.myInternalReplaceHandlers = internalReplaceHandlers;
        this.myUserHandler = userReplaceHandler;

        Subscription subscription = () -> {};

        for (int i = 0; i < myOuterOffsets.length; i++) {
            subscription = subscription.and(
                initBinding(dataContext, bindings.get(i), i)
            );
        }

        this.isInitialized = true;
        this.myCurCtxSubscription = subscription;

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



    private void handleContentChange(ValIdx valIdx, int start, int end, String value) {
        myStringBuffer.replace(start, end, value);

        // propagate the change to the templates that contain this one
        myInternalReplaceHandlers.forEach(h -> h.replace(start, end, value));

        valIdx.propagateOffsetShift(value.length() - (end - start));

        if (isInitialized) {
            invalidator.push(null);
            myUserHandler.forEach(h -> safeHandlerCall(h, start, end, value));
        }
    }


    @SuppressWarnings("unchecked")
    private Subscription initBinding(D context, BindingExtractor<D> bindingExtractor, int outerIdx) {
        myOuterOffsets[outerIdx] = myStringBuffer.length();

        LiveList<Val<String>> lst = bindingExtractor.extract(context);
        mySequences.set(outerIdx, new ArrayList<>(lst.size()));

        return Subscription.dynamic(lst, (elt, innerIdx) -> initVal(bindingExtractor, elt, outerIdx, innerIdx));
    }


    /**
     * Initialises a single Val at the given indices.
     */
    private Subscription initVal(BindingExtractor<D> origin, Val<String> stringSource, int outerIdx, int innerIdx) {

        // sequence bindings will call this method when their content has changed
        // so we must take care of the offsets

        String initialValue = Objects.toString(stringSource.getValue());
        ValIdx valIdx = insertBindingAt(outerIdx, innerIdx, initialValue.length());

        int abs = valIdx.currentAbsoluteOffset();
        handleContentChange(valIdx, abs, abs , initialValue);

        return origin.bindSingleVal(stringSource,
                                    valIdx::currentAbsoluteOffset,
                                    (start, end, value) -> handleContentChange(valIdx, start, end, value))
                     // part of the subscription
                     .and(() -> deleteBindingAt(valIdx));
    }




    private void deleteBindingAt(ValIdx idx) {

        int start = idx.currentAbsoluteOffset();
        int deletedLength = idx.length();
        handleContentChange(idx, start, start + deletedLength, "");

        idx.delete();
    }


    private ValIdx insertBindingAt(int outerIdx, int innerIdx, int insertedLength) {
        ValIdx valIdx = new ValIdx(myOuterOffsets, outerIdx, innerIdx, mySequences.get(outerIdx), insertedLength, !isInitialized);

        if (!isInitialized) {
            // initialisation pass is special for now
            valIdx.relativeOffset = myStringBuffer.length() - myOuterOffsets[outerIdx];
            return valIdx;
        }

        return valIdx;
    }



    private static void safeHandlerCall(ReplaceHandler h, int start, int end, String value) {
        try {
            h.replace(start, end, value);
        } catch (Exception e) {
            LiveTemplate.LOGGER.log(Level.WARNING, e, () -> "An exception was thrown by an external replacement handler");
        }
    }
}
