package com.github.oowekyala.rxstring;

import java.util.Objects;


/**
 * Object describing a replacement that has occurred inside a {@link LiveTemplate}
 * because of a change in a bound value. This is a simple data class.
 *
 * @author Clément Fournier
 */
final class RxTextChange {

    private final int startIndex;
    private final int endIndex;
    private final String value;


    public RxTextChange(int startIndex, int endIndex, String value) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.value = value;
    }


    /** Start index of the replacement. */
    public int getStartIndex() {
        return startIndex;
    }


    /** End index of the replacement. */
    public int getEndIndex() {
        return endIndex;
    }


    /** Text that overwrote the specified text range. */
    public String getReplacementText() {
        return value;
    }


    public int component1() {
        return getStartIndex();
    }


    public int component2() {
        return getEndIndex();
    }


    public String component3() {
        return getReplacementText();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RxTextChange that = (RxTextChange) o;
        return startIndex == that.startIndex &&
            endIndex == that.endIndex &&
            Objects.equals(value, that.value);
    }


    @Override
    public int hashCode() {
        return Objects.hash(startIndex, endIndex, value);
    }


    @Override
    public String toString() {
        return "RxTextChange(" + startIndex + ", " + endIndex + ", '" + value + '\'' + ')';
    }
}
