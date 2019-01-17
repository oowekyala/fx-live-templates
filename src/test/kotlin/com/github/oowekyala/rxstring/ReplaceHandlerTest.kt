package com.github.oowekyala.rxstring

import io.kotlintest.matchers.beOfType
import io.kotlintest.matchers.haveSize
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import org.reactfx.value.Var
import java.util.logging.Logger

/**
 * @author Clément Fournier
 * @since 1.0
 */
class ReplaceHandlerTest : FunSpec({

    test("Test initialisation causes full text insertion") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()

        val extSb = StringBuilder()

        val handler = ReplaceHandler { start, end, value -> extSb.replace(start, end, value) }
        lt.replaceHandler = handler
        lt.replaceHandler shouldBe handler
        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc
        lt.value shouldBe "Foo[MissingOverride]bar"
        extSb.toString() shouldBe "Foo[MissingOverride]bar"

    }

    test("Test replacement in external stringbuilder") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()

        val extSb = StringBuilder()

        val handler = ReplaceHandler { start, end, value -> extSb.replace(start, end, value) }
        lt.replaceHandler = handler
        lt.replaceHandler shouldBe handler
        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc
        lt.value shouldBe "Foo[MissingOverride]bar"
        extSb.toString() shouldBe "Foo[MissingOverride]bar"

        dc.name.value = "HELLO"

        lt.value shouldBe "Foo[HELLO]bar"
        extSb.toString() shouldBe "Foo[HELLO]bar"

    }



    test("Exceptions in the handler should not affect the consistency of the live template") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()

        val extSb = StringBuilder()

        val handler = ReplaceHandler { start, end, value -> extSb.replace(start, end, value) }
        lt.replaceHandler = handler
        lt.replaceHandler shouldBe handler
        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc
        lt.value shouldBe "Foo[MissingOverride]bar"
        extSb.toString() shouldBe "Foo[MissingOverride]bar"

        extSb.clear()

        val testLogHandler = TestLogHandler()
        Logger.getLogger(LiveTemplate::class.java.name).addHandler(testLogHandler)

        dc.name.value = "HELLO" // catches a StringIndexOutOfBoundsException

        testLogHandler.log should haveSize(1)
        testLogHandler.log[0].thrown should beOfType<StringIndexOutOfBoundsException>()

        // the value stayed consistent
        lt.value shouldBe "Foo[HELLO]bar"

    }

})