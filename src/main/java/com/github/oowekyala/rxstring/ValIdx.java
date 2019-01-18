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

    /** Index in the sequence table. */
    private final int outerIdx;
    /** This sequence. */
    private final List<ValIdx> parent;
    /** Offset relative to the start of the sequence. */
    int relativeOffset;
    /** Index in the parent list. Shifted when elements are inserted to the left. */
    private int innerIdx;
    private int[] myOuterOffsets;


    ValIdx(int[] myOuterOffsets,
           int outerIdx,
           int innerIdx,
           List<ValIdx> parent,
           int initialLength,
           boolean isInitializing) {

        this.outerIdx = outerIdx;
        this.innerIdx = innerIdx;
        this.parent = parent;
        this.myOuterOffsets = myOuterOffsets;

        relativeOffset = innerIdx == 0 ? 0 : left().length() + left().relativeOffset;

        // you can't call left().length() after setting this node in its parent
        parent.add(innerIdx, this);
        if (!isInitializing) {
            propagateItemShift(+1);
            // The offset shift will be propagated via handleContentChange
            // when inserting the initial value, so not here
            // propagateOffsetShift(initialLength);
        }
    }


    int length() {
        if (innerIdx + 1 < parent.size()) {
            // there's a right node
            return right().relativeOffset - relativeOffset;
        } else {
            return myOuterOffsets[outerIdx + 1] - currentAbsoluteOffset();
        }
    }


    private ValIdx left() {
        return innerIdx == 0 ? null : parent.get(innerIdx - 1);
    }


    private ValIdx right() {
        return innerIdx + 1 < parent.size() ? parent.get(innerIdx + 1) : null;
    }


    private void propagateRight(Consumer<ValIdx> idxConsumer) {
        for (int j = currentSeqIdx() + 1; j < parent.size(); j++) {
            idxConsumer.accept(parent.get(j));
        }
    }


    private void propagateItemShift(int shift) {
        propagateRight(idx -> idx.shiftRightInSeq(shift));
    }


    void propagateOffsetShift(int shift) {
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


    void delete() {
        parent.remove(currentSeqIdx());
        propagateItemShift(-1);
    }


    private int currentSeqIdx() {
        return innerIdx;
    }


    int currentAbsoluteOffset() {
        return myOuterOffsets[outerIdx] + relativeOffset;
    }


    private void shiftRightInSeq(int shift) {
        innerIdx += shift;
    }


    @Override
    public int compareTo(ValIdx o) {
        return Integer.compare(currentSeqIdx(), o.currentSeqIdx());
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
