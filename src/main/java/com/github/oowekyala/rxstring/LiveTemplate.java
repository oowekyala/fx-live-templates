package com.github.oowekyala.rxstring;

import org.reactfx.EventStream;
import org.reactfx.value.Val;


/**
 * @author Clément Fournier
 * @since 1.0
 */
public interface LiveTemplate<D> extends Val<String> {


    Val<D> dataContextProperty();


    void setDataContext(D context);


    D getDataContext();


    EventStream<ReplaceEvent> replaceEvents();


    static <D> LiveTemplateBuilder<D> builder() {
        return new LiveTemplateBuilder<>();
    }
}
