package com.github.oowekyala.rxstring;

import java.util.List;
import java.util.function.Consumer;


/**
 * @author Clément Fournier
 * @since 1.0
 */
class ValIdx implements Comparable<ValIdx> {

    final int outerIdx;
    final List<ValIdx> parent;
    int innerIdx;
    int relativeOffset;
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

        int leftLength = innerIdx == 0 ? 0 : left().length();
        relativeOffset = innerIdx == 0 ? 0 : leftLength + left().relativeOffset;

        // you can't call left().length() after setting this node in its parent

        if (innerIdx >= parent.size()) {
            assert innerIdx == parent.size(); // it's just an append

            parent.add(this);
            if (!isInitializing) {
                propagateOffsetShift(initialLength);
            }

        } else {
            parent.add(innerIdx, this);
            if (!isInitializing) {
                propagateItemShift(+1);
                propagateOffsetShift( initialLength);
            }
        }

    }


    int length() {
        if (innerIdx < parent.size() - 1) {
            // not the last
            return right().relativeOffset - relativeOffset;
        } else {
            return myOuterOffsets[outerIdx + 1] - currentAbsoluteOffset();
        }
    }


    List<ValIdx> siblings() {
        return parent;
    }


    ValIdx left() {
        return innerIdx == 0 ? null : parent.get(innerIdx - 1);
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


    void propagateItemShift(int shift) {
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
        siblings().remove(currentSeqIdx());
        propagateItemShift(-1);
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
