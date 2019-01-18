package com.github.oowekyala.rxstring;

import java.util.logging.Logger;

import org.reactfx.value.Val;
import org.reactfx.value.Var;


/**
 * A {@link Val}&lt;String&gt; that binds to the properties of an object and reacts their changes.
 * You can build one with a {@link LiveTemplateBuilder}, see {@link LiveTemplate#builder()}.
 *
 * @param <D> Type of data context this template can be bound to
 *
 * @author Clément Fournier
 * @since 1.0
 */
public interface LiveTemplate<D> extends Val<String> {

    /**
     * Logger used by all live templates.
     */
    Logger LOGGER = Logger.getLogger(LiveTemplate.class.getName());


    /**
     * The object to which this template is bound. If the template
     * is bound to nothing, then its string value ({@link #getValue()})
     * is null, i.e. {@link #isEmpty()} returns true.
     *
     * @return The data context property
     */
    Var<D> dataContextProperty();


    /**
     * Rebinds this template to the given data context.
     *
     * @param context The new data context
     *
     * @see #dataContextProperty()
     */
    default void setDataContext(D context) {
        dataContextProperty().setValue(context);
    }


    /**
     * Returns the current data context, or null if there is none.
     *
     * @see #dataContextProperty()
     */
    default D getDataContext() {
        return dataContextProperty().getValue();
    }


    /**
     * Adds a callback that is called every time a change in the bound
     * properties (or in data context) causes a change in the string
     * value of this template.
     *
     * This can be used for example to update an external presentation
     * layer, e.g. a {@link javafx.scene.control.TextArea} or similar,
     * without replacing the whole text each time.
     *
     * Handlers are called once with the whole evaluated value of this
     * template when switching data contexts (parameters (0,0,text)).
     * Then they're only called incrementally.
     *
     * Exceptions in handlers are logged with {@link #LOGGER} but are
     * not rethrown.
     *
     * @param handler the new handler to set
     *
     * @throws NullPointerException if the given handler is null
     */
    void addReplaceHandler(ReplaceHandler handler);


    /**
     * Removes the given replace handler if it was present.
     *
     * @param handler handler to remove
     */
    void removeReplaceHandler(ReplaceHandler handler);


    /**
     * Returns a builder for a new live template.
     *
     * @param <D> Type of the returned builder
     *
     * @return A new builder
     */
    static <D> LiveTemplateBuilder<D> builder() {
        return new LiveTemplateBuilderImpl<>();
    }

}
