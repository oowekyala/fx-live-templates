package com.github.oowekyala.rxstring;

import org.reactfx.value.Val;


/**
 * @author Clément Fournier
 * @since 1.0
 */
public interface LiveTemplate<D> extends Val<String> {


    Val<D> dataContextProperty();


    void setDataContext(D context);


    D getDataContext();


    Val<ReplaceHandler> replacementHandler();


    void setReplacementHandler(ReplaceHandler handler);


    ReplaceHandler getReplaceHandler();


    static <D> LiveTemplateBuilder<D> builder() {
        return new LiveTemplateBuilder<>();
    }
}
