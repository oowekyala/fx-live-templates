package com.github.oowekyala.rxstring

import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import javafx.beans.Observable
import javafx.beans.property.ListProperty
import javafx.beans.property.SimpleListProperty
import javafx.collections.FXCollections
import javafx.util.Callback
import org.reactfx.value.Var

/**
 * @author Clément Fournier
 * @since 1.0
 */
class ListPropertyTest : FunSpec() {
    init {

        test("Foo bar") {

            val lt =
                    LiveTemplate.newBuilder<DContext>()
                            .appendLine("<top>")
                            .bindTemplatedSeq({ it.subs }) {
                                it.append("{").bind { it.name }.appendLine("}")
                            }
                            .append("</top>")
                            .toTemplate()


            lt.value shouldBe null

            val dc = DContext()
            lt.dataContext = dc

            lt.value shouldBe """
            <top>
            {sub}
            </top>
        """.trimIndent()

            lt.dataContext.subs += SubContext("bar")

            lt.value shouldBe """
            <top>
            {sub}
            {bar}
            </top>
        """.trimIndent()

            val nameBinding = Var.newSimpleVar("hey")

            lt.dataContext.subs[1].name.bind(nameBinding)


            lt.value shouldBe """
            <top>
            {sub}
            {hey}
            </top>
        """.trimIndent()

            lt.dataContext.subs.set(FXCollections.observableArrayList(extractor()))
            lt.dataContext.subs += SubContext("bar")
            lt.dataContext.subs[0].name.bind(nameBinding)

            // FIXME the extractor causes the list to fire updated changes
            lt.value shouldBe """
            <top>
            {hey}
            {hey}
            </top>
        """.trimIndent()

        }
    }

    companion object {
        class SubContext(n: String = "sub") {
            val name = Var.newSimpleVar(n)
        }

        class DContext {
            val subs: ListProperty<SubContext> = SimpleListProperty(FXCollections.observableArrayList(SubContext()))
        }

        /** Extractor for observable lists.  */
        fun extractor(): Callback<SubContext, Array<Observable>> = Callback { ctx -> arrayOf(ctx.name) }
    }
}
