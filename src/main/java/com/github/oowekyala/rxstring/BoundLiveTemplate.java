package com.github.oowekyala.rxstring;

import static com.github.oowekyala.rxstring.BindingExtractor.ConstantBinding;

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
import org.reactfx.value.Var;

import com.github.oowekyala.rxstring.ReactfxUtil.RebindSubscription;
import com.github.oowekyala.rxstring.diff_match_patch.Patch;
import javafx.collections.ObservableList;


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
    // indices of the constant bindings, which don't need to be rebound
    // those are 1-to-1 with myOuterOffsets
    private final boolean[] myConstantIndices;
    /** The bindings that specify this template. Used for rebinding. */
    private final List<BindingExtractor<D>> myBindings;
    /** One subscription for each sequence. Rebound when rebinding the template. */
    private final List<RebindSubscription<ObservableList<Val<String>>>> mySequenceSubscriptions;

    private final StringBuffer myStringBuffer;
    private final EventSource<?> myInvalidations = new EventSource<>();
    private final Handlers myReplaceHandlers;
    /** The template that spawned this bound template. */
    private final LiveTemplate<D> myParent;
    /**
     * Whether to push invalidations to the parent template.
     * False while initializing and destroying.
     */
    private boolean isPushInvalidations;
    private String myIndent = ">";


    BoundLiveTemplate(D dataContext,
                      LiveTemplate<D> parent,
                      List<BindingExtractor<D>> bindings,
                      List<ReplaceHandler> userReplaceHandlers,
                      Var<ReplaceHandler> parentReplaceCallback) {

        Objects.requireNonNull(dataContext);

        this.myParent = parent;

        // the size of these is absolutely constant
        this.myOuterOffsets = new int[bindings.size()];
        this.myConstantIndices = new boolean[bindings.size()];
        this.mySequences = new ArrayList<>(Collections.nCopies(bindings.size(), null));
        this.mySequenceSubscriptions = new ArrayList<>(Collections.nCopies(bindings.size(), null));
        this.myBindings = bindings;

        this.myStringBuffer = new StringBuffer();
        this.myReplaceHandlers = new Handlers(userReplaceHandlers, parentReplaceCallback);

        bindTo(dataContext, false);
        this.isPushInvalidations = true;

        myReplaceHandlers.notifyListenersOfReplace(ReplacementStrategy.replacing(0, 0, myStringBuffer.toString()));
    }


    @Override
    public String toString() {
        return "BoundLiveTemplate{" +
            "myStringBuffer=" + myStringBuffer +
            ", myOuterOffsets=" + Arrays.toString(myOuterOffsets) +
            '}';
    }


    void unbind() {
        // save the length, because unsubscribing will change it
        int myLength = myStringBuffer.length();
        isPushInvalidations = false; // avoid pushing every intermediary state as a value
        mySequenceSubscriptions.forEach(Subscription::unsubscribe);
        // notify everyone that the template was deleted but only once
        myReplaceHandlers.notifyListenersOfReplace(ReplacementStrategy.replacing(0, myLength, ""));

    }


    /**
     * Rebinds this bound template to a new data context.
     *
     * @param dataContext New data context
     */
    void rebind(D dataContext) {
        bindTo(dataContext, true);
    }


    private void bindTo(D dataContext, boolean isRebind) {
        for (int i = 0; i < myOuterOffsets.length; i++) {
            // only reevaluate the thing if it's not constant
            if (!myConstantIndices[i]) {
                mySequenceSubscriptions.set(i, initSequence(dataContext, myBindings.get(i), i, isRebind));
            }
        }
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
        String prevSlice = myStringBuffer.substring(start, end);
        if (prevSlice.equals(value)) {
            return (base, canFail) -> {
                // nothing to replace
            };
        }

        if (myParent.isUseDiffMatchPatchStrategy()) {
            DiffMatchPatchWithHooks dmp = new DiffMatchPatchWithHooks();

            // the unfailing() call must be placed after withOffset

            if (!prevSlice.isEmpty() && !value.isEmpty()) {
                LinkedList<Patch> patches = dmp.patchMake(prevSlice, value);
                return (base, canFail) -> dmp.patchApply(patches, prevSlice, base.withOffset(start).unfailing(canFail));
            } // else return the normal callback, bc the replace is trivial (full deletion | full insertion)
        }

        return (base, canFail) -> base.unfailing(canFail).replace(start, end, value);
    }


    /**
     * The main function that replaces text and notifies listeners. It's a {@link ReplaceHandler}.
     */
    private void handleContentChange(int start, int end, String value) {
        if (start == end && value.isEmpty()) {
            // don't fire an event for nothing
            return;
        }

        ReplacementStrategy replacementStrategy = getReplacementStrategy(start, end, value);

        replacementStrategy.apply(myStringBuffer::replace, false);

        if (isPushInvalidations) {
            // propagate the change to the templates that contain this one
            myReplaceHandlers.notifyListenersOfReplace(replacementStrategy);
            // invalidate the value of this Val, reflected on the parent LiveTemplate
            myInvalidations.push(null);
        }
    }


    private boolean isIgnorable(Val<String> val) {
        return ReactfxUtil.isConst(val) && (val.isEmpty() || val.getValue().isEmpty());
    }


    /**
     * Initialises the whole sequence at index outerIdx. Returns the subscription that unsubscribes
     * all elements of the sequence.
     */
    private RebindSubscription<ObservableList<Val<String>>> initSequence(D context, BindingExtractor<D> bindingExtractor, int outerIdx, boolean isRebind) {

        if (!myConstantIndices[outerIdx] && bindingExtractor instanceof ConstantBinding) {
            // then we're initialising for the first time
            myConstantIndices[outerIdx] = true;
        }

        // whether rebind or not we have to extract the list from the up-to-date data context
        ObservableList<Val<String>> lst = bindingExtractor.extract(context).filtered(v -> !isIgnorable(v));

        if (!isRebind) {
            // if it's a rebind then those have already been initialized
            myOuterOffsets[outerIdx] = myStringBuffer.length();
            mySequences.set(outerIdx, new ArrayList<>(lst.size()));
        } else if (mySequenceSubscriptions.get(outerIdx) != null) {
            // then we're rebinding, and so we'll rebase the new list onto the current one
            // to merge existing templates
            return mySequenceSubscriptions.get(outerIdx).rebind(lst);
        }

        return ReactfxUtil.dynamicRecombine(lst, (elt, innerIdx) -> initVal(elt, outerIdx, innerIdx));
    }


    /**
     * Initialises a single Val in a sequence at the given indices.
     */
    private RebindSubscription<Val<String>> initVal(Val<String> stringSource, int outerIdx, int innerIdx) {
        // sequence bindings will call this method when their content has changed

        // this thing is captured which allows its indices to remain up to date
        ValIdx valIdx = insertBindingAt(outerIdx, innerIdx);

        return BindingExtractor.bindSingleVal(myParent, stringSource, valIdx).and(() -> deleteBindingAt(valIdx));
    }


    private ValIdx insertBindingAt(int outerIdx, int innerIdx) {
        return new ValIdx(myOuterOffsets, myStringBuffer, outerIdx, innerIdx, mySequences.get(outerIdx), this::handleContentChange);
    }


    private void deleteBindingAt(ValIdx idx) {
        if (isPushInvalidations) {
            idx.delete();
        }
    }


    /**
     * An object that encapsulates a set of patches to forward to a renderer.
     * Using a diff-match-patch strategy may forward several patches.
     */
    @FunctionalInterface
    private interface ReplacementStrategy {
        /**
         * Applies the given replace handler. The strategy captures the parameters
         * to be passed to the handler.
         */
        void apply(ReplaceHandler handler, boolean logExceptionsButDontFail);


        /** Simple single-patch strategy. */
        static ReplacementStrategy replacing(int start, int end, String value) {
            return (handler, canFail) -> handler.unfailing(canFail).replace(start, end, value);
        }
    }

    private static class Handlers {

        private final List<ReplaceHandler> myUserHandlers;
        private final Var<ReplaceHandler> myParentCallback;


        Handlers(List<ReplaceHandler> userHandlers, Var<ReplaceHandler> parentHandler) {
            this.myUserHandlers = userHandlers;
            this.myParentCallback = parentHandler;
        }


        /**
         * Notify the parent template and the user replace handlers of a replacement
         * using the given replacement strategy. The strategy encapsulates the parameters
         * to be passed to each handler. See {@link #getReplacementStrategy(int, int, String)}
         * and {@link ReplacementStrategy#replacing(int, int, String)}.
         */
        private void notifyListenersOfReplace(ReplacementStrategy strategy) {
            myParentCallback.ifPresent(h -> strategy.apply(h, false));
            if (myParentCallback.isEmpty()) {
                myUserHandlers.forEach(h -> strategy.apply(h, true));
            }
        }

    }
}
