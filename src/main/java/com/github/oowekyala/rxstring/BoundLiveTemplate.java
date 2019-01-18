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

import com.github.oowekyala.rxstring.BindingExtractor.SeqBinding;


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
    private final List<List<Subscription>> mySequenceSubs;

    private final Subscription myCurCtxSubscription;
    private final StringBuffer myStringBuffer;
    private final EventSource<?> invalidator = new EventSource<>();
    private final List<ReplaceHandler> myUserHandler;
    private final List<ReplaceHandler> myInternalReplaceHandlers;


    BoundLiveTemplate(D dataContext,
                      List<BindingExtractor<D, ?>> bindings,
                      List<ReplaceHandler> userReplaceHandler,
                      List<ReplaceHandler> internalReplaceHandlers) {
        Objects.requireNonNull(dataContext);

        // the size of these is absolutely constant
        this.myOuterOffsets = new int[bindings.size()];
        this.mySequenceOffsets = new ArrayList<>(Collections.nCopies(bindings.size(), null));
        this.mySequenceSubs = new ArrayList<>(Collections.nCopies(bindings.size(), null));

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
    private Subscription initBinding(D context, BindingExtractor<D, ?> bindingExtractor, int outerIdx) {
        myOuterOffsets[outerIdx] = myStringBuffer.length();

        if (bindingExtractor instanceof BindingExtractor.ValExtractor
            || bindingExtractor instanceof BindingExtractor.TemplateBinding) {
            mySequenceOffsets.set(outerIdx, Collections.singletonList(0));
            return initVal((Val<String>) bindingExtractor.apply(context), outerIdx, 0);
        } else if (bindingExtractor instanceof BindingExtractor.SeqBinding) {
            return initSeqBinding(context, (SeqBinding<D, ?>) bindingExtractor, outerIdx);
        }

        throw new IllegalStateException("Unhandled binding extractor of " + bindingExtractor.getClass());

    }


    private <T> Subscription initSeqBinding(D ctx, SeqBinding<D, T> seqBinding, int outerIdx) {
        LiveList<Val<String>> lst = seqBinding.apply(ctx);
        mySequenceOffsets.set(outerIdx, new ArrayList<>(lst.size() * 2));

        return Subscription.dynamic(lst, (elt, innerIdx) -> initVal(elt, outerIdx, innerIdx));
    }


    private Subscription initVal(Val<String> source, int outerIdx, int innerIdx) {

        String initialValue = Objects.toString(source.getValue());

        myStringBuffer.append(initialValue);

        if (source instanceof LiveTemplateImpl) {
            return initTemplateBinding((LiveTemplateImpl<?>) source, outerIdx, innerIdx);
        } else {
            // constant and Val bindings go here

            return source.orElseConst("null") // so that the values in changes are never null
                         .changes()
                         .subscribe(change -> {
                             int startOffset = absoluteOffset(outerIdx, innerIdx);
                             int endOffset = startOffset + change.getOldValue().length();

                             handleContentChange(outerIdx, innerIdx, startOffset, endOffset, change.getNewValue());
                         });
        }
    }


    private int absoluteOffset(int outerIdx, int innerIdx) {
        return myOuterOffsets[outerIdx] + mySequenceOffsets.get(outerIdx).get(innerIdx);
    }


    private <Sub> Subscription initTemplateBinding(LiveTemplateImpl<Sub> subTemplate, int outerIdx, int innerIdx) {

        // add a replace handler to the bound value of the child

        ReplaceHandler subHandler = (relativeStart, relativeEnd, value) -> {
            int absoluteOffset = absoluteOffset(outerIdx, innerIdx);

            handleContentChange(outerIdx, outerIdx,
                                // the offsets here must be offset by the start of the subtemplate
                                absoluteOffset + relativeStart,
                                absoluteOffset + relativeEnd,
                                value);
        };

        subTemplate.addInternalReplaceHandler(subHandler);

        return () -> subTemplate.removeInternalReplaceHandler(subHandler);
    }


    private static void safeHandlerCall(ReplaceHandler h, int start, int end, String value) {
        try {
            h.replace(start, end, value);
        } catch (Exception e) {
            LiveTemplate.LOGGER.log(Level.WARNING, e, () -> "An exception was thrown by an external replacement handler");
        }
    }


}
