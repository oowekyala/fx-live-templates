package com.github.oowekyala.rxstring

import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import org.reactfx.value.Var
import java.lang.StringBuilder

/**
 * @author Clément Fournier
 * @since 1.0
 */
class ReplaceHandlerTest : FunSpec({

    test("Test value changes") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
            val clazz = Var.newSimpleVar<Class<*>>(FunSpec::class.java)
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()

        val extSb = StringBuilder("foooo")

        lt.replaceHandler =
        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc


        lt.value shouldBe "Foo[MissingOverride]bar"

        dc.name.value = "hehe"

        lt.value shouldBe "Foo[hehe]bar"

        dc.name.value = null
        lt.value shouldBe "Foo[null]bar"
    }

    test("Test null value handling") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
            val clazz = Var.newSimpleVar<Class<*>>(FunSpec::class.java)
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()


        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc

        lt.value shouldBe "Foo[MissingOverride]bar"

        dc.name.value = null
        lt.value shouldBe "Foo[null]bar"
        dc.name.value = "FOO"
        lt.value shouldBe "Foo[FOO]bar"
    }

    test("Test initial null value") {
        class DContext {
            val name: Var<String?> = Var.newSimpleVar(null)
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()


        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc

        lt.value shouldBe "Foo[null]bar"
    }

})