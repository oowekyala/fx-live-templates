package com.github.oowekyala.rxstring;

import java.util.Objects;


/**
 * Object describing a replacement that has occurred inside a live template
 * because of a change in a bound value.
 *
 * @author Clément Fournier
 */
final class ReplaceEvent {

    private final int startIndex;
    private final int endIndex;
    private final String value;


    public ReplaceEvent(int startIndex, int endIndex, String value) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.value = value;
    }


    public int getStartIndex() {
        return startIndex;
    }


    public int getEndIndex() {
        return endIndex;
    }


    public String getValue() {
        return value;
    }


    public int component1() {
        return getStartIndex();
    }


    public int component2() {
        return getEndIndex();
    }


    public String component3() {
        return getValue();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ReplaceEvent that = (ReplaceEvent) o;
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
        return "ReplaceEvent(" + startIndex + ", " + endIndex + ", '" + value + '\'' + ')';
    }
}
