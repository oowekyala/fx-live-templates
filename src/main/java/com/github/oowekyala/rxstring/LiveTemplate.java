package com.github.oowekyala.rxstring;

import org.reactfx.value.Val;
import org.reactfx.value.Var;


/**
 * @author Clément Fournier
 * @since 1.0
 */
public interface LiveTemplate<D> extends Val<String> {


    Var<D> dataContextProperty();


    void setDataContext(D context);


    D getDataContext();


    Val<ReplaceHandler> replacementHandler();


    void setReplaceHandler(ReplaceHandler handler);


    ReplaceHandler getReplaceHandler();


    static <D> LiveTemplateBuilder<D> builder() {
        return new LiveTemplateBuilder<>();
    }

}
