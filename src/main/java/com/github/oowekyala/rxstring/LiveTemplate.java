package com.github.oowekyala.rxstring;

import java.util.logging.Logger;

import org.reactfx.Subscription;
import org.reactfx.value.Val;
import org.reactfx.value.Var;


/**
 * A {@link Val}&lt;String&gt; that binds to the properties of an object and reacts their changes.
 * You can build one with a {@link LiveTemplateBuilder}, see {@link LiveTemplate#newBuilder()}.
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

    /** Logger used by live templates to report eg failure of an external handler. */
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
     * data context, if any, and sets this value to empty. The {@linkplain #addReplaceHandler(ReplaceHandler)
     * replacement handlers} are also called when binding and unbinding.
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
    // TODO infer minimal replacement when switching data contexts
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
     * <p>That can be used for example to update an external presentation
     * layer, e.g. a {@link javafx.scene.control.TextArea} or similar,
     * without replacing the whole text each time.
     *
     * <p>Handlers are called:
     * <ul>
     * <li>When being added, and if the template is bound, to insert the current
     * value of the text. Parameters are (0, 0, {@link #getValue()}). If the template
     * is not bound the handler is not called</li>
     * <li>When switching data contexts:
     * <ul>
     * <li>If the new data context is null, parameters are (0, text.length, ""),
     * which corresponds to a deletion of the whole text</li>
     * <li>If the new data context is non-null, the handler is called with the
     * parameters (0, prevText.length(), newText.length()), or (0,0, newText.length())
     * if the previous data context was null.</li>
     * </ul>
     * </li>
     * <li>When a change in the bound properties causes a change in the value
     * of this template. Then the parameters are the smallest inferred bounds
     * for the change. The use of a {@linkplain #isUseDiffMatchPatchStrategyProperty()
     * patch algorithm} may scope down even more the range of the change.
     * </li>
     * </ul>
     *
     * <p>Exceptions in handlers are logged with {@link #LOGGER} but are
     * not rethrown, so that the value of the template stays consistent.
     *
     * @param handler the new handler to set
     *
     * @return A subscription that removes the handler when unsubscribing
     *
     * @throws NullPointerException if the given handler is null
     * @see #removeReplaceHandler(ReplaceHandler)
     */
    Subscription addReplaceHandler(ReplaceHandler handler);


    /**
     * Removes the given replace handler if it was present.
     *
     * @param handler handler to remove
     */
    void removeReplaceHandler(ReplaceHandler handler);


    /**
     * Whether to use a diff-match-patch algorithm to patch only
     * the smallest changes we can find. If false, when a variable
     * changes, its whole value will be replaced in the previous text.
     * This is enabled by default.
     *
     * @see #setUseDiffMatchPatchStrategy(boolean)
     * @see #isUseDiffMatchPatchStrategy()
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
     * Returns a builder for a new live template.
     *
     * @param <D> Type of the returned builder
     *
     * @return A new builder
     */
    static <D> LiveTemplateBuilder<D> newBuilder() {
        return new LiveTemplateBuilderImpl<>();
    }

}
