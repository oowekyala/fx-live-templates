package com.github.oowekyala.rxstring;

import java.util.List;
import java.util.function.Consumer;


/**
 * Keeps track of the relative offset of a binding in a sequence.
 *
 * @author Clément Fournier
 * @since 1.0
 */
final class ValIdx implements Comparable<ValIdx> {

    private final StringBuffer stringBuffer;
    /** Index in the table of sequence offsets. */
    private final int outerIdx;
    /** The enclosing sequence. */
    private final List<ValIdx> parent;
    /** Replace handler for the parent template. */
    private final ReplaceHandler myParentReplaceHandler;
    /** Text offset relative to the start of the sequence. */
    private int relativeOffset;
    /** Index in the parent list. Shifted when elements are inserted to the left. */
    private int innerIdx;
    /** Outer offsets of the parent bound template. */
    private int[] myOuterOffsets;


    ValIdx(int[] myOuterOffsets,
           StringBuffer stringBuffer,
           int outerIdx,
           int innerIdx,
           List<ValIdx> parent,
           ReplaceHandler parentReplaceHandler) {

        this.stringBuffer = stringBuffer;
        this.outerIdx = outerIdx;
        this.innerIdx = innerIdx;
        this.parent = parent;
        this.myOuterOffsets = myOuterOffsets;

        this.relativeOffset = innerIdx == 0 ? 0 : left().relativeOffset + left().length();
        this.myParentReplaceHandler = parentReplaceHandler.withOffset(this::currentAbsoluteOffset);

        // you can't call left().length() after setting this node in its parent
        parent.add(innerIdx, this);
        propagateItemShift(+1);
        // The offset shift will be propagated via replaceValue
        // when inserting the initial value, so not here
        // propagateOffsetShift(initialLength);

    }


    private int length() {
        if (innerIdx + 1 < parent.size()) {
            // there's a right node
            return right().relativeOffset - relativeOffset;
        } else if (outerIdx + 1 < myOuterOffsets.length) {
            return myOuterOffsets[outerIdx + 1] - currentAbsoluteOffset();
        } else {
            return stringBuffer.length() - currentAbsoluteOffset();
        }
    }


    private ValIdx left() {
        return innerIdx == 0 ? null : parent.get(innerIdx - 1);
    }


    private ValIdx right() {
        return innerIdx + 1 < parent.size() ? parent.get(innerIdx + 1) : null;
    }


    /** Replaces all of this text range. */
    void replaceValue(String value) {
        replaceRelative(0, length(), value);
    }


    /**
     * Replaces part of the text range of this valid.
     */
    void replaceRelative(int start, int end, String value) {
        String safe = value == null ? "" : value;

        myParentReplaceHandler.replace(start, end, safe);
        propagateOffsetShift(safe.length() - (end - start));
    }


    /**
     * Deletes this.
     */
    void delete() {
        replaceValue("");

        // propagate the shift before removing, otherwise we're
        // missing the right sibling
        propagateItemShift(-1);
        parent.remove(innerIdx);
    }


    private void propagateRight(Consumer<ValIdx> idxConsumer) {
        for (int j = innerIdx + 1; j < parent.size(); j++) {
            idxConsumer.accept(parent.get(j));
        }
    }


    private void propagateItemShift(int shift) {
        propagateRight(idx -> idx.shiftRightInSeq(shift));
    }


    private void propagateOffsetShift(int shift) {
        if (shift == 0) {
            return;
        }

        // the relative change is propagated to all elements right of this one in the inner table
        propagateRight(idx -> idx.relativeOffset += shift);

        // and to all outer indices right of this one
        // the inner indices of the other sequences need not be touched
        for (int j = outerIdx + 1; j < myOuterOffsets.length; j++) {
            myOuterOffsets[j] += shift;
        }
    }


    private int currentAbsoluteOffset() {
        return myOuterOffsets[outerIdx] + relativeOffset;
    }


    private void shiftRightInSeq(int shift) {
        innerIdx += shift;
    }


    @Override
    public int compareTo(ValIdx o) {
        return Integer.compare(innerIdx, o.innerIdx);
    }


    @Override
    public String toString() {
        return "ValIdx{" +
            "outerIdx=" + outerIdx +
            ", innerIdx=" + innerIdx +
            ", relativeOffset=" + relativeOffset +
            '}';
    }
}
