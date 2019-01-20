package com.github.oowekyala.rxstring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.reactfx.EventSource;
import org.reactfx.Subscription;
import org.reactfx.value.Val;
import org.reactfx.value.ValBase;

import com.github.oowekyala.rxstring.diff_match_patch.Patch;
import javafx.collections.transformation.FilteredList;


/**
 * The actual implementation of a live template, bound to a known data context.
 * This only manages to sequences. Other bindings are mapped to a sequence of
 * length 1.
 *
 * Basically this class works as follows:
 * * Conceptually, it presents a List[List[Val[String]]]
 * * The outer list has fixed length, each item corresponds to one binding from the builder
 * * Each inner list can have different lengths. Only SeqBindings have length > 1
 * * We keep track of the text offsets of each inner list in myOuterOffsets
 * * Additionally, each inner list keeps track of the text offsets of its individual elements
 * relative to the start of the list in the document. This is tracked with {@link ValIdx} because since
 * elements may be inserted or removed they're hard to keep tabs on without reifying them
 * * When a Val changes (say in the inner list [i][j]), the absolute offset in the
 * document where we want to perform the replacement is myOuterOffsets[i] + mySequences[i][j].
 * Then we have to propagate the length difference to all indices to the right of it (we only need
 * to update the rest of the inner sequence + the rest of the outer table).
 * * When an item is removed or added from/into a sequence its following siblings are shifted as well
 *
 * @author Clément Fournier
 * @since 1.0
 */
final class BoundLiveTemplate<D> extends ValBase<String> {

    // Represents the offsets of each outer binding
    // this is shared with all the ValIdx spawned by this object
    private final int[] myOuterOffsets;
    // Represents the items of the individual sequences. The
    // shifts incurred by insertions into the
    // middle of a bound observable list. The items to the right
    // are shifted right once, which affects their innerIdx
    // These are local to each sequence
    private final List<List<ValIdx>> mySequences;

    private final Subscription myCurCtxSubscription;
    private final StringBuffer myStringBuffer;
    private final EventSource<?> myInvalidations = new EventSource<>();
    private final List<ReplaceHandler> myUserHandler;
    private final List<ReplaceHandler> myInternalReplaceHandlers;
    /** The template that spawned this bound template. */
    private final LiveTemplate<D> myParent;
    private boolean isPushInvalidations;


    BoundLiveTemplate(D dataContext,
                      LiveTemplate<D> parent,
                      List<BindingExtractor<D>> bindings,
                      List<ReplaceHandler> userReplaceHandler,
                      List<ReplaceHandler> internalReplaceHandlers) {

        Objects.requireNonNull(dataContext);

        this.myParent = parent;

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

        this.isPushInvalidations = true;
        this.myCurCtxSubscription = subscription;

        myUserHandler.stream().map(ReplaceHandler::unfailing).forEach(h -> h.replace(0, 0, myStringBuffer.toString()));
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
        int myLength = myStringBuffer.length();
        // notify user subscribers that everything was deleted
        myUserHandler.stream().map(ReplaceHandler::unfailing).forEach(h -> h.replace(0, myLength, ""));
        // notify parent that the sub template was deleted but only once
        myInternalReplaceHandlers.forEach(h -> h.replace(0, myLength, ""));

        isPushInvalidations = false; // avoid pushing every intermediary state as a value
        myCurCtxSubscription.unsubscribe();
    }


    @Override
    protected Subscription connect() {
        return myInvalidations.subscribe(any -> invalidate());
    }


    @Override
    protected String computeValue() {
        return myStringBuffer.toString();
    }
    // for construction


    // test only
    long totalSubscriptions() {
        return mySequences.stream().mapToLong(List::size).sum();
    }


    private ReplacementStrategy getReplacementStrategy(int start, int end, String value) {
        if (myParent.isUseDiffMatchPatchStrategy()) {
            DiffMatchPatchWithHooks dmp = new DiffMatchPatchWithHooks();
            String prevSlice = myStringBuffer.substring(start, end);

            // the ordering of the unfailing() calls affects output line numbers and stuff

            if (!prevSlice.isEmpty() && !value.isEmpty()) {
                LinkedList<Patch> patches = dmp.patchMake(prevSlice, value);
                return (base, canFail) -> dmp.patchApply(patches, prevSlice, base.withOffset(start).unfailing(canFail));
            } // else return the normal callback, bc the replace is trivial
        }

        return (base, canFail) -> base.unfailing(canFail).replace(start, end, value);
    }


    private void handleContentChange(ValIdx valIdx, int start, int end, String value) {
        if (start == end && value.isEmpty()) {
            // don't fire an event for nothing
            return;
        }

        ReplacementStrategy replacementStrategy = getReplacementStrategy(start, end, value);

        replacementStrategy.apply(myStringBuffer::replace, false);

        valIdx.propagateOffsetShift(value.length() - (end - start));

        if (isPushInvalidations) {
            // propagate the change to the templates that contain this one
            myInternalReplaceHandlers.forEach(h -> replacementStrategy.apply(h, false));
            myInvalidations.push(null);
            myUserHandler.forEach(h -> replacementStrategy.apply(h, true));
        }
    }


    private boolean isIgnorable(Val<String> val) {
        return ReactfxUtil.isConst(val) && (val.isEmpty() || val.getValue().isEmpty());
    }


    private Subscription initBinding(D context, BindingExtractor<D> bindingExtractor, int outerIdx) {
        myOuterOffsets[outerIdx] = myStringBuffer.length();

        FilteredList<Val<String>> lst = bindingExtractor.extract(context).filtered(v -> !isIgnorable(v));
        mySequences.set(outerIdx, new ArrayList<>(lst.size()));

        return ReactfxUtil.dynamic(lst, (elt, innerIdx) -> initVal(bindingExtractor, elt, outerIdx, innerIdx));
    }


    /**
     * Initialises a single Val in a sequence at the given indices.
     */
    private Subscription initVal(BindingExtractor<D> origin, Val<String> stringSource, int outerIdx, int innerIdx) {
        // sequence bindings will call this method when their content has changed

        // this thing is captured which allows its indices to remain up to date
        ValIdx valIdx = insertBindingAt(outerIdx, innerIdx);
        String initialValue = stringSource.getValue();
        int abs = valIdx.currentAbsoluteOffset();

        handleContentChange(valIdx, abs, abs, initialValue == null ? "" : initialValue);

        return origin.bindSingleVal(myParent,
                                    stringSource,
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


    private ValIdx insertBindingAt(int outerIdx, int innerIdx) {
        return new ValIdx(myOuterOffsets, myStringBuffer, outerIdx, innerIdx, mySequences.get(outerIdx));
    }


    @FunctionalInterface
    private interface ReplacementStrategy {
        /** Applies the given replace handler. */
        void apply(ReplaceHandler handler, boolean logExceptionsButDontFail);
    }
}
