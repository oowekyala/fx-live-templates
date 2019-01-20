package com.github.oowekyala.rxstring;

import java.util.logging.Logger;

import org.reactfx.EventStream;
import org.reactfx.value.Val;
import org.reactfx.value.Var;


/**
 * A {@link Val}&lt;String&gt; that binds to the properties of an object and reacts their changes.
 * You can build one with a {@link LiveTemplateBuilder}, see {@link LiveTemplate#builder()}.
 *
 * TODO Streamline edition of optional attributes/elements
 * TODO make lazy
 *
 * @param <D> Type of data context this template can be bound to
 *
 * @author Clément Fournier
 * @since 1.0
 */
public interface LiveTemplate<D> extends Val<String> {

    /**
     * Logger used by live templates to report eg failure of an external handler.
     */
    Logger LOGGER = Logger.getLogger(LiveTemplate.class.getName());


    /**
     * The object to which this template is bound. If the template
     * is bound to nothing, then its string value ({@link #getValue()})
     * is null, i.e. {@link #isEmpty()} returns true.
     *
     * @return The data context property
     *
     * @see #setDataContext(Object)
     */
    Var<D> dataContextProperty();


    /**
     * Rebinds this template to the given data context. If null, then just unbinds the current
     * data context, if any, and sets this value to empty.
     *
     * <p>When setting the data context to a non-null value, the {@linkplain #getValue() value}
     * of this Val passes from its previous state to the full text bound to the new data context.
     * When setting the data context to a null value, the value goes directly to null. If the value
     * was already null, no changed is recorder. This is illustrated by the following schema:
     * <pre>
     * data context:  ---null  d1--------------...--d1  d2--------d2   null  null
     * value:         ---null  text(d1)--------...--|   text(d2)--|    null------
     * time:         >---|-----|---------------...--|---|---------|----|--------->
     * </pre>
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
     * After that they're only called incrementally. When unbinding the
     * data context, handlers are called once to delete the whole text
     * (parameters (0, text.length, "")).
     *
     * Exceptions in handlers are logged with {@link #LOGGER} but are
     * not rethrown.
     *
     * TODO fix that by exposing text changes
     * FIXME: handlers must be added BEFORE calling {@link #setDataContext(Object)},
     * otherwise the initial replacement is lost.
     *
     * @param handler the new handler to set
     *
     * @throws NullPointerException if the given handler is null
     */
    @Deprecated
    void addReplaceHandler(ReplaceHandler handler);


    /**
     * Removes the given replace handler if it was present.
     *
     * @param handler handler to remove
     */
    @Deprecated
    void removeReplaceHandler(ReplaceHandler handler);


    /**
     * Whether to use a diff-match-patch algorithm to patch only
     * the smallest changes we can find. If false, when a variable
     * changes, its whole value will be replaced in the previous text.
     */
    Var<Boolean> isUseDiffMatchPatchStrategyProperty();


    /**
     * Sets {@link #isUseDiffMatchPatchStrategyProperty()} to the given
     * value.
     *
     * @param bool Whether to use a patch strategy or not
     */
    default void setUseDiffMatchPatchStrategy(boolean bool) {
        isUseDiffMatchPatchStrategyProperty().setValue(bool);
    }


    /**
     * Returns the current value of {@link #isUseDiffMatchPatchStrategyProperty()}.
     */
    default boolean isUseDiffMatchPatchStrategy() {
        return isUseDiffMatchPatchStrategyProperty().getValue();
    }


    /**
     * Returns a stream of replacement events. An event is pushed every
     * time a change in the bound properties (or in data context) causes
     * a change in the string value of this template.
     *
     * <p>This can be used for example to update an external presentation
     * layer, e.g. a {@link javafx.scene.control.TextArea} or similar,
     * without replacing the whole text each time.
     *
     * <p>A single event is pushed when changing the data context. If the
     * data context is set to a non-null value, the event has the form
     * {@code RxTextChange(0, 0, text(dc))}, where {@code text(dc)} is the
     * whole evaluated value of the template. If the data context is set
     * to null, the the event has the form {@code RxTextChange(0, text(dc).length, "")},
     * which corresponds to a deletion of the entire previous value.
     *
     * <p>While a data context is bound, events are pushed with for the
     * smallest changed ranges that could be inferred.
     *
     * <p>Exceptions thrown by subscribers are logged with {@link #LOGGER}
     * but are not rethrown.
     *
     * @return A stream of replacement events
     */
    EventStream<RxTextChange> textChanges();


    /**
     * Returns a new builder for a live template.
     *
     * @param <D> Type of the returned builder
     *
     * @return A new builder
     */
    static <D> LiveTemplateBuilder<D> builder() {
        return new LiveTemplateBuilderImpl<>();
    }

}
