package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
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

        Subscription subscription = () -> {};

        for (int i = 0; i < myOuterOffsets.length; i++) {
            subscription = subscription.and(
                initBinding(dataContext, bindings.get(i), i)
            );
        }

        this.isInitialized = true;
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


    private void propagateOffsetChange(ValIdx valIdx, int diff) {
        if (diff == 0) {
            return;
        }

        // the relative change is propagated to all elements right of this one in the inner table
        valIdx.propagateRight(idx -> idx.relativeOffset += diff);

        // and to all outer indices right of this one
        // the inner indices of the other sequences need not be touched
        for (int j = valIdx.outerIdx + 1; j < myOuterOffsets.length; j++) {
            myOuterOffsets[j] += diff;
        }
    }


    private void handleContentChange(ValIdx valIdx, int start, int end, String value) {
        myStringBuffer.replace(start, end, value);

        // propagate the change to the templates that contain this one
        myInternalReplaceHandlers.forEach(h -> h.replace(start, end, value));

        propagateOffsetChange(valIdx, value.length() - (end - start));

        invalidator.push(null);
        myUserHandler.forEach(h -> safeHandlerCall(h, start, end, value));

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

        // TODO use handleContentChange? very probably
        myStringBuffer.insert(valIdx.currentAbsoluteOffset(), initialValue);

        return origin.bindSingleVal(stringSource,
                                    valIdx::currentAbsoluteOffset,
                                    (start, end, value) -> handleContentChange(valIdx, start, end, value))
                     // part of the subscription
                     .and(() -> deleteBindingAt(valIdx));
    }


    private void propagateItemShift(ValIdx from, int shift) {
        from.propagateRight(idx -> idx.shiftRightInSeq(shift));
    }


    private void deleteBindingAt(ValIdx idx) {

        int start = idx.currentAbsoluteOffset();
        int deletedLength = sizeOf(idx);
        handleContentChange(idx, start, start + deletedLength, "");

        propagateItemShift(idx, -1);
        idx.delete();
    }


    private ValIdx insertBindingAt(int outerIdx, int innerIdx, int insertedLength) {
        ValIdx valIdx = new ValIdx(outerIdx, innerIdx, mySequences.get(outerIdx));

        if (!isInitialized) {
            // initialisation pass is special for now
            valIdx.relativeOffset = myStringBuffer.length() - myOuterOffsets[outerIdx];
            return valIdx;
        }

        // length difference between prev and current to propagate right
        int diff;
        if (valIdx.innerIdx >= valIdx.parent.size()) {
            assert valIdx.innerIdx == valIdx.parent.size();
            valIdx.relativeOffset = valIdx.innerIdx == 0 ? 0 : valIdx.left().relativeOffset + sizeOf(valIdx.left());
            // the siblings are affected, but not this valIdx
            propagateItemShift(valIdx, +1);
            diff = insertedLength;
        } else {
            // overwrite something else, already unbound
            diff = sizeOf(valIdx) - insertedLength;
        }

        propagateOffsetChange(valIdx, diff);

        return valIdx;
    }


    /** Gets the size in characters of the sequence element at the specified position. */
    private int sizeOf(ValIdx idx) {

        if (idx.innerIdx < idx.siblings().size() - 1) {
            // not the last
            return idx.right().relativeOffset - idx.relativeOffset;
        } else {
            return myOuterOffsets[idx.outerIdx + 1] - idx.currentAbsoluteOffset();
        }
    }

    private static void safeHandlerCall(ReplaceHandler h, int start, int end, String value) {
        try {
            h.replace(start, end, value);
        } catch (Exception e) {
            LiveTemplate.LOGGER.log(Level.WARNING, e, () -> "An exception was thrown by an external replacement handler");
        }
    }


    private class ValIdx implements Comparable<ValIdx> {

        final int outerIdx;
        final List<ValIdx> parent;
        int innerIdx;
        int relativeOffset;


        private ValIdx(int outerIdx,
                       int innerIdx,
                       List<ValIdx> parent) {
            this.outerIdx = outerIdx;
            this.innerIdx = innerIdx;
            this.parent = parent;

            if (innerIdx >= parent.size()) {
                parent.add(this);
            } else {
                parent.set(innerIdx, this);
            }
        }


        List<ValIdx> siblings() {
            return parent;
        }


        ValIdx left() {
            return innerIdx == 0 ? null : siblings().get(innerIdx - 1);
        }


        ValIdx right() {
            List<ValIdx> siblings = siblings();
            return innerIdx + 1 >= siblings.size() ? null : siblings.get(innerIdx + 1);
        }


        void propagateRight(Consumer<ValIdx> idxConsumer) {
            for (int j = currentSeqIdx() + 1; j < parent.size(); j++) {
                idxConsumer.accept(parent.get(j));
            }
        }


        void delete() {
            siblings().remove(currentSeqIdx());
        }


        int currentSeqIdx() {
            return innerIdx;
        }


        int currentAbsoluteOffset() {
            return myOuterOffsets[outerIdx] + relativeOffset;
        }


        void shiftRightInSeq(int shift) {
            innerIdx += shift;
        }


        @Override
        public int compareTo(BoundLiveTemplate.ValIdx o) {
            return Integer.compare(currentSeqIdx(), o.currentSeqIdx());
        }


    }


}
